/**
 * Block which wraps processing of common notifications
 *
 * @param blockOptions Map with options (it's used for support optional params)
 * Possible elements:
 *     title - Name of status check which should be updated (required if GithubNotificator is initialized)
 *     options - Options list
 *     logUrl (optional) - Url which will be set in status check before execution of code
 *     artifactUrl (optional) - Url which will be set in status check if code was finished without exceptions
 *     printMessage (optional) - If it's true, print error message instead of its saving (default - false)
 *     configuration - Map with configuration of notifications
 *     Possible elements:
 *         begin (optional) - Map with configuration of notifications for status check before execution of code
 *         Possible elements:
 *             message (optional) - New message for status check 
 *         end (optional) - Map with configuration of notifications for status check after execution of code without exceptions
 *         Possible elements:
 *             message (optional) - New message for status check 
 *         exceptions (optional) - List of Maps with configuration of notifications for each exception
 *         Structure of Maps:
 *             class - Class of target exception
 *             problemMessage - Displayed message
 *             rethrow - Describe should exception be rethrown and how (default - No)
 *             scope - scope of problem message (default - Specific)
 *             getMessage - list of messages one of which must be in message of exception
 *             githubNotification - Map with values which describe params for updating status in Github repo
 *             Possible elements:
 *                 status - Status which will be set
 *                 message (optional) - New message for status check 
 * @param code Block of code which is executed
 */
def call(Map blockOptions, Closure code) {
    def title = blockOptions["title"]
    def options = blockOptions["options"]
    def logUrl = blockOptions["logUrl"]
    def artifactUrl = blockOptions["artifactUrl"]
    def printMessage = blockOptions["printMessage"]
    def configuration = blockOptions["configuration"]

    try {
        if (configuration.begin?.message) {
            GithubNotificator.updateStatus(options["stage"], title, "in_progress",
                options, configuration["begin"]["message"], logUrl)
        }

        code()

        if (configuration.end?.message) {
            GithubNotificator.updateStatus(options["stage"], title, "success",
                options, configuration["end"]["message"], artifactUrl)
        }
    } catch (e) {
        // find necessary exception in configuration
        for (exception in configuration["exceptions"]) {
            if ((!(exception["class"] instanceof String) && exception["class"].isInstance(e)) 
                || (exception["class"] == "TimeoutExceeded" && utils.isTimeoutExceeded(e))) {
                // check existence of some messages in exception if it's required
                Boolean exceptionFound = true
                if (exception["getMessage"]) {
                    exceptionFound = false
                    if (e.getMessage()) {
                        for (message in exception["getMessage"]) {
                            if (e.getMessage().contains(message)) {
                                exceptionFound = true
                                break
                            }
                        }
                    }
                }
                if (exceptionFound) {
                    if (!printMessage && options.problemMessageManager && (options["stage"] != "Test")) {
                        saveProblemMessage(options, exception, exception["problemMessage"], options["stage"], options["osName"])
                    } else {
                        println(exception["problemMessage"])
                    }
                    switch(exception["rethrow"]) {
                        case ExceptionThrowType.RETHROW:
                            throw e
                            break
                        case ExceptionThrowType.THROW_IN_WRAPPER:
                            throw new ExpectedExceptionWrapper(exception["problemMessage"], e)
                            break
                        default:
                            println(e.toString())
                            println(e.getMessage())
                    }
                    if (exception["githubNotification"]) {
                        GithubNotificator.updateStatus(options["stage"], title, exception["githubNotification"]["status"], 
                            options, exception["githubNotification"]["message"] ?: exception["problemMessage"])
                    }
                    return
                }
            }
        }
        // exception wasn't found in configuration. Rethrow it
        println("[${this.class.getName()}][WARNING] Exception wasn't found in configuration")
        throw e
    }
}


def saveProblemMessage(Map options, Map exception, String message, String stage = "", String osName = "") {
    def messageFunciton
    // find function for specified scope
    switch(exception["scope"]) {
        case ProblemMessageManager.GENERAL:
            options.problemMessageManager.saveGeneralFailReason(message, stage, osName)
            break
        case ProblemMessageManager.GLOBAL:
            options.problemMessageManager.saveGlobalFailReason(message)
            break
        default:
            // SPECIFIC scope is default scope
            options.problemMessageManager.saveSpecificFailReason(message, stage, osName)
    }
}