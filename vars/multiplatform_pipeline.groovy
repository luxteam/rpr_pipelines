import java.text.SimpleDateFormat;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import java.time.*;
import java.time.format.DateTimeFormatter;


def executeTestsNode(String osName, String gpuNames, def executeTests, Map options)
{
    if(gpuNames && options['executeTests'])
    {
        def testTasks = [:]
        gpuNames.split(',').each()
        {
            String asicName = it
            testTasks["Test-${it}-${osName}"] = {
                stage("Test-${asicName}-${osName}") {
                    // if not split - testsList doesn't exists
                    // TODO: replace testsList check to splitExecution var
                    options.testsList = options.testsList ?: ['']

                    options.testsList.each() { testName ->
                        println("Scheduling ${osName}:${asicName} ${testName}")

                        Map newOptions = options.clone()
                        newOptions['testResultsName'] = testName ? "testResult-${asicName}-${osName}-${testName}" : "testResult-${asicName}-${osName}"
                        newOptions['stageName'] = testName ? "${asicName}-${osName}-${testName}" : "${asicName}-${osName}"
                        newOptions['tests'] = testName ? testName : options.tests

                        def testerTag = "Tester"
                        if (options.TESTER_TAG){
                            if (options.TESTER_TAG.indexOf(' ') > -1){
                                testerTag = options.TESTER_TAG
                            }else {
                                testerTag = "${options.TESTER_TAG} && Tester"
                            }
                        }
                        def testerLabels = "${osName} && ${testerTag} && OpenCL && gpu${asicName}"

                        def retringFunction = { nodesList ->
                            try {
                                executeTests(osName, asicName, newOptions)
                            } catch(Exception e) {
                                // add info about retry to options
                                boolean added = false;
                                String testsOrTestPackage = newOptions['tests'];
                                if (testsOrTestPackage == ''){
                                    testsOrTestPackage = newOptions['testsPackage'].replace(' ', '_')
                                }
                                options['nodeRetry'].each{ retry ->
                                    if (retry['Testers'].equals(nodesList)){
                                        retry['Tries'][testsOrTestPackage].add([host:env.NODE_NAME, link:"${testsOrTestPackage}.${env.NODE_NAME}.crash.log", time: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))])
                                        added = true
                                    }
                                }
                                if (!added){
                                    options['nodeRetry'].add([Testers: nodesList, Tries: [["${testsOrTestPackage}": [[host:env.NODE_NAME, link:"${testsOrTestPackage}.${env.NODE_NAME}.crash.log", time: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))]]]]])
                                }
                                println options['nodeRetry'].inspect()

                                throw e
                            }
                        }

                        run_with_retries(testerLabels, options.TEST_TIMEOUT, retringFunction, true, "Test", newOptions)
                    }

                }
            }
        }
        parallel testTasks
    }
    else
    {
        echo "[WARNING] No tests found for ${osName}"
    }
}

def executePlatform(String osName, String gpuNames, def executeBuild, def executeTests, Map options)
{
    def retNode =
    {
        try
        {
            if(options['executeBuild'] && executeBuild)
            {
                stage("Build-${osName}")
                {
                    def builderLabels = "${osName} && ${options.BUILDER_TAG}"

                    def retringFunction = { nodesList ->
                        executeBuild(osName, options)
                    }

                    run_with_retries(builderLabels, options.BUILD_TIMEOUT, retringFunction, false, "Build", options, ['FlowInterruptedException'])
                }
            }
            if (options.containsKey('tests') && options.containsKey('testsPackage')){
                if (options['testsPackage'] != 'none' || options['tests'].size() == 0 || !(options['tests'].size() == 1 && options['tests'].get(0).length() == 0)){ // BUG: can throw exception if options['tests'] is string with length 1
                    executeTestsNode(osName, gpuNames, executeTests, options)
                }
            } else {
                executeTestsNode(osName, gpuNames, executeTests, options)
            }
        }
        catch (e)
        {
            println "[ERROR] executePlatform throw the exception"
            println "Exception: ${e.toString()}"
            println "Exception message: ${e.getMessage()}"
            println "Exception cause: ${e.getCause()}"
            println "Exception stack trace: ${e.getStackTrace()}"

            currentBuild.result = "FAILURE"
            options.FAILED_STAGES.add(e.toString())
            throw e
        }
    }
    return retNode
}

def call(String platforms, def executePreBuild, def executeBuild, def executeTests, def executeDeploy, Map options) {
    try {
        // if it's PR - supersede all previously launched executions
        if(env.CHANGE_ID) {
            //set logRotation for PRs
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);

            def buildNumber = env.BUILD_NUMBER as int
            if (buildNumber > 1) milestone(buildNumber - 1)
            milestone(buildNumber)
        }

        def date = new Date()
        dateFormatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
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

            // if timeout doesn't set - use default
            // value in minutes
            options['PREBUILD_TIMEOUT'] = options['PREBUILD_TIMEOUT'] ?: 20
            options['BUILD_TIMEOUT'] = options['BUILD_TIMEOUT'] ?: 40
            options['TEST_TIMEOUT'] = options['TEST_TIMEOUT'] ?: 20
            options['DEPLOY_TIMEOUT'] = options['DEPLOY_TIMEOUT'] ?: 20

            options['FAILED_STAGES'] = []

            def platformList = [];
            def testResultList = [];

            try
            {
                if(executePreBuild)
                {
                    node("Windows && PreBuild")
                    {
                        ws("WS/${options.PRJ_NAME}_PreBuild")
                        {
                            stage("PreBuild")
                            {
                                try {
                                    timeout(time: "${options.PREBUILD_TIMEOUT}", unit: 'MINUTES')
                                    {
                                        executePreBuild(options)
                                        if(!options['executeBuild'])
                                        {
                                            options.CBR = 'SKIPPED'
                                            echo "Build SKIPPED"
                                        }
                                    }
                                }
                                catch (e)
                                {
                                    println("[ERROR] Failed during prebuild stage on ${env.NODE_NAME}")
                                    println(e.toString());
                                    println(e.getMessage());
                                    throw e
                                }
                            }
                        }
                    }
                }

                def tasks = [:]

                platforms.split(';').each()
                {
                    if (it) {
                        List tokens = it.tokenize(':')
                        String osName = tokens.get(0)
                        String gpuNames = ""
                        if (tokens.size() > 1)
                        {
                            gpuNames = tokens.get(1)
                        }

                        platformList << osName
                        if(gpuNames)
                        {
                            gpuNames.split(',').each()
                            {
                                // if not split - testsList doesn't exists
                                options.testsList = options.testsList ?: ['']
                                options['testsList'].each() { testName ->
                                    String asicName = it
                                    String testResultItem = testName ? "testResult-${asicName}-${osName}-${testName}" : "testResult-${asicName}-${osName}"
                                    testResultList << testResultItem
                                }
                            }
                        }

                        tasks[osName]=executePlatform(osName, gpuNames, executeBuild, executeTests, options)
                    }
                }
                parallel tasks
            }
            catch (e)
            {
                println(e.toString());
                println(e.getMessage());
                currentBuild.result = "FAILURE"
                String exceptionClassName = e.getClass().toString()
                if (exceptionClassName.contains("FlowInterruptedException")) {
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
            }
            finally
            {
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
                if(executeDeploy && executeDeployStage)
                {
                    stage("Deploy")
                    {
                        def reportBuilderLabels = "Windows && ReportBuilder"

                        def retringFunction = { nodesList ->
                            executeDeploy(options, platformList, testResultList)
                        }

                        run_with_retries(reportBuilderLabels, options.DEPLOY_TIMEOUT, retringFunction, false, "Deploy", options, [], 2)
                    }
                }
            }
        }
    }
    catch (FlowInterruptedException e)
    {
        println(e.toString());
        println(e.getMessage());
        echo "Job was ABORTED by user. Job status: ${currentBuild.result}"
    }
    catch (e)
    {
        println(e.toString());
        println(e.getMessage());
        currentBuild.result = "FAILURE"
        throw e
    }
    finally
    {
        echo "enableNotifications = ${options.enableNotifications}"
        if("${options.enableNotifications}" == "true")
        {
            sendBuildStatusNotification(currentBuild.result,
                                        options.get('slackChannel', ''),
                                        options.get('slackBaseUrl', ''),
                                        options.get('slackTocken', ''),
                                        options)
        }

        echo "Send Slack message to debug channels"
        sendBuildStatusToDebugSlack(options)

        println "[INFO] BUILD RESULT: ${currentBuild.result}"
        println "[INFO] BUILD CURRENT RESULT: ${currentBuild.currentResult}"
    }
}
