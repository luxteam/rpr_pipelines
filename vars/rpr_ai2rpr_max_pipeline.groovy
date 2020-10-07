def executeGenTestRefCommand(String osName, Map options)
{
    executeTestCommand(osName, options)

    dir('scripts')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                make_rpr_baseline.bat
                """
                break;
            case 'OSX':
                sh """
                ./make_rpr_baseline.sh
                """
                break;
            default:
                sh """
                ./make_rpr_baseline.sh
                """
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
    case 'OSX':
        sh """
        echo 'sample image' > ./OutputImages/sample_image.txt
        """
        break;
    default:
        sh """
        echo 'sample image' > ./OutputImages/sample_image.txt
        """
    }
}


def executeTests(String osName, String asicName, Map options)
{
    try {
        checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_ai2rpr_max.git')
        dir('jobs/Scripts')
        {
            bat "del convertAI2RPR.ms"
            unstash "convertionScript"
        }

        downloadAssets("/${options.PRJ_PATH}/ArnoldMaxAssets/", 'ArnoldMaxAssets')

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        String REF_PATH_PROFILE_OR="${options.REF_PATH}/Arnold-${osName}"
        String JOB_PATH_PROFILE_OR="${options.JOB_PATH}/Arnold-${osName}"

        outputEnvironmentInfo(osName)

        if(options['updateORRefs'])
        {
            dir('scripts')
            {
                bat """render_or.bat ${options.testsPackage} \"${options.tests}\">> ../${STAGE_NAME}.log  2>&1"""
                bat "make_original_baseline.bat"
            }
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE_OR)
        }
        else if(options['updateRefs'])
        {
            receiveFiles("bin_storage/RadeonProRender3dsMax_2.3.403.msi", "/mnt/c/TestResources/")
            options.pluginWinSha = 'c:\\TestResources\\RadeonProRender3dsMax_2.3.403'
            installMSIPlugin(osName, 'Max', options, false)

            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else
        {
            receiveFiles("bin_storage/RadeonProRender3dsMax_2.3.403.msi", "/mnt/c/TestResources/")
            options.pluginWinSha = 'c:\\TestResources\\RadeonProRender3dsMax_2.3.403'
            installMSIPlugin(osName, 'Max', options, false)
            try
            {
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/')
                }
            }
            catch (e) {
            }
            try
            {
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE_OR}/${it}", './Work/Baseline/')
                }
            } catch (e) {}
            executeTestCommand(osName, options)
        }
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        echo "Stashing test results to : ${options.testResultsName}"
        dir('Work')
        {
            stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

            def sessionReport = readJSON file: 'Results/ai2rpr/session_report.json'
            if (sessionReport.summary.total == 0) {
                options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
            }
            /*sessionReport.results.each{ testName, testConfigs ->
                testConfigs.each{ key, value ->
                    if ( value.render_duration == 0)
                    {
                        error "Crashed tests detected"
                    }
                }
            }*/
        }
    }
}

def executeBuildWindows(Map options)
{

}

def executeBuildOSX(Map options)
{

}

def executeBuildLinux(Map options)
{

}

def executeBuild(String osName, Map options)
{
    try {
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
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}

def executePreBuild(Map options)
{
    dir('Arnold2RPRConvertTool-3dsMax')
    {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/Arnold2RPRConvertTool-3dsMax.git')
        stash includes: "convertAI2RPR.ms", name: "convertionScript"

        AUTHOR_NAME = bat (
                script: "git show -s --format=%%an HEAD ",
                returnStdout: true
                ).split('\r\n')[2].trim()

        echo "The last commit was written by ${AUTHOR_NAME}."
        options.AUTHOR_NAME = AUTHOR_NAME

        commitMessage = bat ( script: "git log --format=%%B -n 1", returnStdout: true )
        echo "Commit message: ${commitMessage}"

        options.commitMessage = commitMessage.split('\r\n')[2].trim()
        echo "Opt.: ${options.commitMessage}"
        options['commitSHA'] = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

    }

    if(options.splitTestsExecution) {
        def tests = []
        if(options.testsPackage != "none")
        {
            dir('jobs_test_ai2rpr_max')
            {
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_ai2rpr_max.git')
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
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_ai2rpr_max.git')

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
                        build_reports.bat ..\\summaryTestResults Arnold2RPR-Max ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
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

def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_WX9100',
         Boolean updateORRefs = false,
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         String testsPackage = "",
         String tests = "") {
    try
    {
        String PRJ_NAME="Arnold2RPRConvertTool-Max"
        String PRJ_ROOT="rpr-tools"

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                updateORRefs:updateORRefs,
                                updateRefs:updateRefs,
                                enableNotifications:enableNotifications,
                                executeTests:true,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                TEST_TIMEOUT:650,
                                testsPackage:testsPackage,
                                tests:tests,
                                reportName:'Test_20Report',
                                splitTestsExecution:false])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
