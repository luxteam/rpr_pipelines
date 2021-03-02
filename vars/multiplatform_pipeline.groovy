import java.text.SimpleDateFormat
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import hudson.plugins.git.GitException
import java.nio.channels.ClosedChannelException
import hudson.remoting.RequestAbortedException
import java.lang.IllegalArgumentException
import java.time.*
import java.time.format.DateTimeFormatter
import jenkins.model.Jenkins
import groovy.transform.Synchronized
import java.util.Iterator
import TestsExecutionType


@NonCPS
@Synchronized
def getNextTest(Iterator iterator) {
    if (iterator.hasNext()) {
        return iterator.next()
    } else {
        return null
    }
}


def executeTestsNode(String osName, String gpuNames, def executeTests, Map options)
{
    if (gpuNames && options['executeTests']) {
        def testTasks = [:]
        gpuNames.split(',').each() {
            String asicName = it
            
            testTasks["Test-${asicName}-${osName}"] = {
                stage("Test-${asicName}-${osName}") {
                    // if not split - testsList doesn't exists
                    // TODO: replace testsList check to splitExecution var
                    options.testsList = options.testsList ?: ['']

                    def testerTag = "Tester"
                    if (options.TESTER_TAG) {
                        if (options.TESTER_TAG.indexOf(' ') > -1){
                            testerTag = options.TESTER_TAG
                        } else {
                            testerTag = "${options.TESTER_TAG} && Tester"
                        }
                    } 
                    def testerLabels = "${osName} && ${testerTag} && OpenCL && gpu${asicName}"

                    Iterator testsIterator = options.testsList.iterator()
                    Integer launchingGroupsNumber = 1
                    if (!options["parallelExecutionType"] || options["parallelExecutionType"] == TestsExecutionType.TAKE_ONE_NODE_PER_GPU) {
                        launchingGroupsNumber = 1
                    } else if (options["parallelExecutionType"] == TestsExecutionType.TAKE_ALL_NODES) {
                        List possibleNodes = nodesByLabel label: testerLabels, offline: true
                        launchingGroupsNumber = possibleNodes.size()
                    }

                    Map testsExecutors = [:]

                    for (int i = 0; i < launchingGroupsNumber; i++) {
                        testsExecutors["Test-${asicName}-${osName}-${i}"] = {
                            String testName = getNextTest(testsIterator)
                            while (testName != null) {
                                String engine = null
                                if (options.engines) {
                                    engine = testName.split("-")[-1]
                                }
                                if (options.skippedTests && options.skippedTests.containsKey(testName) && options.skippedTests[testName].contains("${asicName}-${osName}")) {
                                    println("Test group ${testName} on ${asicName}-${osName} fully skipped")
                                    testName = getNextTest(testsIterator)
                                    continue
                                } 
                                // if there number of errored groups in succession is more than 
                                if (options["errorsInSuccession"] && 
                                        ((engine && options["errorsInSuccession"]["${osName}-${asicName}-${engine}"] && options["errorsInSuccession"]["${osName}-${asicName}-${engine}"].intValue() >= 3)
                                        || (options["errorsInSuccession"]["${osName}-${asicName}"] && options["errorsInSuccession"]["${osName}-${asicName}"].intValue() >= 3))) {
                                    println("Test group ${testName} on ${asicName}-${osName} aborted due to exceeded number of errored groups in succession")
                                    testName = getNextTest(testsIterator)
                                    continue
                                }
                                if (options["abort${osName}"]) {
                                    println("Test group ${testName} on ${asicName}-${osName} aborted due to current context")
                                    testName = getNextTest(testsIterator)
                                    continue
                                }

                                println("Scheduling ${osName}:${asicName} ${testName}")

                                Map newOptions = options.clone()
                                newOptions["stage"] = "Test"
                                newOptions["osName"] = osName
                                newOptions['testResultsName'] = testName ? "testResult-${asicName}-${osName}-${testName}" : "testResult-${asicName}-${osName}"
                                newOptions['stageName'] = testName ? "${asicName}-${osName}-${testName}" : "${asicName}-${osName}"
                                newOptions['tests'] = testName ?: options.tests

                                def retringFunction = { nodesList, currentTry ->
                                    try {
                                        executeTests(osName, asicName, newOptions)
                                    } catch(Exception e) {
                                        // save expected exception message for add it in report
                                        String expectedExceptionMessage = ""
                                        if (e instanceof ExpectedExceptionWrapper) {
                                            if (e.abortCurrentOS) {
                                                options["abort${osName}"] = true
                                            }
                                            expectedExceptionMessage = e.getMessage()
                                            // check that cause isn't more specific expected exception
                                            if (e.getCause() instanceof ExpectedExceptionWrapper) {
                                                if (e.getCause().abortCurrentOS) {
                                                    options["abort${osName}"] = true
                                                }
                                                expectedExceptionMessage = e.getCause().getMessage()
                                            }
                                        }

                                        println "[ERROR] Failed during tests on ${env.NODE_NAME} node"
                                        println "Exception: ${e.toString()}"
                                        println "Exception message: ${e.getMessage()}"
                                        println "Exception cause: ${e.getCause()}"
                                        println "Exception stack trace: ${e.getStackTrace()}"

                                        String exceptionClassName = e.getClass().toString()
                                        if (exceptionClassName.contains("FlowInterruptedException")) {
                                            e.getCauses().each(){
                                                // UserInterruption aborting by user
                                                // ExceededTimeout aborting by timeout
                                                // CancelledCause for aborting by new commit
                                                String causeClassName = it.getClass().toString()
                                                println "Interruption cause: ${causeClassName}"
                                                if (causeClassName.contains("CancelledCause")) {
                                                    expectedExceptionMessage = "Build was aborted by new commit."
                                                } else if (causeClassName.contains("UserInterruption") || causeClassName.contains("ExceptionCause")) {
                                                    expectedExceptionMessage = "Build was aborted by user."
                                                } else if (utils.isTimeoutExceeded(e) && !expectedExceptionMessage.contains("timeout")) {
                                                    expectedExceptionMessage = "Timeout exceeded (pipelines layer)."
                                                }
                                            }
                                        } else if (exceptionClassName.contains("ClosedChannelException") || exceptionClassName.contains("RemotingSystemException") || exceptionClassName.contains("InterruptedException")) {
                                            expectedExceptionMessage = "Lost connection with machine."
                                        }

                                        // add info about retry to options
                                        boolean added = false;
                                        String testsOrTestPackage
                                        if (newOptions['splitTestsExecution']) {
                                            testsOrTestPackage = newOptions['tests']
                                        } else {
                                            //all non splitTestsExecution and non regression builds (e.g. any build of core)
                                            testsOrTestPackage = 'DefaultExecution'
                                        }

                                        if (!expectedExceptionMessage) {
                                            expectedExceptionMessage = "Unexpected exception."
                                        }

                                        if (options.containsKey('nodeRetry')) {
                                            // parse united suites
                                            testsOrTestPackageParts = testsOrTestPackage.split("-")
                                            for (failedSuite in testsOrTestPackageParts[0].split()) {
                                                String suiteName
                                                // check engine availability
                                                if (testsOrTestPackageParts.length > 1) {
                                                    suiteName = "${failedSuite}-${testsOrTestPackageParts[1]}"
                                                } else {
                                                    suiteName = "${failedSuite}"
                                                }
                                                Map tryInfo = [host:env.NODE_NAME, link:"${testsOrTestPackage}.${env.NODE_NAME}.retry_${currentTry}.crash.log", exception: expectedExceptionMessage, time: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))]

                                                retryLoops: for (testers in options['nodeRetry']) {
                                                    if (testers['Testers'].equals(nodesList)){
                                                        for (suite in testers['Tries']) {
                                                            if (suite[suiteName]) {
                                                                suite[suiteName].add(tryInfo)
                                                                added = true
                                                                break retryLoops
                                                            }
                                                        }
                                                        // add list for test group if it doesn't exists
                                                        testers['Tries'].add([(suiteName): [tryInfo]])
                                                        added = true
                                                        break retryLoops
                                                    }
                                                }

                                                if (!added){
                                                    options['nodeRetry'].add([Testers: nodesList, gpuName: asicName, osName: osName, Tries: [[(suiteName): [tryInfo]]]])
                                                }
                                            }

                                            println options['nodeRetry'].inspect()
                                        }

                                        throw e
                                    }
                                }

                                try {
                                    Integer retries_count = options.retriesForTestStage ?: -1
                                    run_with_retries(testerLabels, options.TEST_TIMEOUT, retringFunction, true, "Test", newOptions, [], retries_count, osName)
                                } catch(FlowInterruptedException e) {
                                    options.buildWasAborted = true
                                    e.getCauses().each(){
                                        String causeClassName = it.getClass().toString()
                                        if (causeClassName.contains("CancelledCause") || causeClassName.contains("UserInterruption")) {
                                            throw e
                                        }
                                    }
                                } catch (e) {
                                    // Ignore other exceptions
                                }
                                testName = getNextTest(testsIterator)
                            }
                        }                        
                    }

                    parallel testsExecutors
                }
            }
        }
        parallel testTasks
    } else {
        println "[WARNING] No tests found for ${osName}"
    }
}

def executePlatform(String osName, String gpuNames, def executeBuild, def executeTests, Map options)
{
    def retNode = {
        try {

            if (options['executeBuild'] && executeBuild) {
                stage("Build-${osName}") {
                    def builderLabels = "${osName} && ${options.BUILDER_TAG}"
                    def retringFunction = { nodesList, currentTry ->
                        executeBuild(osName, options)
                    }
                    run_with_retries(builderLabels, options.BUILD_TIMEOUT, retringFunction, false, "Build", options, ['FlowInterruptedException'], -1, osName, true)
                }
            }

            if (options.containsKey('tests') && options.containsKey('testsPackage')){
                if (options['testsPackage'] != 'none' || options['tests'].size() == 0 || !(options['tests'].size() == 1 && options['tests'].get(0).length() == 0)){ // BUG: can throw exception if options['tests'] is string with length 1
                    executeTestsNode(osName, gpuNames, executeTests, options)
                }
            } else {
                executeTestsNode(osName, gpuNames, executeTests, options)
            }

        } catch (e) {
            println "[ERROR] executePlatform throw the exception"
            println "Exception: ${e.toString()}"
            println "Exception message: ${e.getMessage()}"
            println "Exception cause: ${e.getCause()}"
            println "Exception stack trace: ${e.getStackTrace()}"

            String exceptionClassName = e.getClass().toString()
            if (exceptionClassName.contains("FlowInterruptedException")) {
                options.buildWasAborted = true
            }

            currentBuild.result = "FAILURE"
            options.FAILED_STAGES.add(e.toString())
            throw e
        }
    }
    return retNode
}

def call(String platforms, def executePreBuild, def executeBuild, def executeTests, def executeDeploy, Map options) {
    try {

        try {
            setupBuildStoragePolicy()
        } catch (e) {
            println("[ERROR] Failed to setup build storage policty.")
            println(e.toString())
        }

        try {
            options.baseBuildName = currentBuild.displayName
            if (env.BuildPriority) {
                currentBuild.displayName = "${currentBuild.displayName} (Priority: ${env.BuildPriority})"
                println("[INFO] Priority was set by BuildPriority parameter")
            } else {
                def jenkins = Jenkins.getInstance();        
                def views = Jenkins.getInstance().getViews()
                String jobName = env.JOB_NAME.split('/')[0]

                def jobsViews = []
                for (view in views) {
                    if (view.contains(jenkins.getItem(jobName))) {
                        jobsViews.add(view.getDisplayName())
                    }
                }

                if (jobsViews.contains('Autojobs')) {
                    currentBuild.displayName = "${currentBuild.displayName} (Priority: 20)"
                } else if (jobsViews.contains('Large_autojobs') || jobsViews.contains('Plugins Weekly')) {
                    currentBuild.displayName = "${currentBuild.displayName} (Priority: 30)"
                } else {
                    currentBuild.displayName = "${currentBuild.displayName} (Priority: 40)"
                }
                println("[INFO] Priority was set based on view of job")
            }
        } catch (e) {
            println("[ERROR] Failed to add priority into build name")
            println(e.toString())
        }

        if (env.CHANGE_URL) {
            def buildNumber = env.BUILD_NUMBER as int
            if (buildNumber > 1) milestone(buildNumber - 1)
            milestone(buildNumber) 
        } 

        def date = new Date()
        dateFormatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
        dateFormatter.setTimeZone(TimeZone.getTimeZone("GMT+3:00"))
        options.JOB_STARTED_TIME = dateFormatter.format(date)
        
        timestamps
        {
            String PRJ_PATH="${options.PRJ_ROOT}/${options.PRJ_NAME}"
            String REF_PATH="${PRJ_PATH}/ReferenceImages"
            String JOB_PATH="${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}".replace('%2F', '_')
            options['PRJ_PATH']="${PRJ_PATH}"
            options['REF_PATH']="${REF_PATH}"
            options['JOB_PATH']="${JOB_PATH}"

            if(options.get('BUILDER_TAG', '') == '')
                options['BUILDER_TAG'] = 'Builder'

            // if timeout doesn't set - use default (value in minutes)
            options['PREBUILD_TIMEOUT'] = options['PREBUILD_TIMEOUT'] ?: 20
            options['BUILD_TIMEOUT'] = options['BUILD_TIMEOUT'] ?: 40
            options['TEST_TIMEOUT'] = options['TEST_TIMEOUT'] ?: 20
            options['DEPLOY_TIMEOUT'] = options['DEPLOY_TIMEOUT'] ?: 20

            options['FAILED_STAGES'] = []

            def platformList = []
            def testResultList = []

            try {
                if (executePreBuild) {
                    node("Windows && PreBuild") {
                        ws("WS/${options.PRJ_NAME}_Build") {
                            stage("PreBuild") {
                                try {
                                    timeout(time: "${options.PREBUILD_TIMEOUT}", unit: 'MINUTES') {
                                        options["stage"] = "PreBuild"
                                        executePreBuild(options)
                                        if(!options['executeBuild']) {
                                            options.CBR = 'SKIPPED'
                                            echo "Build SKIPPED"
                                        }
                                    }
                                } catch (e) {
                                    println("[ERROR] Failed during prebuild stage on ${env.NODE_NAME}")
                                    println(e.toString())
                                    println(e.getMessage())
                                    String exceptionClassName = e.getClass().toString()
                                    if (exceptionClassName.contains("FlowInterruptedException")) {
                                        e.getCauses().each(){
                                            String causeClassName = it.getClass().toString()
                                            if (causeClassName.contains("ExceededTimeout")) {
                                                if (options.problemMessageManager) {
                                                    options.problemMessageManager.saveSpecificFailReason(NotificationConfiguration.TIMEOUT_EXCEEDED, "PreBuild")
                                                }
                                            }
                                        }
                                    }
                                    if (options.problemMessageManager) {
                                        options.problemMessageManager.saveGeneralFailReason(NotificationConfiguration.UNKNOWN_REASON, "PreBuild")
                                    }
                                    GithubNotificator.closeUnfinishedSteps(options, NotificationConfiguration.PRE_BUILD_STAGE_FAILED)
                                    throw e
                                }
                            }
                        }
                    }
                }

                def tasks = [:]

                platforms.split(';').each() {
                    if (it) {
                        List tokens = it.tokenize(':')
                        String osName = tokens.get(0)
                        String gpuNames = ""

                        Map newOptions = options.clone()
                        newOptions["stage"] = "Build"
                        newOptions["osName"] = osName

                        if (tokens.size() > 1) {
                            gpuNames = tokens.get(1)
                        }

                        platformList << osName
                        if (gpuNames) {
                            gpuNames.split(',').each() {
                                // if not split - testsList doesn't exists
                                newOptions.testsList = newOptions.testsList ?: ['']
                                newOptions['testsList'].each() { testName ->
                                    String asicName = it
                                    String testResultItem = testName ? "testResult-${asicName}-${osName}-${testName}" : "testResult-${asicName}-${osName}"
                                    testResultList << testResultItem
                                }
                            }
                        }

                        tasks[osName]=executePlatform(osName, gpuNames, executeBuild, executeTests, newOptions)
                    }
                }
                parallel tasks
            } catch (e) {
                println(e.toString())
                println(e.getMessage())
                currentBuild.result = "FAILURE"
                String exceptionClassName = e.getClass().toString()
                if (exceptionClassName.contains("FlowInterruptedException")) {
                    options.buildWasAborted = true
                    e.getCauses().each(){
                        // UserInterruption aborting by user
                        // ExceededTimeout aborting by timeout
                        // CancelledCause for aborting by new commit
                        String causeClassName = it.getClass().toString()
                        if (causeClassName.contains("CancelledCause")) {
                            executeDeploy = null
                        }
                    }
                }
            } finally {
                Boolean executeDeployStage = true
                if (options['executeTests']) {
                    if (options.containsKey('tests') && options.containsKey('testsPackage')){
                        if (options['testsPackage'] == 'none' && options['tests'].size() == 1 && options['tests'].get(0).length() == 0){
                            executeDeployStage = false
                        }
                    }
                } else {
                    executeDeployStage = false
                }
                if (executeDeploy && executeDeployStage) {
                    stage("Deploy") {
                        def reportBuilderLabels = "Windows && ReportBuilder"

                        options["stage"] = "Deploy"
                        def retringFunction = { nodesList, currentTry ->
                            executeDeploy(options, platformList, testResultList)
                            println("[INFO] Deploy stage finished without unexpected exception. Clean workspace")
                            cleanWS("Windows")
                        }
                        run_with_retries(reportBuilderLabels, options.DEPLOY_TIMEOUT, retringFunction, false, "Deploy", options, [], 2)
                    }
                }
            }
        }
    } catch (FlowInterruptedException e) {
        println(e.toString())
        println(e.getMessage())
        options.buildWasAborted = true
        println("Job was ABORTED by user. Job status: ${currentBuild.result}")
    } catch (e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        println("enableNotifications = ${options.enableNotifications}")
        if ("${options.enableNotifications}" == "true") {
            sendBuildStatusNotification(currentBuild.result,
                                        options.get('slackChannel', ''),
                                        options.get('slackBaseUrl', ''),
                                        options.get('slackTocken', ''),
                                        options)
        }

        println("Send Slack message to debug channels")
        sendBuildStatusToDebugSlack(options)

        println("[INFO] BUILD RESULT: ${currentBuild.result}")
        println("[INFO] BUILD CURRENT RESULT: ${currentBuild.currentResult}")
    }
}
