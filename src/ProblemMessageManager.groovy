/**
 * Class which manages fail and unstable messages
 */
public class ProblemMessageManager {
    static enum ProblemScope {
        SPECIFIC, GENERAL, GLOBAL
    }

    def static SPECIFIC = ProblemScope.SPECIFIC
    def static GENERAL = ProblemScope.GENERAL
    def static GLOBAL = ProblemScope.GLOBAL

    def context
    def currentBuild

    // save error messages and stages on which problems appear with os names if it's necessary
    // it's used for decide that general error message should be added or not
    Map failReasons = [:]
    List unstableReasons = []

    /**
     * Main constructor
     *
     * @param context
     * @param currentBuild Object of current build
     */
    ProblemMessageManager(context, currentBuild) {
        this.context = context
        this.currentBuild = currentBuild
    }

    /**
     * Function for save some Specific fail reason
     *
     * @param reason message which describe fail reason
     * @param stageName name of stage on which fail appeared 
     * @param osName OS in which branch fail appeared. It shouldn't be set if fail is common for whole build
     */
    def saveSpecificFailReason(String reason, String stageName, String osName = "") {
        saveFailReason(reason, ProblemScope.SPECIFIC, stageName, osName)
    }

    /**
     * Function for save some General fail reason
     * General fail reason will be saved if there aren't other fail reasons for this stage and OS
     *
     * @param reason message which describe fail reason
     * @param stageName name of stage on which fail appeared 
     * @param osName OS in which branch fail appeared. It shouldn't be set if fail is common for whole build
     */
    def saveGeneralFailReason(String reason, String stageName, String osName = "") {
        saveFailReason(reason, ProblemScope.GENERAL, stageName, osName)
    }

    /**
     * Function for save some Global fail reason
     * Global fail reason isn't connected with any stage and must be saved anyway
     *
     * @param reason message which describe fail reason
     */
    def saveGlobalFailReason(String reason) {
        saveFailReason(reason, ProblemScope.GLOBAL)
    }

    private def saveFailReason(String reason, ProblemScope problemScope, String stageName = "", String osName = "") {
        try {
            // ignore general error messages if specific message has already saved
            if (problemScope == ProblemScope.GENERAL) {
                if (!osName) {
                    if (failReasons.containsKey(stageName)) {
                        return
                    } else {
                        failReasons[stageName] = []
                        failReasons[stageName].add(reason)
                    }
                } else {
                    if (failReasons.containsKey(stageName) && failReasons[stageName].containsKey(osName)) {
                        return
                    } else {
                        if (!failReasons.containsKey(stageName)) {
                            failReasons[stageName] = [:]
                        }
                        failReasons[stageName][osName] = []
                        failReasons[stageName][osName].add(reason)
                    }
                }
            }

            // save information about failed stages if it is specific or general message
            if (problemScope == ProblemScope.SPECIFIC) {
                if (osName) {
                    if (!failReasons.containsKey(stageName)) {
                        failReasons[stageName] = [:]
                    }
                    if (!failReasons[stageName].containsKey(osName)) {
                        failReasons[stageName][osName] = []
                    }
                    if (!failReasons[stageName][osName].contains(reason)) {
                        failReasons[stageName][osName].add(reason)
                    }
                } else {
                    if (!failReasons.containsKey(stageName)) {
                        failReasons[stageName] = []
                    }
                    if (!failReasons[stageName].contains(reason)) {
                        failReasons[stageName].add(reason)
                    }
                }
            } else if (problemScope == ProblemScope.GLOBAL) {
                if (!failReasons.containsKey('Global')) {
                    failReasons['Global'] = []
                }
                if (!failReasons['Global'].contains(reason)) {
                    failReasons['Global'].add(reason)
                }
            }

            context.println("[INFO] Message '${reason}' appended")
        } catch (e) {
            context.println("[ERROR] Could not save fail reason")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    def saveUnstableReason(String reason) {
        try {
            String message = "${reason} </br>"
            if (!unstableReasons.contains(message)) {
                unstableReasons.add(message)
            }
            context.println("[INFO] Message '${reason}' appended")
        } catch (e) {
            context.println("[ERROR] Could not save unstable reason")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    /**
     * Function for clear all fail messages for specified stage and os (optional)
     *
     * @param stageName name of stage on which error reasons must be deleted
     * @param osName os on which error reasons must be deleted
     */
    def clearErrorReasons(String stageName, String osName = "") {
        try {
            if (failReasons.containsKey(stageName)) {
                if (osName) {
                    if (failReasons[stageName].containsKey(osName)) {
                        failReasons[stageName].remove(osName)
                    }
                    if (failReasons[stageName].size() == 0) {
                        failReasons.remove(stageName)
                    }
                } else {
                    failReasons.remove(stageName)
                }
            }
        } catch (e) {
            context.println("[ERROR] Error during cleaning of error messages")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    /**
     * Return information about fail or unstable status as plain text
     */
    def getMessages() {
        String messages = ""
        if (failReasons.size() != 0) {
            List failReasonsMessage = []
            for (stage in failReasons) {
                if (stage.value instanceof Map) {
                    for (os in stage.value) {
                        for (reason in os.value) {
                            if (stage.key == 'Global') {
                                failReasonsMessage.add("${reason}.")
                            } else {
                                failReasonsMessage.add("${reason} (${stage.key} stage: ${os.key}).")
                            }   
                        }
                    }
                } else {
                    for (reason in stage.value) {
                        if (stage.key == 'Global') {
                            failReasonsMessage.add("${reason}.")
                        } else {
                            failReasonsMessage.add("${reason} (${stage.key} stage).")
                        }
                    }
                }
            }
            messages = "Build Failure Reason: \n${failReasonsMessage.join('\n')}"
        } else if (unstableReasons.size() != 0) {
            messages = "Build Unstable Reason: \n${unstableReasons.join('\n')}"
        } else if (currentBuild.result == "FAILURE") {
            messages = "Build Failure Reason: \nUnknown"
        } else if (currentBuild.result == "UNSTABLE") {
            messages = "Build Unstable Reason: \nUnknown"
        }

        return messages
    }

    /**
     * Function for add in description of build information about fail or unstable status if there are any problems
     */
    def publishMessages() {
        String statusMessage = "</br>"
        if (failReasons.size() != 0) {
            List failReasonsMessage = []
            for (stage in failReasons) {
                if (stage.value instanceof Map) {
                    for (os in stage.value) {
                        for (reason in os.value) {
                            if (stage.key == 'Global') {
                                failReasonsMessage.add("${reason} </br>")
                            } else {
                                failReasonsMessage.add("${reason} (${stage.key} stage: ${os.key}) </br>")
                            }   
                        }
                    }
                } else {
                    for (reason in stage.value) {
                        if (stage.key == 'Global') {
                            failReasonsMessage.add("${reason} </br>")
                        } else {
                            failReasonsMessage.add("${reason} (${stage.key} stage) </br>")
                        }
                    }
                }
            }
            statusMessage = "<b style='color: #641e16'>Build Failure Reason:</b> <span style='color: #b03a2e'>${failReasonsMessage.join('')}</span><br/>"
        } else if (unstableReasons.size() != 0) {
            statusMessage = "<b style='color: #7d6608'>Build Unstable Reason:</b> <span style='color: #b7950b'>${unstableReasons.join('')}</span><br/>"
        } else if (currentBuild.result == "FAILURE") {
            statusMessage = "<b style='color: #641e16'>Build Failure Reason:</b> <span style='color: #b03a2e'>Unknown</span><br/>"
        } else if (currentBuild.result == "UNSTABLE") {
            statusMessage = "<b style='color: #7d6608'>Build Unstable Reason:</b> <span style='color: #b7950b'>Unknown</span><br/>"
        }

        if (statusMessage) {
            if (currentBuild.description) {
                currentBuild.description += statusMessage
            } else {
                currentBuild.description = statusMessage
            }
        }

        return statusMessage
    }
}
