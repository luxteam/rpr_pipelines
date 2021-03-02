import universe.*
import groovy.transform.Field
import groovy.json.JsonOutput
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import TestsExecutionType
import java.util.concurrent.atomic.AtomicInteger


@Field final String PRODUCT_NAME = "AMD%20Radeon™%20ProRender%20Viewer"


def getViewerTool(String osName, Map options)
{
    switch(osName) {
        case 'Windows':

            if (!fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip")) {

                clearBinariesWin()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "appWindows"
                
                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    copy RprViewer_Windows.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip"
                """

            } else {
                println "[INFO] The plugin ${options.pluginWinSha}.zip exists in the storage."
                bat """
                    copy "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip" RprViewer_Windows.zip
                """
            }

            unzip zipFile: "RprViewer_Windows.zip", dir: "RprViewer", quiet: true
            break

        case 'OSX':
            println "OSX isn't supported"
            break

        default:
            
            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip")) {

                clearBinariesUnix()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "appUbuntu18"
                
                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                    cp RprViewer_Ubuntu18.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip"
                """ 

            } else {

                println "[INFO] The plugin ${options.pluginUbuntuSha}.zip exists in the storage."
                sh """
                    cp "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip" RprViewer_Ubuntu18.zip
                """
            }

            unzip zipFile: "RprViewer_Ubuntu18.zip", dir: "RprViewer", quiet: true
    }
}


def executeGenTestRefCommand(String osName, Map options, Boolean delete)
{
    dir('scripts') {
        switch(osName) {
            case 'Windows':
                bat """
                    make_results_baseline.bat ${delete}
                """
                break
            case 'OSX':
                sh """
                    ./make_results_baseline.sh ${delete}
                """
                break
            default:
                sh """
                    ./make_results_baseline.sh ${delete}
                """
        }
    }
}

def executeTestCommand(String osName, String asicName, Map options)
{
    def testTimeout = options.timeouts["${options.tests}"]
    String testsNames = options.tests
    String testsPackageName = options.testsPackage
    if (options.testsPackage != "none" && !options.isPackageSplitted) {
        if (testsNames.contains(".json")) {
            // if tests package isn't splitted and it's execution of this package - replace test group for non-splitted package by empty string
            testsNames = ""
        } else {
            // if tests package isn't splitted and it isn't execution of this package - replace tests package by empty string
            testsPackageName = ""
        }
    }

    println "Set timeout to ${testTimeout}"

    timeout(time: testTimeout, unit: 'MINUTES') { 
        UniverseManager.executeTests(osName, asicName, options) {
            switch(osName) {
                case 'Windows':
                    String driverPostfix = asicName.endsWith('_Beta') ? " Beta Driver" : ""

                    dir('scripts') {
                        bat """
                            set CIS_RENDER_DEVICE=%CIS_RENDER_DEVICE%${driverPostfix}
                            run.bat \"${testsPackageName}\" \"${testsNames}\" ${options.testCaseRetries} ${options.updateRefs} 1>> \"../${options.stageName}_${options.currentTry}.log\"  2>&1
                        """
                    }
                    break

                case 'OSX':
                    println("OSX is not supported")
                    break;

                default:
                    dir('scripts') {
                        withEnv(["LD_LIBRARY_PATH=../RprViewer/engines/hybrid:\$LD_LIBRARY_PATH"]) {
                            sh """
                                chmod +x ../RprViewer/RadeonProViewer
                                chmod +x run.sh
                                ./run.sh \"${testsPackageName}\" \"${testsNames}\" ${options.testCaseRetries} ${options.updateRefs} 1>> \"../${options.stageName}_${options.currentTry}.log\"  2>&1
                            """
                        }
                    }
            }
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    if (options.sendToUMS){
        options.universeManager.startTestsStage(osName, asicName, options)
    }
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {
        withNotifications(title: options["stageName"], options: options, logUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
            timeout(time: "10", unit: "MINUTES") {
                cleanWS(osName)
                checkOutBranchOrScm(options["testsBranch"], "git@github.com:luxteam/jobs_test_rprviewer.git")
                
            }
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_PACKAGE) {
            getViewerTool(osName, options)
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_SCENES) {
            String assets_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_viewer_autotests_assets" : "/mnt/c/TestResources/rpr_viewer_autotests_assets"
            downloadFiles("/volume1/Assets/rpr_viewer_autotests/", assets_dir)
        }

        String REF_PATH_PROFILE="/volume1/Baselines/rpr_viewer_autotests/${asicName}-${osName}"
        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, "", options.currentTry)

        if (options["updateRefs"].contains("Update")) {
            withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.EXECUTE_TESTS) {
                executeTestCommand(osName, asicName, options)
                executeGenTestRefCommand(osName, options, options["updateRefs"].contains("clean"))
                uploadFiles("./Work/GeneratedBaselines/", REF_PATH_PROFILE)
                // delete generated baselines when they're sent 
                switch(osName) {
                    case "Windows":
                        bat "if exist Work\\GeneratedBaselines rmdir /Q /S Work\\GeneratedBaselines"
                        break
                    default:
                        sh "rm -rf ./Work/GeneratedBaselines"        
                }
            }
        } else {
            withNotifications(title: options["stageName"], printMessage: true, options: options, configuration: NotificationConfiguration.COPY_BASELINES) {
                String baseline_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_viewer_autotests_baselines" : "/mnt/c/TestResources/rpr_viewer_autotests_baselines"
                println("[INFO] Downloading reference images for ${options.tests}")
                options.tests.split(" ").each() {
                    if (it.contains(".json")) {
                        downloadFiles("${REF_PATH_PROFILE}/", baseline_dir)
                    } else {
                        downloadFiles("${REF_PATH_PROFILE}/${it}", baseline_dir)
                    }
                }
            }
            withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.EXECUTE_TESTS) {
                executeTestCommand(osName, asicName, options)
            }
        }
        options.executeTestsFinished = true

        if (options["errorsInSuccession"]["${osName}-${asicName}"] != -1) {
            // mark that one group was finished and counting of errored groups in succession must be stopped
            options["errorsInSuccession"]["${osName}-${asicName}"] = new AtomicInteger(-1)
        }

    } catch (e) {
        String additionalDescription = ""
        if (options.currentTry + 1 < options.nodeReallocateTries) {
            stashResults = false
        } else {
            if (!options["errorsInSuccession"]["${osName}-${asicName}"]) {
                options["errorsInSuccession"]["${osName}-${asicName}"] = new AtomicInteger(0)
            }
            Integer errorsInSuccession = options["errorsInSuccession"]["${osName}-${asicName}"]
            // if counting of errored groups in succession must isn't stopped
            if (errorsInSuccession >= 0) {
                errorsInSuccession = options["errorsInSuccession"]["${osName}-${asicName}"].addAndGet(1)
            
                if (errorsInSuccession >= 3) {
                    additionalDescription = "Number of errored groups in succession exceeded (max - 3). Next groups for this platform will be aborted"
                }
            }
        }
        println(e.toString())
        println(e.getMessage())
        
        if (e instanceof ExpectedExceptionWrapper) {
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, "${e.getMessage()} ${additionalDescription}", "${BUILD_URL}")
            throw new ExpectedExceptionWrapper("${e.getMessage()}\n${additionalDescription}", e.getCause())
        } else {
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, "${NotificationConfiguration.REASON_IS_NOT_IDENTIFIED} ${additionalDescription}", "${BUILD_URL}")
            throw new ExpectedExceptionWrapper("${NotificationConfiguration.REASON_IS_NOT_IDENTIFIED}\n${additionalDescription}", e)
        }
    } finally {
        try {
            dir("${options.stageName}") {
                utils.moveFiles(this, osName, "../*.log", ".")
                utils.moveFiles(this, osName, "../scripts/*.log", ".")
                utils.renameFile(this, osName, "launcher.engine.log", "${options.stageName}_engine_${options.currentTry}.log")
            }
            archiveArtifacts artifacts: "${options.stageName}/*.log", allowEmptyArchive: true
            if (options.sendToUMS) {
                options.universeManager.sendToMINIO(options, osName, "../${options.stageName}", "*.log")
            }
            if (stashResults) {
                dir('Work')
                {
                    if (fileExists("Results/RprViewer/session_report.json")) {

                        def sessionReport = null
                        sessionReport = readJSON file: 'Results/RprViewer/session_report.json'

                        if (options.sendToUMS) {
                            options.universeManager.finishTestsStage(osName, asicName, options)
                        }

                        if (sessionReport.summary.error > 0) {
                            GithubNotificator.updateStatus("Test", options['stageName'], "action_required", options, NotificationConfiguration.SOME_TESTS_ERRORED, "${BUILD_URL}")
                        } else if (sessionReport.summary.failed > 0) {
                            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, NotificationConfiguration.SOME_TESTS_FAILED, "${BUILD_URL}")
                        } else {
                            GithubNotificator.updateStatus("Test", options['stageName'], "success", options, NotificationConfiguration.ALL_TESTS_PASSED, "${BUILD_URL}")
                        }

                        println("Stashing test results to : ${options.testResultsName}")
                        stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

                        // reallocate node if there are still attempts
                        // if test group is fully errored or number of test cases is equal to zero
                        if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped || sessionReport.summary.total == 0) {
                            // check that group isn't fully skipped
                            if (sessionReport.summary.total != sessionReport.summary.skipped || sessionReport.summary.total == 0){
                                // remove broken rprviewer
                                removeInstaller(osName: osName, options: options, extension: "zip")
                                collectCrashInfo(osName, options, options.currentTry)
                                if (osName == "Ubuntu18"){
                                    sh """
                                        echo "Restarting Unix Machine...."
                                        hostname
                                        (sleep 3; sudo shutdown -r now) &
                                    """
                                    sleep(60)
                                }
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
                    GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, e.getMessage(), "${BUILD_URL}")
                    throw e
                } else {
                    GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, NotificationConfiguration.FAILED_TO_SAVE_RESULTS, "${BUILD_URL}")
                    throw new ExpectedExceptionWrapper(NotificationConfiguration.FAILED_TO_SAVE_RESULTS, e)
                }
            }
        }
    }
}

def executeBuildWindows(Map options)
{
    withNotifications(title: "Windows", options: options, logUrl: "${BUILD_URL}/artifact/Build-Windows.log", configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
        bat """
            cmake . -B build -G "Visual Studio 15 2017" -A x64 >> ${STAGE_NAME}.log 2>&1
            cmake --build build --target RadeonProViewer --config Release >> ${STAGE_NAME}.log 2>&1
        """
    }

    withNotifications(title: "Windows", options: options, artifactUrl: "${BUILD_URL}/artifact/RprViewer_Windows.zip", configuration: NotificationConfiguration.BUILD_PACKAGE) {
        bat """
            mkdir ${options.DEPLOY_FOLDER}
            xcopy config.json ${options.DEPLOY_FOLDER}
            xcopy README.md ${options.DEPLOY_FOLDER}
            xcopy UIConfig.json ${options.DEPLOY_FOLDER}
            xcopy sky.hdr ${options.DEPLOY_FOLDER}
            xcopy build\\Viewer\\Release\\RadeonProViewer.exe ${options.DEPLOY_FOLDER}\\RadeonProViewer.exe*

            xcopy shaders ${options.DEPLOY_FOLDER}\\shaders /y/i/s

            mkdir ${options.DEPLOY_FOLDER}\\rml\\lib
            xcopy rml\\lib\\RadeonML-DirectML.dll ${options.DEPLOY_FOLDER}\\rml\\lib\\RadeonML-DirectML.dll*
            xcopy rif\\models ${options.DEPLOY_FOLDER}\\rif\\models /s/i/y
            xcopy rif\\lib ${options.DEPLOY_FOLDER}\\rif\\lib /s/i/y
            del /q ${options.DEPLOY_FOLDER}\\rif\\lib\\*.lib
        """

        //temp fix
        bat """
            xcopy build\\viewer\\engines ${options.DEPLOY_FOLDER}\\engines /s/i/y
        """

        def controlFiles = ["config.json", "UIConfig.json", "sky.hdr", "RadeonProViewer.exe", "rml/lib/RadeonML-DirectML.dll"]
            controlFiles.each() {
            if (!fileExists("${options.DEPLOY_FOLDER}/${it}")) {
                error "Not found ${it}"
            }
        }

        zip archive: true, dir: "${options.DEPLOY_FOLDER}", glob: '', zipFile: "RprViewer_Windows.zip"
        stash includes: "RprViewer_Windows.zip", name: "appWindows"
        options.pluginWinSha = sha1 "RprViewer_Windows.zip"
                        
        if (options.sendToUMS) {
            options.universeManager.sendToMINIO(options, "Windows", "..", "RprViewer_Windows.zip", false)  
        }
    }
}


def executeBuildLinux(Map options)
{
    withNotifications(title: "Ubuntu18", options: options, logUrl: "${BUILD_URL}/artifact/Build-Ubuntu18.log", configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
        sh """
            mkdir build
            cd build
            cmake .. >> ../${STAGE_NAME}.log 2>&1
            make -j 8 >> ../${STAGE_NAME}.log 2>&1
        """
    }

    withNotifications(title: "Ubuntu18", options: options, artifactUrl: "${BUILD_URL}/artifact/RprViewer_Ubuntu18.zip", configuration: NotificationConfiguration.BUILD_PACKAGE) {
        sh """
            mkdir ${options.DEPLOY_FOLDER}
            cp config.json ${options.DEPLOY_FOLDER}
            cp README.md ${options.DEPLOY_FOLDER}
            cp UIConfig.json ${options.DEPLOY_FOLDER}
            cp sky.hdr ${options.DEPLOY_FOLDER}
            cp build/viewer/RadeonProViewer ${options.DEPLOY_FOLDER}/RadeonProViewer

            cp -rf shaders ${options.DEPLOY_FOLDER}/shaders

            mkdir ${options.DEPLOY_FOLDER}/rif
            cp -rf rif/models ${options.DEPLOY_FOLDER}/rif/models
            cp -rf rif/lib ${options.DEPLOY_FOLDER}/rif/lib

            cp -rf build/viewer/engines ${options.DEPLOY_FOLDER}/engines
        """

        zip archive: true, dir: "${options.DEPLOY_FOLDER}", glob: '', zipFile: "RprViewer_Ubuntu18.zip"
        stash includes: "RprViewer_Ubuntu18.zip", name: "appUbuntu18"
        options.pluginUbuntuSha = sha1 "RprViewer_Ubuntu18.zip"

        if (options.sendToUMS) {
            options.universeManager.sendToMINIO(options, "Ubuntu18", "..", "RprViewer_Ubuntu18.zip", false)
        }
    }
}

def executeBuild(String osName, Map options)
{
    if (options.sendToUMS){
        options.universeManager.startBuildStage(osName)
    }

    try {
        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
            checkOutBranchOrScm(options["projectBranch"], options["projectRepo"])
        }

        outputEnvironmentInfo(osName)

        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
            switch(osName) {
                case 'Windows':
                    executeBuildWindows(options)
                    break
                case 'OSX':
                    println("OSX isn't supported.")
                    break
                default:
                    executeBuildLinux(options)
            }
        }
    } catch (e) {
        throw e
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        if (options.sendToUMS) {
            options.universeManager.sendToMINIO(options, osName, "..", "*.log")
            options.universeManager.finishBuildStage(osName)
        }
    }
}

def executePreBuild(Map options)
{
    if (env.CHANGE_URL) {
        println("[INFO] Branch was detected as Pull Request")
        options.testsPackage = "PR.json"
    } else if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.testsPackage = "master.json"
    } else if (env.BRANCH_NAME) {
        options.testsPackage = "smoke.json"
    }

    withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
        checkOutBranchOrScm(options["projectBranch"], options["projectRepo"], true)
    }

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

    if (options['incrementVersion']) {
        withNotifications(title: "Jenkins build configuration", printMessage: true, options: options, configuration: NotificationConfiguration.CREATE_GITHUB_NOTIFICATOR) {
            GithubNotificator githubNotificator = new GithubNotificator(this, options)
            githubNotificator.init(options)
            options["githubNotificator"] = githubNotificator
            githubNotificator.initPreBuild("${BUILD_URL}")
        }
        
        String testsFromCommit = utils.getTestsFromCommitMessage(options.commitMessage)
        if(env.BRANCH_NAME != "develop" && testsFromCommit) {
            // get a list of tests from commit message for auto builds
            options.tests = testsFromCommit
            println "[INFO] Test groups mentioned in commit message: ${options.tests}"
        }
    }

    def tests = []
    options.timeouts = [:]
    options.groupsUMS = []

    withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.CONFIGURE_TESTS) {
        dir('jobs_test_rprviewer') {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_rprviewer.git')
            dir ('jobs_launcher') {
                options['jobsLauncherBranch'] = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            }
            options['testsBranch'] = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            println "[INFO] Test branch hash: ${options['testsBranch']}"

            def packageInfo

            if (options.testsPackage != "none") {
                packageInfo = readJSON file: "jobs/${options.testsPackage}"
                options.isPackageSplitted = packageInfo["split"]
                // if it's build of manual job and package can be splitted - use list of tests which was specified in params (user can change list of tests before run build)
                if (options.forceBuild && options.isPackageSplitted && options.tests) {
                    options.testsPackage = "none"
                }
            }

            if(options.testsPackage != "none") {
                if (options.isPackageSplitted) {
                    println("[INFO] Tests package '${options.testsPackage}' can be splitted")
                } else {
                    // save tests which user wants to run with non-splitted tests package
                    if (options.tests) {
                        tests = options.tests.split(" ") as List
                        options.groupsUMS = tests.clone()
                    }
                    println("[INFO] Tests package '${options.testsPackage}' can't be splitted")
                }

                // modify name of tests package if tests package is non-splitted (it will be use for run package few time with different engines)
                String modifiedPackageName = "${options.testsPackage}~"
                packageInfo["groups"].each() {
                    if (options.isPackageSplitted) {
                        tests << it.key
                        options.groupsUMS << it.key
                    } else {
                        if (!tests.contains(it.key)) {
                            options.groupsUMS << it.key
                        } else {
                            // add duplicated group name in name of package group name for exclude it
                            modifiedPackageName = "${modifiedPackageName},${it.key}"
                        }
                    }
                }

                options.tests = utils.uniteSuites(this, "jobs/weights.json", tempTests)
                options.tests.each() {
                    def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }
                modifiedPackageName = modifiedPackageName.replace('~,', '~')

                if (options.isPackageSplitted) {
                    options.testsPackage = "none"
                } else {
                    options.testsPackage = modifiedPackageName
                    tests << modifiedPackageName
                    options.timeouts[options.testsPackage] = options.NON_SPLITTED_PACKAGE_TIMEOUT + options.ADDITIONAL_XML_TIMEOUT
                }
            } else {
                options.groupsUMS = options.tests.split(" ") as List
                options.tests = utils.uniteSuites(this, "jobs/weights.json", options.tests.split(" ") as List)
                options.tests.each() {
                    def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }
            }
            options.tests = tests

            options.skippedTests = [:]
            if (options.updateRefs == "No") {
                options.platforms.split(';').each() {
                    if (it) {
                        List tokens = it.tokenize(':')
                        String osName = tokens.get(0)
                        String gpuNames = ""
                        if (tokens.size() > 1) {
                            gpuNames = tokens.get(1)
                        }

                        if (gpuNames) {
                            gpuNames.split(',').each() {
                                for (test in options.tests) {
                                    try {
                                        dir ("jobs_launcher") {
                                            String output = bat(script: "is_group_skipped.bat ${it} ${osName} \"\" \"..\\jobs\\Tests\\${test}\\test_cases.json\"", returnStdout: true).trim()
                                            if (output.contains("True")) {
                                                if (!options.skippedTests.containsKey(test)) {
                                                    options.skippedTests[test] = []
                                                }
                                                options.skippedTests[test].add("${it}-${osName}")
                                            }
                                        }
                                    } catch(Exception e) {
                                        println(e.toString())
                                        println(e.getMessage())
                                    }
                                }
                            }
                        }
                    }
                }
                println "Skipped test groups:"
                println options.skippedTests.inspect()
            } else {
                println "Ignore searching of tested groups due to updating of baselines"
            }
        }

        if (env.BRANCH_NAME && options.githubNotificator) {
            options.githubNotificator.initChecks(options, "${BUILD_URL}")
        }

        options.testsList = options.tests

        println "timeouts: ${options.timeouts}"

        if (options.sendToUMS) {
            options.universeManager.createBuilds(options)
        }
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        if (options['executeTests'] && testResultList) {
            withNotifications(title: "Building test report", options: options, startUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
                checkOutBranchOrScm(options["testsBranch"], "git@github.com:luxteam/jobs_test_rprviewer.git")
            }

            List lostStashes = []

            dir("summaryTestResults") {
                unstashCrashInfo(options['nodeRetry'])
                testResultList.each() {
                    dir("$it".replace("testResult-", "")) {
                        try {
                            unstash "$it"
                        } catch(e) {
                            echo "[ERROR] Failed to unstash ${it}"
                            lostStashes.add("'$it'".replace("testResult-", ""))
                            println(e.toString())
                            println(e.getMessage())
                        }
                    }
                }
            }

            try {
                dir("jobs_launcher") {
                    bat """
                    count_lost_tests.bat \"${lostStashes}\" .. ..\\summaryTestResults \"${options.splitTestsExecution}\" \"${options.testsPackage}\" \"[]\" \"\" \"{}\"
                    """
                }
            } catch (e) {
                println("[ERROR] Can't generate number of lost tests")
            }
            
            String branchName = env.BRANCH_NAME ?: options.projectBranch

            try {
                GithubNotificator.updateStatus("Deploy", "Building test report", "in_progress", options, NotificationConfiguration.BUILDING_REPORT, "${BUILD_URL}")
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}", "BUILD_NAME=${options.baseBuildName}"]) {
                    dir("jobs_launcher") {
                        def retryInfo = JsonOutput.toJson(options.nodeRetry)
                        dir("..\\summaryTestResults") {
                            JSON jsonResponse = JSONSerializer.toJSON(retryInfo, new JsonConfig())
                            writeJSON file: 'retry_info.json', json: jsonResponse, pretty: 4
                        }
                        if (options.sendToUMS) {
                            options.universeManager.sendStubs(options, "..\\summaryTestResults\\lost_tests.json", "..\\summaryTestResults\\skipped_tests.json", "..\\summaryTestResults\\retry_info.json")
                        }
                        bat """
                            build_reports.bat ..\\summaryTestResults "RprViewer" ${options.commitSHA} ${branchName} \"${utils.escapeCharsByUnicode(options.commitMessage)}\"
                        """
                    }
                }
            } catch(e) {
                String errorMessage = utils.getReportFailReason(e.getMessage())
                GithubNotificator.updateStatus("Deploy", "Building test report", "failure", options, errorMessage, "${BUILD_URL}")
                if (utils.isReportFailCritical(e.getMessage())) {
                    options.problemMessageManager.saveSpecificFailReason(errorMessage, "Deploy")
                    println("[ERROR] Failed to build test report.")
                    println(e.toString())
                    println(e.getMessage())
                    if (!options.testDataSaved) {
                        try {
                            // Save test data for access it manually anyway
                            utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", \
                                "Test Report", "Summary Report, Performance Report, Compare Report")
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
                    options.problemMessageManager.saveGlobalFailReason(errorMessage)
                }
            }

            try {
                dir("jobs_launcher") {
                    bat "get_status.bat ..\\summaryTestResults"
                }
            } catch(e) {
                println("[ERROR] during slack status generation")
                println(e.toString())
                println(e.getMessage())
            }

            try {
                dir("jobs_launcher") {
                    archiveArtifacts "launcher.engine.log"
                }
            } catch(e) {
                println("[ERROR] during archiving launcher.engine.log")
                println(e.toString())
                println(e.getMessage())
            }

            Map summaryTestResults = [:]
            try {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                summaryTestResults['passed'] = summaryReport.passed
                summaryTestResults['failed'] = summaryReport.failed
                summaryTestResults['error'] = summaryReport.error
                if (summaryReport.error > 0) {
                    println("[INFO] Some tests marked as error. Build result = FAILURE.")
                    currentBuild.result = "FAILURE"

                    options.problemMessageManager.saveGlobalFailReason(NotificationConfiguration.SOME_TESTS_ERRORED)
                } else if (summaryReport.failed > 0) {
                    println("[INFO] Some tests marked as failed. Build result = UNSTABLE.")
                    currentBuild.result = "UNSTABLE"

                    options.problemMessageManager.saveUnstableReason(NotificationConfiguration.SOME_TESTS_FAILED)
                }
            } catch(e) {
                println(e.toString())
                println(e.getMessage())
                println("[ERROR] CAN'T GET TESTS STATUS")
                options.problemMessageManager.saveUnstableReason(NotificationConfiguration.CAN_NOT_GET_TESTS_STATUS)
                currentBuild.result = "UNSTABLE"
            }

            try {
                options.testsStatus = readFile("summaryTestResults/slack_status.json")
            } catch(e) {
                println(e.toString())
                println(e.getMessage())
                options.testsStatus = ""
            }

            withNotifications(title: "Building test report", options: options, configuration: NotificationConfiguration.PUBLISH_REPORT) {
                utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", \
                    "Test Report", "Summary Report, Performance Report, Compare Report")

                if (summaryTestResults) {
                    // add in description of status check information about tests statuses
                    // Example: Report was published successfully (passed: 69, failed: 11, error: 0)
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", options, "${NotificationConfiguration.REPORT_PUBLISHED} Results: passed - ${summaryTestResults.passed}, failed - ${summaryTestResults.failed}, error - ${summaryTestResults.error}.", "${BUILD_URL}/Test_20Report")
                } else {
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", options, NotificationConfiguration.REPORT_PUBLISHED, "${BUILD_URL}/Test_20Report")
                }
            }
        }
    } catch(e) {
        println(e.toString())
        println(e.getMessage())
        throw e
    }
}

def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_RadeonVII,AMD_RadeonVII_Beta,NVIDIA_RTX2080',
         String updateRefs = 'No',
         Boolean enableNotifications = true,
         Boolean incrementVersion = true,
         String testsPackage = "",
         String tests = "",
         Boolean splitTestsExecution = true,
         Boolean sendToUMS = true,
         String tester_tag = 'RprViewer',
         String parallelExecutionTypeString = "TakeAllNodes",
         Integer testCaseRetries = 3)
{
    ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)
    Map options = [:]
    options["stage"] = "Init"
    options["problemMessageManager"] = problemMessageManager

    def nodeRetry = []
    Map errorsInSuccession = [:]

    try {
        withNotifications(options: options, configuration: NotificationConfiguration.INITIALIZATION) {

            sendToUMS = updateRefs.contains('Update') || sendToUMS
            
            def universePlatforms = convertPlatforms(platforms);

            def parallelExecutionType = TestsExecutionType.valueOf(parallelExecutionTypeString)

            println "Platforms: ${platforms}"
            println "Tests: ${tests}"
            println "Tests package: ${testsPackage}"
            println "Tests execution type: ${parallelExecutionType}"
            println "Send to UMS: ${sendToUMS} "
            println "UMS platforms: ${universePlatforms}"

            options << [projectBranch:projectBranch,
                        testsBranch:testsBranch,
                        updateRefs:updateRefs,
                        enableNotifications:enableNotifications,
                        incrementVersion:incrementVersion,
                        PRJ_NAME:'RadeonProViewer',
                        PRJ_ROOT:'rpr-core',
                        projectRepo:'git@github.com:Radeon-Pro/RadeonProViewer.git',
                        TESTER_TAG:tester_tag,
                        executeBuild:true,
                        executeTests:true,
                        splitTestsExecution:splitTestsExecution,
                        DEPLOY_FOLDER:"RprViewer",
                        testsPackage:testsPackage,
                        TEST_TIMEOUT:45,
                        ADDITIONAL_XML_TIMEOUT:15,
                        NON_SPLITTED_PACKAGE_TIMEOUT:45,
                        DEPLOY_TIMEOUT:45,
                        tests:tests,
                        nodeRetry: nodeRetry,
                        errorsInSuccession: errorsInSuccession,
                        sendToUMS:sendToUMS,
                        universePlatforms: universePlatforms,
                        problemMessageManager: problemMessageManager,
                        platforms:platforms,
                        parallelExecutionType:parallelExecutionType,
                        parallelExecutionTypeString: parallelExecutionTypeString,
                        testCaseRetries:testCaseRetries
                        ]

            if (sendToUMS) {
                UniverseManager universeManager = UniverseManagerFactory.get(this, options, env, PRODUCT_NAME)
                universeManager.init()
                options["universeManager"] = universeManager
            }
        } 

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy, options)
    } catch(e) {
        currentBuild.result = "FAILURE"
        println(e.toString())
        println(e.getMessage())
        throw e
    } finally {
        String problemMessage = options.problemMessageManager.publishMessages()
        if (options.sendToUMS) {
            options.universeManager.closeBuild(problemMessage, options)
        }
    }
}
