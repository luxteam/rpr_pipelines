def executeTestCommand(String osName, String asicName, Map options) {
    timeout(time: options.TEST_TIMEOUT, unit: 'MINUTES') { 
        dir('scripts') {
            bat"""
                run.bat \"${options.testsPackage}\" \"${options.tests}\" >> \"../${options.stageName}_${options.currentTry}.log\"  2>&1
            """
        }
    }
}


def executeTests(String osName, String asicName, Map options)
{
    try {
        timeout(time: "5", unit: 'MINUTES') {
            cleanWS(osName)
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_ml.git')
            println("[INFO] Preparing on ${env.NODE_NAME} successfully finished.")
        }
    } catch (e) {
        if (utils.isTimeoutExceeded(e)) {
            println("Failed to download tests repository due to timeout.")
        } else {
            println("Failed to download tests repository.")
        }
        currentBuild.result = "FAILURE"
        throw e
    }

    try {
        downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/MLAssets/", 'MLAssets')
    } catch (e) {
        println("Failed to download test scenes.")
        currentBuild.result = "FAILURE"
        throw e
    }

    try {
        outputEnvironmentInfo(osName, "${STAGE_NAME}")
        dir("RadeonML") {
            unstash "app${osName}"
        }

        executeTestCommand(osName, asicName, options)
    }
    catch (e) {
        println(e.toString())
        println(e.getMessage())
        error_message = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        dir("${options.stageName}") {
            utils.moveFiles(this, osName, "../*.log", ".")
            utils.moveFiles(this, osName, "../scripts/*.log", ".")
            utils.renameFile(this, osName, "launcher.engine.log", "${options.stageName}_engine_${options.currentTry}.log")
        }
        archiveArtifacts artifacts: "${options.stageName}/*.log", allowEmptyArchive: true
        dir('Work') {
            if (fileExists("Results/ML/session_report.json")) {

                def sessionReport = null
                sessionReport = readJSON file: 'Results/ML/session_report.json'

                echo "Stashing test results to : ${options.testResultsName}"
                stash includes: '**/*', excludes: '**/cache/**', name: "${options.testResultsName}", allowEmpty: true
            }
        }
    }
}


def executeWindowsBuildCommand(Map options, String build_type){

    bat """
        mkdir build-${build_type}
        cd build-${build_type}
        cmake ${options.cmakeKeysWin} -DRML_TENSORFLOW_DIR=${WORKSPACE}/third_party/tensorflow -DMIOpen_INCLUDE_DIR=${WORKSPACE}/third_party/miopen -DMIOpen_LIBRARY_DIR=${WORKSPACE}/third_party/miopen .. >> ..\\${STAGE_NAME}_${build_type}.log 2>&1
        set msbuild=\"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe\"
        %msbuild% RadeonML.sln -property:Configuration=${build_type} >> ..\\${STAGE_NAME}_${build_type}.log 2>&1
    """
    
    bat """
        cd build-${build_type}
        xcopy ..\\third_party\\miopen\\MIOpen.dll ${build_type}
        xcopy ..\\third_party\\tensorflow\\windows\\* ${build_type}
        mkdir ${build_type}\\rml
        mkdir ${build_type}\\rml_internal
        xcopy ..\\rml\\include\\rml\\*.h* ${build_type}\\rml
        xcopy ..\\rml\\include\\rml_internal\\*.h* ${build_type}\\rml_internal
    """

    zip dir: "build-${build_type}\\${build_type}", zipFile: "build-${build_type}\\${CIS_OS}_${build_type}.zip"
    archiveArtifacts "build-${build_type}\\${CIS_OS}_${build_type}.zip"
    
    zip archive: true, dir: "build-${build_type}\\${build_type}", glob: "RadeonML*.lib, RadeonML*.dll, MIOpen.dll, libtensorflow*, test*.exe", zipFile: "${CIS_OS}_${build_type}.zip"

}


def executeBuildWindows(Map options)
{
    bat """
        xcopy ..\\\\RML_thirdparty\\\\MIOpen third_party\\\\miopen /s/y/i
        xcopy ..\\\\RML_thirdparty\\\\tensorflow third_party\\\\tensorflow /s/y/i
    """

    options.cmakeKeysWin ='-G "Visual Studio 15 2017 Win64" -DRML_DIRECTML=ON -DRML_MIOPEN=ON -DRML_TENSORFLOW_CPU=ON -DRML_TENSORFLOW_CUDA=OFF -DRML_MPS=OFF'

    executeWindowsBuildCommand(options, "Release")
}


def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

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

    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    options.testsList = ['']
}


def executeBuild(String osName, Map options)
{
    String error_message = ""
    String context = "[${options.PRJ_NAME}] [BUILD] ${osName}"

    try
    {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

        receiveFiles("rpr-ml/MIOpen/${osName}/*", "../RML_thirdparty/MIOpen")
        receiveFiles("rpr-ml/tensorflow/*", "../RML_thirdparty/tensorflow")

        outputEnvironmentInfo(osName)

        withEnv(["CIS_OS=${osName}"]) {
            switch (osName) {
                case 'Windows':
                    executeBuildWindows(options)
                    break
                case 'OSX':
                    print("[WARNING] ${osName} is not supported")
                    break
                default:
                    print("[WARNING] ${osName} is not supported")
            }
        }

        dir('build-Release/Release') {
            stash includes: '*', name: "app${osName}"
        }
    } catch (e) {
        println(e.getMessage())
        error_message = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts "*.log"
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_ml.git')

            List lostStashes = []

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
                            lostStashes.add("'$it'".replace("testResult-", ""))
                            println(e.toString());
                            println(e.getMessage());
                        }

                    }
                }
            }

            try {
                dir("jobs_launcher") {
                    bat """
                    count_lost_tests.bat \"${lostStashes}\" .. ..\\summaryTestResults \"${options.splitTestsExecution}\" \"${options.testsPackage}\" \"${options.tests.toString().replace(" ", "")}\" \"\" \"{}\"
                    """
                }
            } catch (e) {
                println("[ERROR] Can't generate number of lost tests")
            }

            try {
                def buildNumber = ""
                if (options.collectTrackedMetrics) {
                    buildNumber = env.BUILD_NUMBER
                    try {
                        dir("summaryTestResults/tracked_metrics") {
                            receiveFiles("${options.PRJ_ROOT}/${options.PRJ_NAME}/TrackedMetrics/${env.JOB_NAME}/", ".")
                        }
                    } catch (e) {
                        println("[WARNING] Failed to download history of tracked metrics.")
                        println(e.toString())
                        println(e.getMessage())
                    }
                }
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}", "BUILD_NAME=${options.baseBuildName}"])
                {
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

                        bat """
                        build_performance_reports.bat ..\\summaryTestResults ML ${options.commitSHA} ${options.branchName} \"${utils.escapeCharsByUnicode(options.commitMessage)}\" \"${buildNumber}\"
                        """

                        bat "get_status.bat ..\\summaryTestResults"
                    }
                }
                if (options.collectTrackedMetrics) {
                    try {
                        dir("summaryTestResults/tracked_metrics") {
                            sendFiles(".", "${options.PRJ_ROOT}/${options.PRJ_NAME}/TrackedMetrics/${env.JOB_NAME}")
                        }
                    } catch (e) {
                        println("[WARNING] Failed to update history of tracked metrics.")
                        println(e.toString())
                        println(e.getMessage())
                    }
                }  
            } catch(e) {
                if (utils.isReportFailCritical(e.getMessage())) {
                    println("[ERROR] Failed to build test report.")
                    println(e.toString())
                    println(e.getMessage())
                    if (!options.testDataSaved) {
                        try {
                            // Save test data for access it manually anyway
                            utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html", \
                                "Test Report", "Summary Report")
                            options.testDataSaved = true 
                        } catch(e1) {
                            println("[WARNING] Failed to publish test data.")
                            println(e.toString())
                            println(e.getMessage())
                        }
                    }
                    throw e
                } else {
                    currentBuild.result = "FAILURE"
                }
            }

            try
            {
                dir("jobs_launcher") {
                    archiveArtifacts "launcher.engine.log"
                }
            }
            catch(e)
            {
                println("[ERROR] during archiving launcher.engine.log")
                println(e.toString())
                println(e.getMessage())
            }

            Map summaryTestResults = [:]
            try
            {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                summaryTestResults['passed'] = summaryReport.passed
                summaryTestResults['failed'] = summaryReport.failed
                summaryTestResults['error'] = summaryReport.error
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
                println("[ERROR] CAN'T GET TESTS STATUS")
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

            utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", \
                "Test Report", "Summary Report, Performance Report, Compare Report")
        }
    }
    catch (e) {
        println(e.toString())
        println(e.getMessage())
        throw e
    }
    finally
    {}
}

def call(String projectBranch = "",
         String testsBranch = "master",
         String testsPackage = "",
         String tests = "",
         String platforms = 'Windows:NVIDIA_RTX2080',
         String projectRepo='git@github.com:Radeon-Pro/RadeonML.git',
         Boolean enableNotifications = false,
         Boolean collectTrackedMetrics = true)
{
    String PRJ_ROOT='rpr-ml'
    String PRJ_NAME='RadeonML'

    println "Platforms: ${platforms}"
    println "Tests: ${tests}"
    println "Tests package: ${testsPackage}"

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
            [platforms:platforms,
             projectBranch:projectBranch,
             testsBranch:testsBranch,
             enableNotifications:enableNotifications,
             PRJ_NAME:PRJ_NAME,
             PRJ_ROOT:PRJ_ROOT,
             projectRepo:projectRepo,
             testsPackage:testsPackage,
             tests:tests.replace(',', ' '),
             BUILDER_TAG:'BuilderML',
             TESTER_TAG:'MLPerf',
             BUILD_TIMEOUT:'30',
             TEST_TIMEOUT:'60',
             executeBuild:true,
             executeTests:true,
             retriesForTestStage:1,
             collectTrackedMetrics:collectTrackedMetrics])
}
