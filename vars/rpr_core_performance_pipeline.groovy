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
            // Not implemented now
            break;

        default:
            // Not implemented now
            break;
    }
}


def executeGenTestRefCommand(String osName, Map options, Boolean delete)
{

    dir('scripts')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                make_results_baseline.bat ${delete}
                """
                break;
            case 'OSX':
                // Not implemented now
                break;
            default:
                // Not implemented now
        }
    }
}


def executeTestCommand(String osName, String asicName, Map options)
{
    switch(osName) {
        case 'Windows':
            dir('scripts')
            {
                // TODO implement running of tests
            }
            break;
        case 'OSX':
            // Not implemented now
            break;
        default:
            // Not implemented now
    }
}


def executeGenTestRefCommand(String osName, Map options, Boolean delete)
{

    dir('scripts')
    {
        switch(osName)
        {
            case 'Windows':
                // TODO implement making of baselines
                break;
            case 'OSX':
                // Not implemented now
                break;
            default:
                // Not implemented now
        }
    }
}


def executeTests(String osName, String asicName, Map options)
{
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {
        try {
            timeout(time: "10", unit: 'MINUTES') {
                cleanWS(osName)
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_core_perf.git')
            }
        } catch (e) {
            if (utils.isTimeoutExceeded(e)) {
                throw new ExpectedExceptionWrapper("Failed to download tests repository due to timeout.", e)
            } else {
                throw new ExpectedExceptionWrapper("Failed to download tests repository.", e)
            }            
        }

        try {
            getCoreSDK(osName, options)
        } catch (e) {
            throw new ExpectedExceptionWrapper("Failed to download RadeonProRenderSDK package.", e)
        }

        try {
            downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/CoreAssets/", 'CoreAssetsPerformance')
        } catch (e) {
            throw new ExpectedExceptionWrapper("Failed to download test scenes.", e)
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, "", options.currentTry)

        try {
            if(options['updateRefs'].contains('Update'))
            {
                executeTestCommand(osName, asicName, options)
                executeGenTestRefCommand(osName, options, options['updateRefs'].contains('clean'))
                sendFiles('./Work/GeneratedBaselines/', REF_PATH_PROFILE)
                // delete generated baselines when they're sent 
                switch(osName) {
                    case 'Windows':
                        bat "if exist Work\\GeneratedBaselines rmdir /Q /S Work\\GeneratedBaselines"
                        break;
                    default:
                        // Not implemented now  
                }
            }
            else
            {
                try {
                    String baseline_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_core_autotests_baselines" : "/mnt/c/TestResources/rpr_core_autotests_baselines"
                    println "[INFO] Downloading reference images for ${options.tests}"
                    options.tests.split(" ").each() {
                        receiveFiles("${REF_PATH_PROFILE}/${it}", baseline_dir)
                    }
                } catch (e) {
                    println("[WARNING] Problem when copying baselines. " + e.getMessage())
                }
                executeTestCommand(osName, asicName, options)
            }
            options.executeTestsFinished = true
        } catch (e) {
            if (utils.isTimeoutExceeded(e)) {
                throw new ExpectedExceptionWrapper("Failed to execute tests due to timeout.", e)
            } else {
                throw new ExpectedExceptionWrapper("An error occurred while executing tests. Please contact support.", e)
            }
        }

    } catch (e) {
        if (options.currentTry < options.nodeReallocateTries) {
            stashResults = false
        }
        println(e.toString())
        println(e.getMessage())
        if (e instanceof ExpectedExceptionWrapper) {
            throw e
        } else {
            String errorMessage = "The reason is not automatically identified. Please contact support."
            throw new ExpectedExceptionWrapper(errorMessage, e)
        }
    }
    finally {
        try {
            dir("${options.stageName}") {
                utils.moveFiles(this, osName, "../*.log", ".")
                utils.moveFiles(this, osName, "../scripts/*.log", ".")
                utils.renameFile(this, osName, "launcher.engine.log", "${options.stageName}_engine_${options.currentTry}.log")
            }
            archiveArtifacts artifacts: "${options.stageName}/*.log", allowEmptyArchive: true
            if (stashResults) {
                dir('Work')
                {
                    if (fileExists("Results/Core/session_report.json")) {

                        def sessionReport = null
                        sessionReport = readJSON file: 'Results/Core/session_report.json'

                        echo "Stashing test results to : ${options.testResultsName}"
                        stash includes: '**/*', excludes: '**/cache/*', name: "${options.testResultsName}", allowEmpty: true

                        // reallocate node if there are still attempts
                        if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped || sessionReport.summary.total == 0) {
                            if (sessionReport.summary.total != sessionReport.summary.skipped){
                                collectCrashInfo(osName, options, options.currentTry)
                                String errorMessage
                                if (options.currentTry < options.nodeReallocateTries) {
                                    errorMessage = "All tests were marked as error. The test group will be restarted."
                                } else {
                                    errorMessage = "All tests were marked as error."
                                }
                                throw new ExpectedExceptionWrapper(errorMessage, new Exception(errorMessage))
                            }
                        }
                    }
                }
            }
        } catch (e) {
            // throw exception in finally block only if test stage was finished
            if (options.executeTestsFinished) {
                if (e instanceof ExpectedExceptionWrapper) {
                    throw e
                } else {
                    String errorMessage = "An error occurred while saving test results. Please contact support."
                    throw new ExpectedExceptionWrapper(errorMessage, e)
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


def executeBuild(String osName, Map options)
{
    if (options.sendToUMS){
        universeClientProd.stage("Build-" + osName , "begin")
        universeClientDev.stage("Build-" + osName , "begin")
    }

    try {
        dir('RadeonProRenderSDK')
        {
            try {
                checkOutBranchOrScm(options['projectBranch'], 'git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderSDK.git', false, options['prBranchName'], options['prRepoName'])
            } catch (e) {
                String errorMessage
                if (e.getMessage() && e.getMessage().contains("Branch not suitable for integration")) {
                    errorMessage = "Failed to merge branches."
                } else {
                    errorMessage = "Failed to download plugin repository."
                }
                problemMessageManager.saveSpecificFailReason(errorMessage, "Build", osName)
                throw e
            }
        }

        outputEnvironmentInfo(osName)

        try {
            switch(osName)
            {
            case 'Windows':
                executeBuildWindows(options);
                break;
            case 'OSX':
                // Not implemented now
                break;
            default:
                // Not implemented now
            }
        } catch (e) {
            String errorMessage = "Failed to create RadeonProRenderSDK package."
            problemMessageManager.saveSpecificFailReason(errorMessage, "Build", osName)
            throw e
        }
    }
    catch (e) {
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
    if (options.sendToUMS){
        universeClientProd.stage("Build-" + osName, "end")
        universeClientDev.stage("Build-" + osName, "end")
    }
}

def executePreBuild(Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderSDK.git')
    } catch (e) {
        String errorMessage = "Failed to download RadeonProRenderSDK repository."
        problemMessageManager.saveSpecificFailReason(errorMessage, "PreBuild")
        throw e
    }

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

    try {
        options.tests.split(" ").each()
        {
            tests << "${it}"
        }
        options.testsList = ['']
        options.tests = tests.join(" ")
    } catch (e) {
        String errorMessage = "Failed to configurate tests."
        problemMessageManager.saveSpecificFailReason(errorMessage, "PreBuild")
        throw e
    }
}


def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,AMD_RadeonVII,AMD_RX5700XT,NVIDIA_GF1080TI,NVIDIA_RTX2080',
         String updateRefs = 'No',
         String tests = "",
         Boolean sendToUMS = false,
         String tester_tag = 'Core')
{
    
    def nodeRetry = []
    Map options = [:]

    try 
    {
        try 
        {
            String PRJ_NAME="RadeonProRenderCorePerformance"
            String PRJ_ROOT="rpr-core"

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

            println "Platforms: ${platforms}"
            println "Tests: ${tests}"

            options << [projectBranch:projectBranch,
                        testsBranch:testsBranch,
                        updateRefs:updateRefs,
                        PRJ_NAME:PRJ_NAME,
                        PRJ_ROOT:PRJ_ROOT,
                        BUILDER_TAG:'BuilderS',
                        TESTER_TAG:tester_tag,
                        tests:tests.replace(',', ' '),
                        executeBuild:true,
                        executeTests:true,
                        reportName:'Test_20Report',
                        TEST_TIMEOUT:110,
                        nodeRetry: nodeRetry,
                        problemMessageManager: problemMessageManager,
                        platforms:platforms
                        ]
        }
        catch(e)
        {
            problemMessageManager.saveGeneralFailReason("Failed initialization.", "Init")

            throw e
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy, options)
    }
    catch(e) 
    {
        currentBuild.result = "FAILURE"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally
    {
        problemMessageManager.publishMessages()
    }
}
