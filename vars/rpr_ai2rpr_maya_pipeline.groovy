def getMayaPluginInstaller(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':

            println "PluginSHA: ${options.pluginWinSha}"

            if (!options.pluginWinSha || !fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginWinSha}.msi")) {

                clearBinariesWin()
                
                println "[INFO] The plugin does not exist in the storage. Downloading and copying..."
                downloadPlugin(osName, "Maya", options)

                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    move RadeonProRender*.msi "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.msi"
                """

            } else {
                println "[INFO] The plugin ${options.pluginWinSha}.msi exists in the storage."
            }

            break;

        default:
            echo "[WARNING] ${osName} is not supported"
    }
}


def executeGenTestRefCommand(String osName, Map options)
{
    dir('scripts')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                    make_rpr_baseline.bat
                """
                break;
            default:
                echo "[WARNING] ${osName} is not supported"
        }
    }
}


def buildRenderCache(String osName, String toolVersion, String log_name)
{
    dir("scripts") {
        switch(osName) {
            case 'Windows':
                bat "build_rpr_cache.bat ${toolVersion} >> ..\\${log_name}.cb.log  2>&1"
                break;
            default:
                echo "[WARNING] ${osName} is not supported"
        }
    }
}


def executeTestCommand(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':
            dir('scripts')
            {
                bat """
                render_rpr.bat ${options.testsPackage} \"${options.tests}\">> ../${STAGE_NAME}.log  2>&1
                """
            }
            break;
        default:
            echo "[WARNING] ${osName} is not supported"
    }
}


def executeTests(String osName, String asicName, Map options)
{

    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {

        timeout(time: "5", unit: 'MINUTES') {
            try {
                cleanWS(osName)
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_ai2rpr.git')
                dir('jobs/Scripts')
                {
                    unstash "conversionScript"
                }
            } catch(e) {
                println("[ERROR] Failed to prepare test group on ${env.NODE_NAME}")
                println(e.toString())
                throw e
            }
        }

        downloadAssets("/${options.PRJ_PATH}/ArnoldAssets/", 'ArnoldAssets')

        try {
            Boolean newPluginInstalled = false
            timeout(time: "15", unit: 'MINUTES') {
                getMayaPluginInstaller(osName, options)
                newPluginInstalled = installMSIPlugin(osName, 'Maya', options)
                println "[INFO] Install function on ${env.NODE_NAME} return ${newPluginInstalled}"
            }
            if (newPluginInstalled) {
                timeout(time: "5", unit: 'MINUTES') {
                    buildRenderCache(osName, options.toolVersion, options.stageName)
                    if(!fileExists("./Work/Results/ai2rpr/cache_building.jpg")){
                        println "[ERROR] Failed to build cache on ${env.NODE_NAME}. No output image found."
                        throw new Exception("No output image during build cache")
                    }
                }
            }
        } catch(e) {
            println(e.toString())
            println("[ERROR] Failed to install plugin on ${env.NODE_NAME}.")
            // deinstalling broken addon
            installMSIPlugin(osName, "Maya", options, false, true)
            throw e
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        String REF_PATH_PROFILE_OR="${options.REF_PATH}/Redshift-${osName}"
        String JOB_PATH_PROFILE_OR="${options.JOB_PATH}/Redshift-${osName}"

        outputEnvironmentInfo(osName)

        if(options['updateORRefs'])
        {
            dir('scripts')
            {
                bat """
                    render_or.bat ${options.testsPackage} \"${options.tests}\">> ../${STAGE_NAME}.log  2>&1
                """
                bat "make_original_baseline.bat"
            }
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE_OR)
        }
        else if (options['updateRefs'])
        {
            executeTestCommand(osName, options)
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else
        {   
            try
            {
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/')
                }
            } catch (e) {
                println("[WARNING] Baseline doesn't exist.")
            }
            try
            {
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE_OR}/${it}", './Work/Baseline/')
                }
            } catch (e) {
                println("[WARNING] Baseline doesn't exist.")
            }
            executeTestCommand(osName, options)
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
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        if (stashResults) {
            dir('Work')
            {
                if (fileExists("Results/ai2rpr/session_report.json")) {

                    def sessionReport = null
                    sessionReport = readJSON file: 'Results/ai2rpr/session_report.json'

                    // if none launched tests - mark build failed
                    if (sessionReport.summary.total == 0)
                    {
                        options.failureMessage = "None test was finished for: ${asicName}-${osName}"
                    }

                    // deinstalling broken addon
                    if (sessionReport.summary.total == sessionReport.summary.error) {
                        installMSIPlugin(osName, "Maya", options, false, true)
                    }

                    echo "Stashing test results to : ${options.testResultsName}"
                    stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true
                }
            }
        }
    }
}


def executePreBuild(Map options)
{
    // manual job
    if (options.forceBuild) {
        options.executeBuild = true
        options.executeTests = true
    // auto job
    } else {
        if (env.CHANGE_URL) {
            println "[INFO] Branch was detected as Pull Request"
            options.executeTests = true
            options.testsPackage = "Master"
        } else if (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop") {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
            options.executeTests = true
            options.testsPackage = "PR"
        } else {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
            options.testsPackage = "PR"
        }
    }

    dir('Arnold2RPRConvertTool-Maya')
    {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/Arnold2RPRConvertTool-Maya.git')

        stash includes: "convertAI2RPR.py", name: "conversionScript"

        options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
        options.commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true).split('\r\n')[2].trim()
        options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
    
        println "The last commit was written by ${options.commitAuthor}."
        println "Commit message: ${options.commitMessage}"
        println "Commit SHA: ${options.commitSHA}"

        if (options.projectBranch){
            currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
        } else {
            currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
        }

        options.pluginVersion = version_read("convertAI2RPR.py", 'ARNOLD2RPR_CONVERTER_VERSION = ')

        if (options['incrementVersion']) {
            if(env.BRANCH_NAME == "develop" && options.commitAuthor != "radeonprorender") {

                println "[INFO] Incrementing version of change made by ${options.commitAuthor}."
                println "[INFO] Current build version: ${options.pluginVersion}"

                new_version = version_inc(options.pluginVersion, 3)
                
                println "[INFO] New build version: ${new_version}"
                version_write("convertAI2RPR.py", 'ARNOLD2RPR_CONVERTER_VERSION = ', new_version)

                options.pluginVersion = version_read("convertAI2RPR.py", 'ARNOLD2RPR_CONVERTER_VERSION = ')
                println "[INFO] Updated build version: ${options.pluginVersion}"

                bat """
                  git add convertAI2RPR.py
                  git commit -m "buildmaster: version update to ${options.pluginVersion}"
                  git push origin HEAD:develop
                """
            }
        }

        currentBuild.description += "<b>Version:</b> ${options.pluginVersion}<br/>"
        currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
        currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
        currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

        bat """
            rename convertAI2RPR.py convertAI2RPR_${options.pluginVersion}.py
        """
        archiveArtifacts "convertAI2RPR*.py"
        String BUILD_NAME = "convertAI2RPR${options.pluginVersion}.py"
        rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""
    }

    if (env.BRANCH_NAME && (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop")) {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10']]]);
    } else if (env.BRANCH_NAME && env.BRANCH_NAME != "master" && env.BRANCH_NAME != "develop") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else if (env.JOB_NAME == "Arnold2RPRConvertToolWeekly-Maya") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '15']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    }

    println "[INFO] Test package: ${options.testsPackage}"

    def tests = []
    if(options.testsPackage != "none")
    {
        dir('jobs_test_ai2rpr')
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_ai2rpr.git')
            // json means custom test suite. Split doesn't supported
            if(options.testsPackage.endsWith('.json'))
            {
                options.testsList = ['']
            }
            // options.splitTestsExecution = false
            String tempTests = readFile("jobs/${options.testsPackage}")
            tempTests.split("\n").each {
                // TODO: fix: duck tape - error with line ending
                tests << "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
            }
            options.testsList = tests
            options.testsPackage = "none"
        }
    }
    else
    {
        options.tests.split(" ").each()
        {
            tests << "${it}"
        }
        options.testsList = tests
    }

}


def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_ai2rpr.git')

            dir("summaryTestResults")
            {
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
                            println(e.toString());
                            println(e.getMessage());
                        }

                    }
                }
            }

            String branchName = env.BRANCH_NAME ?: options.projectBranch

            try {
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"])
                {
                    dir("jobs_launcher") {
                        bat """
                        build_reports.bat ..\\summaryTestResults Arnold2RPR-Maya ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
                        """
                    }
                }
            } catch(e) {
                println("ERROR during report building")
                println(e.toString())
                println(e.getMessage())
            }

            try
            {
                dir("jobs_launcher") {
                    bat "get_status.bat ..\\summaryTestResults"
                }
            }
            catch(e)
            {
                println("ERROR during slack status generation")
                println(e.toString())
                println(e.getMessage())
            }

            try
            {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                if (summaryReport.error > 0) {
                    println("[INFO] Some tests marked as error. Build result = FAILURE.")
                    currentBuild.result = "FAILURE"
                }
                else if (summaryReport.failed > 0) {
                    println("[INFO] Some tests marked as failed. Build result = UNSTABLE.")
                    currentBuild.result = "UNSTABLE"
                }
            }
            catch(e)
            {
                println(e.toString())
                println(e.getMessage())
                println("CAN'T GET TESTS STATUS")
                currentBuild.result = "UNSTABLE"
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

            utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html", "Test Report", "Summary Report")
        }
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally
    {}
}


def call(String customBuildLinkWindows = "https://builds.rpr.cis.luxoft.com/bin_storage/RadeonProRenderMaya_2.9.8.msi",
         String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:NVIDIA_RTX2080TI',
         Boolean updateORRefs = false,
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         Boolean incrementVersion = true,
         String testsPackage = "",
         String tests = "",
         String toolVersion = "2020",
         Boolean isPreBuilt = true,
         Boolean forceBuild = false) {
    try
    {
        String PRJ_NAME="Arnold2RPRConvertTool-Maya"
        String PRJ_ROOT="rpr-tools"

        multiplatform_pipeline(platforms, this.&executePreBuild, null, this.&executeTests, this.&executeDeploy,
                               [customBuildLinkWindows:customBuildLinkWindows,
                                projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                updateORRefs:updateORRefs,
                                updateRefs:updateRefs,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                incrementVersion:incrementVersion,
                                testsPackage:testsPackage,
                                tests:tests,
                                toolVersion:toolVersion,
                                isPreBuilt:isPreBuilt,
                                forceBuild:forceBuild,
                                reportName:'Test_20Report',
                                TESTER_TAG:"ArnoldMaya",
                                TEST_TIMEOUT:120])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
