import UniverseClient
import groovy.transform.Field
import groovy.json.JsonOutput;

@Field UniverseClient universeClient = new UniverseClient(this, "https://umsapi.cis.luxoft.com", env, "https://imgs.cis.luxoft.com", "AMD%20Radeon™%20ProRender%20Core")

def getCoreSDK(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':

            if (!fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip")) {

                clearBinariesWin()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "WindowsSDK"

                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    copy binWin64.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip"
                """

            } else {
                println "[INFO] The plugin ${options.pluginWinSha}.zip exists in the storage."
                bat """
                    copy "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip" binWin64.zip
                """
            }

            unzip zipFile: "binWin64.zip", dir: "rprSdk", quiet: true

            break;

        case 'OSX':

            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip")) {

                clearBinariesUnix()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "OSXSDK"

                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                    cp binMacOS.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip"
                """

            } else {
                println "[INFO] The plugin ${options.pluginOSXSha}.zip exists in the storage."
                sh """
                    cp "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip" binMacOS.zip
                """
            }

            unzip zipFile: "binMacOS.zip", dir: "rprSdk", quiet: true

            break;

        default:

            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip")) {

                clearBinariesUnix()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "Ubuntu18SDK"

                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                    cp binUbuntu18.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip"
                """

            } else {

                println "[INFO] The plugin ${options.pluginUbuntuSha}.zip exists in the storage."
                sh """
                    cp "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip" binUbuntu18.zip
                """
            }

            unzip zipFile: "binUbuntu18.zip", dir: "rprSdk", quiet: true

            break;
    }
}

def executeGenTestRefCommand(String osName, Map options)
{
    try
    {
        //for update existing manifest file
        receiveFiles("${options.REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
    }
    catch(e)
    {
        println("baseline_manifest.json not found")
    }

    dir('scripts')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                make_results_baseline.bat
                """
                break;
            case 'OSX':
                sh """
                ./make_results_baseline.sh
                """
                break;
            default:
                sh """
                ./make_results_baseline.sh
                """
        }
    }
}

def executeTestCommand(String osName, String asicName, Map options)
{
    build_id = "none"
    job_id = "none"
    if (options.sendToUMS && universeClient.build != null){
        build_id = universeClient.build["id"]
        job_id = universeClient.build["job_id"]
    }

    withCredentials([usernamePassword(credentialsId: 'image_service', usernameVariable: 'IS_USER', passwordVariable: 'IS_PASSWORD'),
        usernamePassword(credentialsId: 'universeMonitoringSystem', usernameVariable: 'UMS_USER', passwordVariable: 'UMS_PASSWORD')])
    {
        withEnv(["UMS_USE=${options.sendToUMS}", "UMS_BUILD_ID=${build_id}", "UMS_JOB_ID=${job_id}",
            "UMS_URL=${universeClient.url}", "UMS_ENV_LABEL=${osName}-${asicName}", "IS_URL=${universeClient.is_url}",
            "UMS_LOGIN=${UMS_USER}", "UMS_PASSWORD=${UMS_PASSWORD}", "IS_LOGIN=${IS_USER}", "IS_PASSWORD=${IS_PASSWORD}"])
        {
            switch(osName) {
                case 'Windows':
                    dir('scripts')
                    {
                        bat """
                        run.bat ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} >> ../${STAGE_NAME}.log 2>&1
                        """
                    }
                    break;
                case 'OSX':
                    dir('scripts')
                    {
                        withEnv(["LD_LIBRARY_PATH=../rprSdk:\$LD_LIBRARY_PATH"]) {
                            sh """
                            ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} >> ../${STAGE_NAME}.log 2>&1
                            """
                        }
                    }
                    break;
                default:
                    dir('scripts')
                    {
                        withEnv(["LD_LIBRARY_PATH=../rprSdk:\$LD_LIBRARY_PATH"]) {
                            sh """
                            ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} >> ../${STAGE_NAME}.log 2>&1
                            """
                        }
                    }
            }
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    // TODO: improve envs, now working on Windows testers only
    if (options.sendToUMS){
        universeClient.stage("Tests-${osName}-${asicName}", "begin")
    }
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {

        timeout(time: "10", unit: 'MINUTES') {
            try {
                cleanWS(osName)
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_core.git')
                getCoreSDK(osName, options)
            } catch(e) {
                println("[ERROR] Failed to prepare test group on ${env.NODE_NAME}")
                println(e.toString())
                throw e
            }
        }

        downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/CoreAssets/", 'CoreAssets')

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName)

        if(options['updateRefs'])
        {
            executeTestCommand(osName, asicName, options)
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else if(options.updateRefsByOne)
        {
            // Update ref images from one card to others
            // TODO: Fix hardcode naming
            executeTestCommand(osName, asicName, options)
            executeGenTestRefCommand(osName, options)
            ['AMD_RXVEGA', 'AMD_WX9100', 'AMD_WX7100', 'AMD_RadeonVII', 'NVIDIA_GF1080TI', 'NVIDIA_RTX2080'].each
            {
                sendFiles('./Work/Baseline/', "${options.REF_PATH}/${it}-Windows")
            }
        }
        else
        {
            try {
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/')
                }
            } catch(e) {
                println("No baseline")
            }
            executeTestCommand(osName, asicName, options)
        }
    } catch (e) {
        if (options.currentTry < options.nodeReallocateTries) {
            stashResults = false
        }
        println(e.toString())
        println(e.getMessage())
        options.failureMessage = "Failed during testing: ${asicName}-${osName}"
        options.failureError = e.getMessage()
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        if (stashResults) {
            dir('Work')
            {
                if (fileExists("Results/Core/session_report.json")) {

                    def sessionReport = null
                    sessionReport = readJSON file: 'Results/Core/session_report.json'

                    // if none launched tests - mark build failed
                    if (sessionReport.summary.total == 0)
                    {
                        options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                        currentBuild.result = "FAILED"
                    }

                    if (options.sendToUMS)
                    {
                        universeClient.stage("Tests-${osName}-${asicName}", "end")
                    }

                    echo "Stashing test results to : ${options.testResultsName}"
                    stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

                    // reallocate node if there are still attempts
                    if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped) {
                        if (sessionReport.summary.total != sessionReport.summary.skipped){
                            collectCrashInfo(osName, options)
                            if (osName == "Ubuntu18"){
                                sh """
                                    echo "Restarting Unix Machine...."
                                    hostname
                                    (sleep 3; sudo shutdown -r now) &
                                """
                                sleep(60)
                            }
                            if (options.currentTry < options.nodeReallocateTries) {
                                throw new Exception("All tests crashed")
                            }
                        }
                    }
                }
            }
        }
    }
}

def executeBuildWindows(Map options)
{
    dir('RadeonProRenderSDK/RadeonProRender/binWin64')
    {
        zip archive: true, dir: '.', glob: '', zipFile: 'binWin64.zip'
        stash includes: 'binWin64.zip', name: 'WindowsSDK'
        options.pluginWinSha = sha1 'binWin64.zip'
    }
}

def executeBuildOSX(Map options)
{
    dir('RadeonProRenderSDK/RadeonProRender/binMacOS')
    {
        zip archive: true, dir: '.', glob: '', zipFile: 'binMacOS.zip'
        stash includes: 'binMacOS.zip', name: 'OSXSDK'
        options.pluginOSXSha = sha1 'binMacOS.zip'
    }

}

def executeBuildLinux(Map options)
{
    dir('RadeonProRenderSDK/RadeonProRender/binUbuntu18')
    {
        zip archive: true, dir: '.', glob: '', zipFile: 'binUbuntu18.zip'
        stash includes: 'binUbuntu18.zip', name: 'Ubuntu18SDK'
        options.pluginUbuntuSha = sha1 'binUbuntu18.zip'
    }

}

def executeBuild(String osName, Map options)
{
    if (options.sendToUMS){
        universeClient.stage("Build-" + osName , "begin")
    }

    try {
        dir('RadeonProRenderSDK')
        {
            checkOutBranchOrScm(options['projectBranch'], 'git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderSDK.git')
        }

        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows':
            executeBuildWindows(options);
            break;
        case 'OSX':
            executeBuildOSX(options);
            break;
        default:
            executeBuildLinux(options);
        }
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
    if (options.sendToUMS){
        universeClient.stage("Build-" + osName, "end")
    }
}

def executePreBuild(Map options)
{

    checkOutBranchOrScm(options['projectBranch'], 'git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderSDK.git')

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim().replace('\n', '')
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"
    println "Commit shortSHA: ${options.commitShortSHA}"

    if (options.projectBranch){
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }

    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    if (env.CHANGE_URL) {
        println "Branch was detected as Pull Request"
        options.isPR = true
    }

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '30']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '30']]]);
    }


    def tests = []
    options.groupsUMS = []
    if(options.testsPackage != "none")
    {
        dir('jobs_test_core')
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_core.git')
            // json means custom test suite. Split doesn't supported
            String tempTests = readFile("jobs/${options.testsPackage}")
            tempTests.split("\n").each {
                // TODO: fix: duck tape - error with line ending
            tests << "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
        }
        options.tests = tests
        options.testsPackage = "none"
        options.groupsUMS = tests
        }
    }
    else {
        options.tests.split(" ").each()
        {
            tests << "${it}"
        }
        options.tests = tests
        options.groupsUMS = tests
    }

    options.testsList = ['']
    options.tests = tests.join(" ")

    if (options.sendToUMS)
    {
        try
        {
            // Universe : auth because now we in node
            // If use httpRequest in master slave will catch 408 error
            universeClient.tokenSetup()

            println("Test groups:")
            println(options.groupsUMS)

            // create build ([OS-1:GPU-1, ... OS-N:GPU-N], ['Suite1', 'Suite2', ..., 'SuiteN'])
            universeClient.createBuild(options.universePlatforms, options.groupsUMS)
        }
        catch (e)
        {
            println(e.toString())
        }
    }
}


def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_core.git')

            List lostStashes = []

            dir("summaryTestResults")
            {
                unstashCrashInfo(options['nodeRetry'])
                testResultList.each()
                {
                    dir("$it".replace("testResult-", ""))
                    {
                        try
                        {
                            unstash "$it"
                        }catch(e)
                        {
                            echo "Can't unstash ${it}"
                            lostStashes.add("'$it'".replace("testResult-", ""))
                            println(e.toString());
                            println(e.getMessage());
                        }

                    }
                }
            }

            try {
            	dir("core_tests_configuration") {
                    bat(returnStatus: false, script: "%CIS_TOOLS%\\receiveFilesCoreConf.bat ${options.PRJ_ROOT}/${options.PRJ_NAME}/CoreAssets/ .")
            	}
            } catch (e) {
                println("[ERROR] Can't download json files with core tests configuration")
            }

            try {
                dir("jobs_launcher") {
                    bat """
                    count_lost_tests.bat \"${lostStashes}\" .. ..\\summaryTestResults default \"${options.tests}\"
                    """
                }
            } catch (e) {
                println("[ERROR] Can't generate number of lost tests")
            }

            dir("jobs_launcher")
            {
                if(options.projectBranch != "") {
                    options.branchName = options.projectBranch
                } else {
                    options.branchName = env.BRANCH_NAME
                }
                if(options.incrementVersion) {
                    options.branchName = "master"
                }

                options.commitMessage = options.commitMessage.replace("'", "")
                options.commitMessage = options.commitMessage.replace('"', '')

                def retryInfo = JsonOutput.toJson(options.nodeRetry)
                bat """
                build_reports.bat ..\\summaryTestResults Core ${options.commitSHA} ${options.branchName} \"${escapeCharsByUnicode(options.commitMessage)}\" \"${escapeCharsByUnicode(retryInfo.toString())}\"
                """

                bat "get_status.bat ..\\summaryTestResults"
            }

            try
            {
                dir("jobs_launcher") {
                    archiveArtifacts "launcher.engine.log"
                }
            }
            catch(e)
            {
                println("ERROR during archiving launcher.engine.log")
                println(e.toString())
                println(e.getMessage())
            }

            try
            {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                if (summaryReport.error > 0) {
                    println("Some tests crashed")
                    currentBuild.result="FAILED"
                }
                if (summaryReport.failed > 0) {
                    println("Some tests failed")
                    currentBuild.result="UNSTABLE"
                }
            }
            catch(e)
            {
                println("CAN'T GET TESTS STATUS")
            }

            try
            {
                options.testsStatus = readFile("summaryTestResults/slack_status.json")
            }
            catch(e)
            {
                println(e.toString())
                println(e.getMessage())
                options.testsStatus = ""
            }

            publishHTML([allowMissing: false,
                         alwaysLinkToLastBuild: false,
                         keepAll: true,
                         reportDir: 'summaryTestResults',
                         reportFiles: 'summary_report.html, performance_report.html, compare_report.html',
                         reportName: 'Test Report',
                         reportTitles: 'Summary Report, Performance Report, Compare Report'])

            if (options.sendToUMS) {
                try {
                    String status = currentBuild.result ?: 'SUCCESSFUL'
                    universeClient.changeStatus(status)
                }
                catch (e){
                    println(e.getMessage())
                }
            }
        }
    }
    catch (e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally
    {}
}


def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,AMD_RadeonVII,NVIDIA_GF1080TI,NVIDIA_RTX2080;OSX:AMD_RXVEGA;Ubuntu18:AMD_RadeonVII,NVIDIA_GTX980',
         Boolean updateRefs = false,
         Boolean updateRefsByOne = false,
         Boolean enableNotifications = true,
         String renderDevice = "gpu",
         String testsPackage = "Full",
         String tests = "",
         String width = "0",
         String height = "0",
         String iterations = "0",
         Boolean sendToUMS = true,
         String tester_tag = 'Core') {
    try
    {
        String PRJ_NAME="RadeonProRenderCore"
        String PRJ_ROOT="rpr-core"

        def nodeRetry = []

        gpusCount = 0
        platforms.split(';').each()
        { platform ->
            List tokens = platform.tokenize(':')
            if (tokens.size() > 1)
            {
                gpuNames = tokens.get(1)
                gpuNames.split(',').each()
                {
                    gpusCount += 1
                }
            }
        }

        def universePlatforms = convertPlatforms(platforms);

        println "Platforms: ${platforms}"
        println "Tests: ${tests}"
        println "Tests package: ${testsPackage}"
        println "UMS platforms: ${universePlatforms}"

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                updateRefs:updateRefs,
                                updateRefsByOne:updateRefsByOne,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                BUILDER_TAG:'BuilderS',
                                TESTER_TAG:tester_tag,
                                slackChannel:"${SLACK_CORE_CHANNEL}",
                                renderDevice:renderDevice,
                                testsPackage:testsPackage,
                                tests:tests.replace(',', ' '),
                                executeBuild:true,
                                executeTests:true,
                                reportName:'Test_20Report',
                                TEST_TIMEOUT:110,
                                width:width,
                                gpusCount:gpusCount,
                                height:height,
                                iterations:iterations,
                                sendToUMS:sendToUMS,
                                universePlatforms: universePlatforms,
                                nodeRetry: nodeRetry
                                ])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        if (sendToUMS){
            universeClient.changeStatus(currentBuild.result)
        }
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
