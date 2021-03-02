import universe.*
import groovy.transform.Field
import groovy.json.JsonOutput
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import TestsExecutionType
import java.util.concurrent.atomic.AtomicInteger


@Field final String PRODUCT_NAME = "AMD%20Radeon™%20ProRender%20Core"


def getCoreSDK(String osName, Map options) {
    switch(osName) {
        case 'Windows':

            if (options['isPreBuilt']) {
                println "[INFO] CoreSDKWinSha: ${options['pluginWinSha']}"

                if (options['pluginWinSha']) {
                    if (fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options['pluginWinSha']}.zip")) {
                        println "[INFO] The Core SDK ${options['pluginWinSha']}.zip exists in the storage."
                    } else {
                        clearBinariesWin()

                        println "[INFO] The Core SDK does not exist in the storage. Downloading and copying..."
                        downloadPlugin(osName, "binWin64", options)

                        bat """
                            IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                            copy binWin64*.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options['pluginWinSha']}.zip"
                        """
                    }
                } else {
                    clearBinariesWin()

                    println "[INFO] The Core SDK does not exist in the storage. PluginSha is unknown. Downloading and copying..."
                    downloadPlugin(osName, "binWin64", options)

                    bat """
                        IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                        copy binWin64*.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip"
                    """
                }

                unzip zipFile: "binWin64_Windows.zip", dir: "rprSdk", quiet: true

            } else {
                if (!fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip")) {

                    clearBinariesWin()

                    println "[INFO] The Core SDK does not exist in the storage. Unstashing and copying..."
                    unstash "WindowsSDK"

                    bat """
                        IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                        copy binWin64.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip"
                    """

                } else {
                    println "[INFO] The Core SDK ${options.pluginWinSha}.zip exists in the storage."
                    bat """
                        copy "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip" binWin64.zip
                    """
                }
                unzip zipFile: "binWin64.zip", dir: "rprSdk", quiet: true
            }

            break

        case "OSX":

            if (options['isPreBuilt']) {

                println "[INFO] CoreSDKOSXSha: ${options['pluginOSXSha']}"

                if (options['pluginOSXSha']) {
                    if (fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip")) {
                        println "[INFO] The Core SDK ${options['pluginOSXSha']}.zip exists in the storage."
                    } else {
                        clearBinariesUnix()

                        println "[INFO] The Core SDK does not exist in the storage. Downloading and copying..."
                        downloadPlugin(osName, "binMacOS", options)

                        sh """
                            mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                            cp binMacOS*.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip"
                        """
                    }
                } else {
                    clearBinariesUnix()

                    println "[INFO] The Core SDK does not exist in the storage. PluginSha is unknown. Downloading and copying..."
                    downloadPlugin(osName, "binMacOS", options)

                    sh """
                        mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                        cp binMacOS*.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip"
                    """
                }

                unzip zipFile: "binMacOS_OSX.zip", dir: "rprSdk", quiet: true

            } else {
                if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip")) {

                    clearBinariesUnix()

                    println "[INFO] The Core SDK does not exist in the storage. Unstashing and copying..."
                    unstash "OSXSDK"

                    sh """
                        mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                        cp binMacOS.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip"
                    """

                } else {
                    println "[INFO] The Core SDK ${options.pluginOSXSha}.zip exists in the storage."
                    sh """
                        cp "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip" binMacOS.zip
                    """
                }

                unzip zipFile: "binMacOS.zip", dir: "rprSdk", quiet: true
            }

            break

        default:

            if (options['isPreBuilt']) {

                println "[INFO] CoreSDKUbuntuSha: ${options['pluginUbuntuSha']}"

                if (options['pluginUbuntuSha']) {
                    if (fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip")) {
                        println "[INFO] The Core SDK ${options['pluginUbuntuSha']}.zip exists in the storage."
                    } else {
                        clearBinariesUnix()

                        println "[INFO] The Core SDK does not exist in the storage. Downloading and copying..."
                        downloadPlugin(osName, "bin${osName}", options)

                        sh """
                            mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                            cp bin${osName}*.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip"
                        """
                    }
                } else {
                    clearBinariesUnix()

                    println "[INFO] The Core SDK does not exist in the storage. PluginSha is unknown. Downloading and copying..."
                    downloadPlugin(osName, "bin${osName}", options)

                    sh """
                        mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                        cp bin${osName}*.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip"
                    """
                }

                unzip zipFile: "bin${osName}_${osName}.zip", dir: "rprSdk", quiet: true

            } else {
                if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip")) {

                    clearBinariesUnix()

                    println "[INFO] The Core SDK does not exist in the storage. Unstashing and copying..."
                    unstash "${osName}SDK"

                    sh """
                        mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                        cp bin${osName}.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip"
                    """

                } else {

                    println "[INFO] The Core SDK ${options.pluginUbuntuSha}.zip exists in the storage."
                    sh """
                        cp "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip" bin${osName}.zip
                    """
                }

                unzip zipFile: "bin${osName}.zip", dir: "rprSdk", quiet: true
            }
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
    UniverseManager.executeTests(osName, asicName, options) {
        switch(osName) {
            case 'Windows':
                dir('scripts') {
                    bat """
                        run.bat ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} ${options.updateRefs} >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                    """
                }
                break
            case 'OSX':
                dir('scripts') {
                    withEnv(["LD_LIBRARY_PATH=../rprSdk:\$LD_LIBRARY_PATH"]) {
                        sh """
                            ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} ${options.updateRefs} >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                        """
                    }
                }
                break
            default:
                dir('scripts') {
                    withEnv(["LD_LIBRARY_PATH=../rprSdk:\$LD_LIBRARY_PATH"]) {
                        sh """
                            ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} ${options.updateRefs} >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                        """
                    }
                }
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    // TODO: improve envs, now working on Windows testers only
    if (options.sendToUMS){
        options.universeManager.startTestsStage(osName, asicName, options)
    }
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {
        withNotifications(title: options["stageName"], options: options, logUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
            timeout(time: "5", unit: "MINUTES") {
                cleanWS(osName)
                checkOutBranchOrScm(options["testsBranch"], "git@github.com:luxteam/jobs_test_core.git")
            }
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_PACKAGE) {
            getCoreSDK(osName, options)
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_SCENES) {
            String assets_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_core_autotests_assets" : "/mnt/c/TestResources/rpr_core_autotests_assets"
            downloadFiles("/volume1/Assets/rpr_core_autotests/", assets_dir)
        }

        String REF_PATH_PROFILE="/volume1/Baselines/rpr_core_autotests/${asicName}-${osName}"
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
                String baseline_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_core_autotests_baselines" : "/mnt/c/TestResources/rpr_core_autotests_baselines"
                println "[INFO] Downloading reference images for ${options.tests}"
                options.tests.split(" ").each() {
                    downloadFiles("${REF_PATH_PROFILE}/${it}", baseline_dir)
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
                dir('Work') {
                    if (fileExists("Results/Core/session_report.json")) {

                        def sessionReport = null
                        sessionReport = readJSON file: 'Results/Core/session_report.json'

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

                        println "Stashing test results to : ${options.testResultsName}"
                        stash includes: '**/*', excludes: '**/cache/**', name: "${options.testResultsName}", allowEmpty: true

                        // reallocate node if there are still attempts
                        // if test group is fully errored or number of test cases is equal to zero
                        if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped || sessionReport.summary.total == 0) {
                            // check that group isn't fully skipped
                            if (sessionReport.summary.total != sessionReport.summary.skipped || sessionReport.summary.total == 0){
                                // remove brocken core package
                                removeInstaller(osName: osName, options: options, extension: "zip")
                                collectCrashInfo(osName, options, options.currentTry)
                                if (osName.contains("Ubuntu")) {
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


def executeBuildWindows(Map options) {
    withNotifications(title: "Windows", options: options, logUrl: "${BUILD_URL}/artifact/Build-Windows.log",
        artifactUrl: "${BUILD_URL}/artifact/binWin64.zip", configuration: NotificationConfiguration.BUILD_PACKAGE) {
        dir("RadeonProRenderSDK/RadeonProRender/binWin64") {
            zip archive: true, dir: ".", glob: "", zipFile: "binWin64.zip"
            stash includes: "binWin64.zip", name: 'WindowsSDK'
            options.pluginWinSha = sha1 "binWin64.zip"
        }
        if (options.sendToUMS) {
            options.universeManager.sendToMINIO(options, "Windows", "..\\RadeonProRenderSDK\\RadeonProRender\\binWin64", "binWin64.zip", false)
        }
    }
}

def executeBuildOSX(Map options) {
    withNotifications(title: "OSX", options: options, logUrl: "${BUILD_URL}/artifact/Build-OSX.log",
        artifactUrl: "${BUILD_URL}/artifact/binMacOS.zip", configuration: NotificationConfiguration.BUILD_PACKAGE) {
        dir("RadeonProRenderSDK/RadeonProRender/binMacOS") {
            zip archive: true, dir: ".", glob: "", zipFile: "binMacOS.zip"
            stash includes: "binMacOS.zip", name: "OSXSDK"
            options.pluginOSXSha = sha1 "binMacOS.zip"
        }
        if (options.sendToUMS) {
            options.universeManager.sendToMINIO(options, "OSX", "../RadeonProRenderSDK/RadeonProRender/binWin64", "binMacOS.zip", false)
        }
    }
}

def executeBuildLinux(String osName, Map options) {
    withNotifications(title: "${osName}", options: options, logUrl: "${BUILD_URL}/artifact/Build-${osName}.log",
        artifactUrl: "${BUILD_URL}/artifact/bin${osName}.zip", configuration: NotificationConfiguration.BUILD_PACKAGE) {
        // no artifacts in repo for ubuntu20
        dir("RadeonProRenderSDK/RadeonProRender/binUbuntu18") {
            zip archive: true, dir: ".", glob: "", zipFile: "bin${osName}.zip"
            stash includes: "bin${osName}.zip", name: "${osName}SDK"
            options.pluginUbuntuSha = sha1 "bin${osName}.zip"
        }
        if (options.sendToUMS) {
            options.universeManager.sendToMINIO(options, "${osName}", "../RadeonProRenderSDK/RadeonProRender/binUbuntu18", "bin${osName}.zip", false)
        }
    }
}

def executeBuild(String osName, Map options)
{
    if (options.sendToUMS){
        options.universeManager.startBuildStage(osName)
    }

    try {
        dir('RadeonProRenderSDK') {
            withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
                checkOutBranchOrScm(options["projectBranch"], options["projectRepo"], false, options["prBranchName"], options["prRepoName"])
            }
        }

        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.BUILD_PACKAGE) {
            switch(osName) {
                case "Windows":
                    executeBuildWindows(options)
                    break
                case "OSX":
                    executeBuildOSX(options)
                    break
                default:
                    executeBuildLinux(osName, options)
            }
        }
    } catch (e) {
        throw e
    } finally {
        if (options.sendToUMS) {
            options.universeManager.sendToMINIO(options, osName, "..", "*.log")
            options.universeManager.finishBuildStage(osName)
        }
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}

def executePreBuild(Map options) {
    if (options['isPreBuilt']) {
        println "[INFO] Build was detected as prebuilt. Build stage will be skipped"
        currentBuild.description = "<b>Project branch:</b> Prebuilt plugin<br/>"
        options['executeBuild'] = false
        options['executeTests'] = true
    } else if (env.CHANGE_URL) {
        println "Branch was detected as Pull Request"
    } else if (env.BRANCH_NAME == "master") {
        println("[INFO] ${env.BRANCH_NAME} branch was detected")
        options.collectTrackedMetrics = true
    }

    if (!options['isPreBuilt']) {
        dir('RadeonProRenderSDK') {
            withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
                checkOutBranchOrScm(options["projectBranch"], options["projectRepo"])
            }

            options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
            options.commitMessage = bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim().replace('\n', '')
            options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

            println("The last commit was written by ${options.commitAuthor}.")
            println("Commit message: ${options.commitMessage}")
            println("Commit SHA: ${options.commitSHA}")

            if (options.projectBranch){
                currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
            } else {
                currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
            }

            currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
            currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
            currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

            if (env.BRANCH_NAME) {
                withNotifications(title: "Jenkins build configuration", printMessage: true, options: options, configuration: NotificationConfiguration.CREATE_GITHUB_NOTIFICATOR) {
                    GithubNotificator githubNotificator = new GithubNotificator(this, options)
                    githubNotificator.init(options)
                    options["githubNotificator"] = githubNotificator
                    githubNotificator.initPreBuild("${BUILD_URL}")
                }
            }
        }
    }


    def tests = []
    options.groupsUMS = []

    withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.CONFIGURE_TESTS) {
        dir('jobs_test_core') {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_core.git')
            dir ('jobs_launcher') {
                options['jobsLauncherBranch'] = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            }
            options['testsBranch'] = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            println("[INFO] Test branch hash: ${options['testsBranch']}")

            if (options.testsPackage != "none") {
                // json means custom test suite. Split doesn't supported
                def tempTests = readJSON file: "jobs/${options.testsPackage}"
                tempTests["groups"].each() {
                    // TODO: fix: duck tape - error with line ending
                    tests << it.key
                }
                options.tests = tests
                options.testsPackage = "none"
                options.groupsUMS = tests
            } else {
                options.tests.split(" ").each() {
                    tests << "${it}"
                }
                options.tests = tests
                options.groupsUMS = tests
            }

            options.testsList = ['']
            options.tests = tests.join(" ")

            if (options.sendToUMS) {
                options.universeManager.createBuilds(options)   
            }
        }

        if (env.BRANCH_NAME && options.githubNotificator) {
            options.githubNotificator.initChecks(options, "${BUILD_URL}")
        }
    }
}


def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        if (options['executeTests'] && testResultList) {

            withNotifications(title: "Building test report", options: options, startUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
                checkOutBranchOrScm(options["testsBranch"], "git@github.com:luxteam/jobs_test_core.git")
            }

            List lostStashes = []

            dir("summaryTestResults") {
                unstashCrashInfo(options['nodeRetry'])
                testResultList.each() {
                    dir("$it".replace("testResult-", "")) {
                        try {
                            unstash "$it"
                        } catch(e) {
                            echo "Can't unstash ${it}"
                            lostStashes.add("'$it'".replace("testResult-", ""))
                            println(e.toString())
                            println(e.getMessage())
                        }

                    }
                }
            }

            try {
                withCredentials([string(credentialsId: 'buildsRemoteHost', variable: 'REMOTE_HOST')]) {
                    dir("core_tests_configuration") {
                        bat(returnStatus: false, script: "%CIS_TOOLS%\\receiveFilesCoreConf.bat ${options.PRJ_ROOT}/${options.PRJ_NAME}/CoreAssets/ . ${REMOTE_HOST}")
                    }
                }
            } catch (e) {
                println("[ERROR] Can't download json files with core tests configuration")
            }

            try {
                dir("jobs_launcher") {
                    bat """
                        count_lost_tests.bat \"${lostStashes}\" .. ..\\summaryTestResults \"${options.splitTestsExecution}\" \"${options.testsPackage}\" \"${options.tests.toString()}\" \"\" \"{}\"
                    """
                }
            } catch (e) {
                println("[ERROR] Can't generate number of lost tests")
            }

            try {
                GithubNotificator.updateStatus("Deploy", "Building test report", "in_progress", options, NotificationConfiguration.BUILDING_REPORT, "${BUILD_URL}")
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
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}", "BUILD_NAME=${options.baseBuildName}"]) {
                    dir("jobs_launcher") {
                        if (options.projectBranch != "") {
                            options.branchName = options.projectBranch
                        } else {
                            options.branchName = env.BRANCH_NAME
                        }
                        if (options.incrementVersion) {
                            options.branchName = "master"
                        }

                        def retryInfo = JsonOutput.toJson(options.nodeRetry)
                        dir("..\\summaryTestResults") {
                            JSON jsonResponse = JSONSerializer.toJSON(retryInfo, new JsonConfig())
                            writeJSON file: 'retry_info.json', json: jsonResponse, pretty: 4
                        }
                        if (options.sendToUMS) {
                            options.universeManager.sendStubs(options, "..\\summaryTestResults\\lost_tests.json", "..\\summaryTestResults\\skipped_tests.json", "..\\summaryTestResults\\retry_info.json")
                        }

                        if (options['isPreBuilt']) {
                            bat """
                                build_reports.bat ..\\summaryTestResults Core "PreBuilt" "PreBuilt" "PreBuilt" \"\" \"${buildNumber}\"
                            """
                        } else {
                            bat """
                                build_reports.bat ..\\summaryTestResults Core ${options.commitSHA} ${options.branchName} \"${utils.escapeCharsByUnicode(options.commitMessage)}\" \"\" \"${buildNumber}\"
                            """
                        }

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
    } catch (e) {
        println(e.toString())
        println(e.getMessage())
        throw e
    } finally {}
}

def appendPlatform(String filteredPlatforms, String platform) {
    if (filteredPlatforms) {
        filteredPlatforms +=  ";" + platform
    } else {
        filteredPlatforms += platform
    }
    return filteredPlatforms
}


def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,AMD_RadeonVII,AMD_RX5700XT,NVIDIA_GF1080TI,NVIDIA_RTX2080,AMD_RX6800;OSX:AMD_RXVEGA;Ubuntu18:AMD_RadeonVII,NVIDIA_RTX2070;Ubuntu20:AMD_RadeonVII',
         String updateRefs = 'No',
         Boolean enableNotifications = true,
         String renderDevice = "gpu",
         String testsPackage = "Full.json",
         String tests = "",
         String width = "0",
         String height = "0",
         String iterations = "0",
         Boolean sendToUMS = true,
         String customBuildLinkWindows = "",
         String customBuildLinkUbuntu18 = "",
         String customBuildLinkUbuntu20 = "",
         String customBuildLinkOSX = "",
         String tester_tag = 'Core',
         String mergeablePR = "",
         String parallelExecutionTypeString = "TakeOneNodePerGPU",
         Boolean collectTrackedMetrics = false)
{
    ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)
    Map options = [:]
    options["stage"] = "Init"
    options["problemMessageManager"] = problemMessageManager
    options["projectRepo"] = "git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderSDK.git"
    
    def nodeRetry = []
    Map errorsInSuccession = [:]

    try {
        withNotifications(options: options, configuration: NotificationConfiguration.INITIALIZATION) {

            sendToUMS = updateRefs.contains('Update') || sendToUMS

            Boolean isPreBuilt = customBuildLinkWindows || customBuildLinkOSX || customBuildLinkUbuntu18 || customBuildLinkUbuntu20

            if (isPreBuilt) {
                //remove platforms for which pre built plugin is not specified
                String filteredPlatforms = ""

                platforms.split(';').each() { platform ->
                    List tokens = platform.tokenize(':')
                    String platformName = tokens.get(0)

                    switch(platformName) {
                        case 'Windows':
                            if (customBuildLinkWindows) {
                                filteredPlatforms = appendPlatform(filteredPlatforms, platform)
                            }
                            break
                        case 'OSX':
                            if (customBuildLinkOSX) {
                                filteredPlatforms = appendPlatform(filteredPlatforms, platform)
                            }
                            break
                        case 'Ubuntu18':
                            if (customBuildLinkUbuntu18) {
                                filteredPlatforms = appendPlatform(filteredPlatforms, platform)
                            }
                        // Ubuntu20
                        default:
                            if (customBuildLinkUbuntu20) {
                                filteredPlatforms = appendPlatform(filteredPlatforms, platform)
                            }
                        }
                }

                platforms = filteredPlatforms
            }

            gpusCount = 0
            platforms.split(';').each() { platform ->
                List tokens = platform.tokenize(':')
                if (tokens.size() > 1) {
                    gpuNames = tokens.get(1)
                    gpuNames.split(',').each() {
                        gpusCount += 1
                    }
                }
            }

            def universePlatforms = convertPlatforms(platforms)
            def parallelExecutionType = TestsExecutionType.valueOf(parallelExecutionTypeString)

            println "Platforms: ${platforms}"
            println "Tests: ${tests}"
            println "Tests package: ${testsPackage}"
            println "Tests execution type: ${parallelExecutionType}"
            println "Send to UMS: ${sendToUMS} "
            println "UMS platforms: ${universePlatforms}"

            String prRepoName = ""
            String prBranchName = ""
            if (mergeablePR) {
                String[] prInfo = mergeablePR.split(";")
                prRepoName = prInfo[0]
                prBranchName = prInfo[1]
            }

            options << [projectBranch:projectBranch,
                        testsBranch:testsBranch,
                        updateRefs:updateRefs,
                        enableNotifications:enableNotifications,
                        PRJ_NAME:"RadeonProRenderCore",
                        PRJ_ROOT:"rpr-core",
                        TESTER_TAG:tester_tag,
                        slackChannel:"${SLACK_CORE_CHANNEL}",
                        renderDevice:renderDevice,
                        testsPackage:testsPackage,
                        tests:tests.replace(',', ' '),
                        executeBuild:true,
                        executeTests:true,
                        reportName:'Test_20Report',
                        TEST_TIMEOUT:180,
                        DEPLOY_TIMEOUT:30,
                        width:width,
                        gpusCount:gpusCount,
                        height:height,
                        iterations:iterations,
                        sendToUMS:sendToUMS,
                        universePlatforms: universePlatforms,
                        nodeRetry: nodeRetry,
                        errorsInSuccession: errorsInSuccession,
                        problemMessageManager: problemMessageManager,
                        platforms:platforms,
                        prRepoName:prRepoName,
                        prBranchName:prBranchName,
                        parallelExecutionType:parallelExecutionType,
                        parallelExecutionTypeString: parallelExecutionTypeString,
                        collectTrackedMetrics:collectTrackedMetrics,
                        isPreBuilt:isPreBuilt,
                        customBuildLinkWindows: customBuildLinkWindows,
                        customBuildLinkUbuntu18: customBuildLinkUbuntu18,
                        customBuildLinkUbuntu20: customBuildLinkUbuntu20,
                        customBuildLinkOSX: customBuildLinkOSX
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
        println(e.toString());
        println(e.getMessage());
        throw e
    } finally {
        String problemMessage = options.problemMessageManager.publishMessages()
        if (options.sendToUMS) {
            options.universeManager.closeBuild(problemMessage, options)
        }
    }
}
