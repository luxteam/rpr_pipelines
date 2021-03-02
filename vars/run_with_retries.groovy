def shoudBreakRetries(labels) {
    // retries should be broken if it isn't first try (some other nodes are excluded) and there isn't any suitable online node
    return labels.contains('!') && nodesByLabel(label: labels, offline: false).size() == 0
}


def call(String labels, def stageTimeout, def retringFunction, Boolean reuseLastNode, def stageName, def options, List allowedExceptions = [], Integer maxNumberOfRetries = -1, String osName = "", Boolean setBuildStatus = false) {
    List nodesList = nodesByLabel label: labels, offline: true
    println "[INFO] Found ${nodesList.size()} suitable nodes"
    // if 0 suitable nodes are found - wait some node in loop
    while (nodesList.size() == 0) {
        println "[INFO] Couldn't find suitable nodes. Search will be retried after pause"
        if (!options.nodeNotFoundMessageSent) {
            node ("master") {
                withCredentials([string(credentialsId: 'zabbix-notifier-webhook', variable: 'WEBHOOK_URL')]) {
                    utils.sendExceptionToSlack(this, env.JOB_NAME, env.BUILD_NUMBER, env.BUILD_URL, WEBHOOK_URL, "zabbix_critical", "Failed to find any node with labels '${labels}'")
                    options.nodeNotFoundMessageSent = true
                }
            }
        }
        sleep(time: 5, unit: 'MINUTES')
        nodesList = nodesByLabel label: labels, offline: true
    }
    options.nodeNotFoundMessageSent = false
    println "Found the following PCs: ${nodesList}"
    def nodesCount = nodesList.size()
    def tries = nodesCount
    def closedChannelRetries = 0

    if (reuseLastNode) {
        tries++
    } else {
        // if there is only one suitable machine and reuseLastNode is false - forcibly add one more try
        if (tries == 1) {
            tries++
            reuseLastNode = true
        }
    }

    if (maxNumberOfRetries > 0 && tries > maxNumberOfRetries) {
        tries = maxNumberOfRetries
    }

    Boolean successCurrentNode = false

    String title = ""
    if (stageName == "Build") {
        title = osName
    } else if (stageName == "Test") {
        title = options['stageName']
    } else {
        title = "Building test report"
    }

    for (int i = 0; i < tries; i++) {
        String nodeName = ""
        options['currentTry'] = i
        options['nodeReallocateTries'] = tries

        try {
            // check that there is at least one suitable online node and break retries if not (except waiting of first node)
            if (shoudBreakRetries(labels)) {
                // if some nodes were rebooted - they should be up in 10 minutes
                sleep(time: 10, unit: 'MINUTES')
                if (shoudBreakRetries(labels)) {
                    // no one node was found. Try to do last retry with previous set of labels
                    def labelsParts = labels.split("&&") as List
                    labelsParts.removeAt(labelsParts.size() - 1)
                    labels = labelsParts.join("&&")
                    tries = i + 2
                    continue
                }
            }
            node(labels) {
                timeout(time: "${stageTimeout}", unit: 'MINUTES') {
                    ws("WS/${options.PRJ_NAME}_${stageName}") {
                        nodeName = env.NODE_NAME
                        retringFunction(nodesList, i)
                        successCurrentNode = true
                        def currentStatus = GithubNotificator.getCurrentStatus(stageName, title, options)
                        if (stageName != 'Test' && options.problemMessageManager) {
                            options.problemMessageManager.clearErrorReasons(stageName, osName) 
                        }
                    }
                }
            }
        } catch(Exception e) {
            if (e instanceof ExpectedExceptionWrapper) {
                if (e.abortCurrentOS) {
                    println("[ERROR] Detected abortCurrentOS flag in catched exception. Abort next tests on ${osName} OS")
                    i = tries + 1
                }
                e = e.getCause()
                if (e instanceof ExpectedExceptionWrapper) {
                    if (e.abortCurrentOS) {
                        println("[ERROR] Detected abortCurrentOS flag in catched exception. Abort next tests on ${osName} OS")
                        i = tries + 1
                    }
                    e = e.getCause()
                }
            }

            String exceptionClassName = e.getClass().toString()

            if (exceptionClassName.contains("FlowInterruptedException")) {
                e.getCauses().each(){
                    // UserInterruption aborting by user
                    // ExceededTimeout aborting by timeout
                    // CancelledCause for aborting by new commit
                    String causeClassName = it.getClass().toString()
                    println "Interruption cause: ${causeClassName}"
                    if (causeClassName.contains("CancelledCause")) {
                        if (options.problemMessageManager) {
                            options.problemMessageManager.saveSpecificFailReason(NotificationConfiguration.BUILD_ABORTED_BY_COMMIT, stageName, osName) 
                        }
                        println "[INFO] GOT NEW COMMIT"
                        GithubNotificator.closeUnfinishedSteps(options, NotificationConfiguration.BUILD_ABORTED_BY_COMMIT)
                        throw e
                    } else if (causeClassName.contains("UserInterruption") || causeClassName.contains("ExceptionCause")) {
                        if (options.problemMessageManager) {
                            options.problemMessageManager.saveSpecificFailReason(NotificationConfiguration.BUILD_ABORTED_BY_USER, stageName, osName) 
                        }
                        println "[INFO] Build was aborted by user"
                        GithubNotificator.closeUnfinishedSteps(options, NotificationConfiguration.BUILD_ABORTED_BY_USER)
                        throw e
                    }
                }
            } else if (exceptionClassName.contains("RemotingSystemException")) {
                
                try {
                    // take master node for send exception in Slack channel
                    node ("master") {
                        withCredentials([string(credentialsId: 'zabbix-notifier-webhook', variable: 'WEBHOOK_URL')]) {
                            utils.sendExceptionToSlack(this, env.JOB_NAME, env.BUILD_NUMBER, env.BUILD_URL, WEBHOOK_URL, "zabbix_critical", "${nodeName}: RemotingSystemException appeared. Node is going to be marked as offline")
                            utils.markNodeOffline(this, nodeName, "RemotingSystemException appeared. This node was marked as offline")
                            utils.sendExceptionToSlack(this, env.JOB_NAME, env.BUILD_NUMBER, env.BUILD_URL, WEBHOOK_URL, "zabbix_critical", "${nodeName}: Node was marked as offline")
                        }
                    }
                } catch (e2) {
                    node ("master") {
                        withCredentials([string(credentialsId: 'zabbix-notifier-webhook', variable: 'WEBHOOK_URL')]) {
                            utils.sendExceptionToSlack(this, env.JOB_NAME, env.BUILD_NUMBER, env.BUILD_URL, WEBHOOK_URL, "zabbix_critical", "Failed to mark node '${nodeName}' as offline")
                        }
                    }
                }
            } else if (exceptionClassName.contains("ClosedChannelException")) {
                GithubNotificator.updateStatus(stageName, title, "failure", options, NotificationConfiguration.LOST_CONNECTION_WITH_MACHINE)
            }

            println("[ERROR] Failed on ${env.NODE_NAME} node")
            println("Exception: ${e.toString()}")
            println("Exception message: ${e.getMessage()}")
            println("Exception cause: ${e.getCause()}")
            println("Exception stack trace: ${e.getStackTrace()}")

            if (utils.isTimeoutExceeded(e)) {
                GithubNotificator.updateStatus(stageName, title, "timed_out", options, NotificationConfiguration.STAGE_TIMEOUT_EXCEEDED)
            } else {
                // save unknown reason if any other reason wasn't set
                def currentStatus = GithubNotificator.getCurrentStatus(stageName, title, options)
                if (currentStatus == "failure" || currentStatus == "timed_out") {
                    GithubNotificator.updateStatus(stageName, title, "failure", options, NotificationConfiguration.REASON_IS_NOT_IDENTIFIED)
                }
            }

            if (allowedExceptions.size() != 0) {
                Boolean isExceptionAllowed = false

                for (allowedException in allowedExceptions) {
                    if (exceptionClassName.contains(allowedException)) {
                        isExceptionAllowed = true
                        break
                    }
                }

                if (!isExceptionAllowed) {
                    println("[INFO] Exception isn't allowed")
                    if (options.problemMessageManager) {
                        if (stageName == 'Test') {
                            options.problemMessageManager.saveGeneralFailReason(NotificationConfiguration.SOME_TESTS_FAILED, stageName, osName)
                        } else if (utils.isTimeoutExceeded(e)) {
                            options.problemMessageManager.saveGeneralFailReason(NotificationConfiguration.TIMEOUT_EXCEEDED, stageName, osName)
                        } else {
                            options.problemMessageManager.saveGeneralFailReason(NotificationConfiguration.UNKNOWN_REASON, stageName, osName)
                        }
                    }
                    GithubNotificator.updateStatus(stageName, title, "action_required", options)
                    if (stageName == 'Build') {
                        GithubNotificator.failPluginBuilding(options, osName)
                    }
                    if (setBuildStatus) {
                        currentBuild.result = "FAILURE"
                    }
                    throw e
                } else {
                    println("[INFO] Exception found in allowed exceptions")
                }
            }

            if (i == tries - 1) {
                if (options.problemMessageManager) {
                    if (stageName == 'Test') {
                        options.problemMessageManager.saveGeneralFailReason(NotificationConfiguration.SOME_TESTS_FAILED, stageName, osName)
                    } else if (utils.isTimeoutExceeded(e)) {
                        options.problemMessageManager.saveGeneralFailReason(NotificationConfiguration.TIMEOUT_EXCEEDED, stageName, osName)
                    } else {
                        options.problemMessageManager.saveGeneralFailReason(NotificationConfiguration.UNKNOWN_REASON, stageName, osName)
                    }
                }
                GithubNotificator.updateStatus(stageName, title, "action_required", options)
                if (stageName == 'Build') {
                    GithubNotificator.failPluginBuilding(options, osName)
                }
                println "[ERROR] All nodes on ${stageName} stage with labels ${labels} failed."
                if (setBuildStatus) {
                    currentBuild.result = "FAILURE"
                }
                throw e
            }
        }

        if (successCurrentNode) {
            i = tries + 1
        // exclude label of failed machine only if it isn't necessary to reuse last node and if it isn't last try
        } else if (!(reuseLastNode && i == nodesCount + closedChannelRetries - 1) && nodeName) {
            println "[EXCLUDE] ${nodeName} from nodes pool (Labels: ${labels})"
            labels += " && !${nodeName}"
        } else if (!nodeName) {
            // if ClosedChannelException appeared on 'node' block - add additional try
            tries++
            closedChannelRetries++
            options['nodeReallocateTries']++
            println("[INFO] Additional retry failed")
        }
    }
}