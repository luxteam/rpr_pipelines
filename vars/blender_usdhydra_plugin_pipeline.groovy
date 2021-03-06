import groovy.transform.Field
import groovy.json.JsonOutput
import utils
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import TestsExecutionType
import java.util.concurrent.atomic.AtomicInteger


def getBlenderAddonInstaller(String osName, Map options) {
    switch(osName) {
        case 'Windows':

            if (options['isPreBuilt']) {

                println "[INFO] PluginWinSha: ${options['pluginWinSha']}"

                if (options['pluginWinSha']) {
                    if (fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options['pluginWinSha']}.zip")) {
                        println "[INFO] The plugin ${options['pluginWinSha']}.zip exists in the storage."
                    } else {
                        clearBinariesWin()

                        println "[INFO] The plugin does not exist in the storage. Downloading and copying..."
                        downloadPlugin(osName, "BlenderUSDHydraAddon", options)

                        bat """
                            IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                            move BlenderUSDHydraAddon*.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options['pluginWinSha']}.zip"
                        """
                    }
                } else {
                    clearBinariesWin()

                    println "[INFO] The plugin does not exist in the storage. PluginSha is unknown. Downloading and copying..."
                    downloadPlugin(osName, "BlenderUSDHydraAddon", options)

                    bat """
                        IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                        move BlenderUSDHydraAddon*.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip"
                    """
                }

            } else {
                if (fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.commitSHA}_${osName}.zip")) {
                    println "[INFO] The plugin ${options.commitSHA}_${osName}.zip exists in the storage."
                } else {
                    clearBinariesWin()

                    println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                    unstash "appWindows"

                    bat """
                        IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                        move BlenderUSDHydraAddon*.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.commitSHA}_${osName}.zip"
                    """
                }
            }

            break
    }
}

def executeGenTestRefCommand(String osName, Map options, Boolean delete) {
    dir('scripts') {
        switch(osName) {
            case 'Windows':
                bat """
                    make_results_baseline.bat ${delete}
                """
                break
            // OSX & Ubuntu18
            default:
                sh """
                    ./make_results_baseline.sh ${delete}
                """
        }
    }
}

def buildRenderCache(String osName, String toolVersion, String log_name, Integer currentTry) {
    try {
        dir("scripts") {
            switch(osName) {
                case 'Windows':
                    bat "build_cache.bat ${toolVersion} >> \"..\\${log_name}_${currentTry}.cb.log\"  2>&1"
                    break
                default:
                    sh "./build_cache.sh ${toolVersion} >> \"../${log_name}_${currentTry}.cb.log\" 2>&1"        
            }
        }
    } catch (e) {
        String cacheBuildingLog = readFile("${log_name}_${currentTry}.cb.log")
        if (cacheBuildingLog.contains("engine not found 'HdUSD'")) {
            throw new ExpectedExceptionWrapper(NotificationConfiguration.PLUGIN_NOT_FOUND, e)
        }
        throw e
    }
}

def executeTestCommand(String osName, String asicName, Map options) {
    def test_timeout = options.timeouts["${options.parsedTests}"]
    String testsNames = options.parsedTests
    String testsPackageName = options.testsPackage
    if (options.testsPackage != "none" && !options.isPackageSplitted) {
        if (options.parsedTests.contains(".json")) {
            // if tests package isn't splitted and it's execution of this package - replace test group for non-splitted package by empty string
            testsNames = ""
        } else {
            // if tests package isn't splitted and it isn't execution of this package - replace tests package by empty string
            testsPackageName = "none"
        }
    }

    println "Set timeout to ${test_timeout}"

    timeout(time: test_timeout, unit: 'MINUTES') { 
        switch(osName) {
        case 'Windows':
            dir('scripts') {
                bat """
                    run.bat \"${testsPackageName}\" \"${testsNames}\" ${options.resX} ${options.resY} ${options.engine} ${options.toolVersion} ${options.testCaseRetries} ${options.updateRefs} 1>> \"..\\${options.stageName}_${options.currentTry}.log\"  2>&1
                """
            }
            break
        // OSX & Ubuntu18
        default:
            dir("scripts") {
                sh """
                    ./run.sh \"${testsPackageName}\" \"${testsNames}\" ${options.resX} ${options.resY} ${options.engine} ${options.toolVersion} ${options.testCaseRetries} ${options.updateRefs} 1>> \"../${options.stageName}_${options.currentTry}.log\" 2>&1
                """
            }
        }
    }
}


def executeTests(String osName, String asicName, Map options) {
    options.parsedTests = options.tests.split("-")[0]
    options.engine = options.tests.split("-")[1]

    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {
        withNotifications(title: options["stageName"], options: options, logUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
            timeout(time: "5", unit: "MINUTES") {
                cleanWS(osName)
                checkOutBranchOrScm(options["testsBranch"], "git@github.com:luxteam/jobs_test_usdblender.git")
            }
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_SCENES) {
            String assets_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/usd_blender_autotests_assets" : "/mnt/c/TestResources/usd_blender_autotests_assets"
            downloadFiles("/volume1/Assets/usd_blender_autotests/", assets_dir)
        }

        try {
            Boolean newPluginInstalled = false
            withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.INSTALL_PLUGIN) {
                timeout(time: "12", unit: "MINUTES") {
                    getBlenderAddonInstaller(osName, options)
                    newPluginInstalled = installBlenderAddon(osName, 'hdusd', options.toolVersion, options)
                    println "[INFO] Install function on ${env.NODE_NAME} return ${newPluginInstalled}"
                }
            }
        
            withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.BUILD_CACHE) {
                if (newPluginInstalled) {                         
                    timeout(time: "12", unit: "MINUTES") {
                        buildRenderCache(osName, options.toolVersion, options.stageName, options.currentTry)
                        String cacheImgPath = "./Work/Results/BlenderUSDHydra/cache_building.jpg"
                        if (!fileExists(cacheImgPath)) {
                            throw new ExpectedExceptionWrapper(NotificationConfiguration.NO_OUTPUT_IMAGE, new Exception(NotificationConfiguration.NO_OUTPUT_IMAGE))
                        }
                    }
                }
            }  
        } catch(e) {
            println(e.toString())
            println("[ERROR] Failed to install plugin on ${env.NODE_NAME}")
            // deinstalling broken addon
            installBlenderAddon(osName, 'hdusd', options.toolVersion, options, false, true)
            // remove installer of broken addon
            removeInstaller(osName: osName, options: options, extension: "zip")
            throw e
        }

        String enginePostfix = ""
        String REF_PATH_PROFILE="/volume1/Baselines/usd_blender_autotests/${asicName}-${osName}"
        switch(options.engine) {
            case 'HdRprPlugin':
                enginePostfix = "RPR"
                break
            case 'HdStormRendererPlugin':
                enginePostfix = "GL"
                break
        }
        REF_PATH_PROFILE = enginePostfix ? "${REF_PATH_PROFILE}-${enginePostfix}" : REF_PATH_PROFILE

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, options.stageName, options.currentTry)

        if (options["updateRefs"].contains("Update")) {
            withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.EXECUTE_TESTS) {
                executeTestCommand(osName, asicName, options)
                executeGenTestRefCommand(osName, options, options["updateRefs"].contains("clean"))
                uploadFiles("./Work/GeneratedBaselines/", REF_PATH_PROFILE)
                // delete generated baselines when they're sent 
                switch(osName) {
                    case 'Windows':
                        bat "if exist Work\\GeneratedBaselines rmdir /Q /S Work\\GeneratedBaselines"
                        break
                    default:
                        sh "rm -rf ./Work/GeneratedBaselines"        
                }
            }
        } else {
            // TODO: receivebaseline for json suite
            withNotifications(title: options["stageName"], printMessage: true, options: options, configuration: NotificationConfiguration.COPY_BASELINES) {
                String baseline_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/usd_blender_autotests_baselines" : "/mnt/c/TestResources/usd_blender_autotests_baselines"
                baseline_dir = enginePostfix ? "${baseline_dir}-${enginePostfix}" : baseline_dir
                println "[INFO] Downloading reference images for ${options.parsedTests}"
                options.parsedTests.split(" ").each() {
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

        if (options["errorsInSuccession"]["${osName}-${asicName}-${options.engine}"] != -1) {
            // mark that one group was finished and counting of errored groups in succession must be stopped
            options["errorsInSuccession"]["${osName}-${asicName}-${options.engine}"] = new AtomicInteger(-1)
        }

    } catch (e) {
        String additionalDescription = ""
        if (options.currentTry + 1 < options.nodeReallocateTries) {
            stashResults = false
        } else {
            if (!options["errorsInSuccession"]["${osName}-${asicName}-${options.engine}"]) {
                options["errorsInSuccession"]["${osName}-${asicName}-${options.engine}"] = new AtomicInteger(0)
            }
            Integer errorsInSuccession = options["errorsInSuccession"]["${osName}-${asicName}-${options.engine}"]
            // if counting of errored groups in succession must isn't stopped
            if (errorsInSuccession >= 0) {
                errorsInSuccession = options["errorsInSuccession"]["${osName}-${asicName}-${options.engine}"].addAndGet(1)
            
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
            if (stashResults) {
                dir('Work') {
                    if (fileExists("Results/BlenderUSDHydra/session_report.json")) {

                        def sessionReport = null
                        sessionReport = readJSON file: 'Results/BlenderUSDHydra/session_report.json'

                        if (sessionReport.summary.error > 0) {
                            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, NotificationConfiguration.SOME_TESTS_ERRORED, "${BUILD_URL}")
                        } else if (sessionReport.summary.failed > 0) {
                            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, NotificationConfiguration.SOME_TESTS_FAILED, "${BUILD_URL}")
                        } else {
                            GithubNotificator.updateStatus("Test", options['stageName'], "success", options, NotificationConfiguration.ALL_TESTS_PASSED, "${BUILD_URL}")
                        }

                        println "Stashing test results to : ${options.testResultsName}"
                        stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

                        // deinstalling broken addon
                        // if test group is fully errored or number of test cases is equal to zero
                        if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped || sessionReport.summary.total == 0) {
                            // check that group isn't fully skipped
                            if (sessionReport.summary.total != sessionReport.summary.skipped || sessionReport.summary.total == 0) {
                                collectCrashInfo(osName, options, options.currentTry)
                                installBlenderAddon(osName, 'hdusd', options.toolVersion, options, false, true)
                                // remove installer of broken addon
                                removeInstaller(osName: osName, options: options, extension: "zip")
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
            } else {
                println "[INFO] Task ${options.tests} on ${options.nodeLabels} labels will be retried."
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
    dir('RadeonProRenderBlenderAddon') {
        GithubNotificator.updateStatus("Build", "Windows", "in_progress", options, NotificationConfiguration.BUILD_SOURCE_CODE_START_MESSAGE, "${BUILD_URL}/artifact/Build-Windows.log")
        
        withEnv(["PATH=c:\\python37\\;c:\\python37\\scripts\\;${PATH}"]) {
            bat """
                set HDUSD_LIBS_DIR=..\\libs
                python tools\\create_zip_addon.py >> ..\\${STAGE_NAME}.log  2>&1
            """
        }

        dir("build") {
            bat """
                rename hdusd*.zip BlenderUSDHydraAddon_${options.pluginVersion}_Windows.zip
            """

            if (options.branch_postfix) {
                bat """
                    rename BlenderUSDHydraAddon*zip *.(${options.branch_postfix}).zip
                """
            }

            archiveArtifacts "BlenderUSDHydraAddon*.zip"
            String BUILD_NAME = options.branch_postfix ? "BlenderUSDHydraAddon_${options.pluginVersion}_Windows.(${options.branch_postfix}).zip" : "BlenderUSDHydraAddon_${options.pluginVersion}_Windows.zip"
            String pluginUrl = "${BUILD_URL}/artifact/${BUILD_NAME}"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${pluginUrl}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""

            bat """
                rename BlenderUSDHydraAddon*.zip BlenderUSDHydraAddon_Windows.zip
            """

            stash includes: "BlenderUSDHydraAddon_Windows.zip", name: "appWindows"

            GithubNotificator.updateStatus("Build", "Windows", "success", options, NotificationConfiguration.BUILD_SOURCE_CODE_END_MESSAGE, pluginUrl)
        }
    }
}


def executeBuildOSX(Map options) {
}


def executeBuildLinux(String osName, Map options) {
    dir('RadeonProRenderBlenderAddon') {
        GithubNotificator.updateStatus("Build", "${osName}", "in_progress", options, NotificationConfiguration.BUILD_SOURCE_CODE_START_MESSAGE, "${BUILD_URL}/artifact/Build-${osName}.log")
        
        sh """
            export HDUSD_LIBS_DIR=../libs
            python3.7 tools/create_zip_addon.py >> ../${STAGE_NAME}.log  2>&1
        """

        dir("build") {
            sh """
                mv hdusd*.zip BlenderUSDHydraAddon_${options.pluginVersion}_${osName}.zip
            """

            if (options.branch_postfix) {
                sh """
                    for i in BlenderUSDHydraAddon*; do name="\${i%.*}"; mv "\$i" "\${name}.(${options.branch_postfix})\${i#\$name}"; done
                """
            }

            archiveArtifacts "BlenderUSDHydraAddon*.zip"
            String BUILD_NAME = options.branch_postfix ? "BlenderUSDHydraAddon_${options.pluginVersion}_${osName}.(${options.branch_postfix}).zip" : "BlenderUSDHydraAddon_${options.pluginVersion}_${osName}.zip"
            String pluginUrl = "${BUILD_URL}/artifact/${BUILD_NAME}"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${pluginUrl}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""

            if (options.sendToUMS) {
                dir("../../../jobs_launcher") {
                    sendToMINIO(options, "${osName}", "../RadeonProRenderBlenderAddon/BlenderPkg/build", BUILD_NAME)                            
                }
            }

            sh """
                mv BlenderUSDHydraAddon*.zip BlenderUSDHydraAddon_${osName}.zip
            """

            stash includes: "BlenderUSDHydraAddon_${osName}.zip", name: "app${osName}"

            GithubNotificator.updateStatus("Build", "${osName}", "success", options, NotificationConfiguration.BUILD_SOURCE_CODE_END_MESSAGE, pluginUrl)
        }
    }
}


def executeBuild(String osName, Map options) {
    try {
        downloadFiles("/volume1/CIS/${options.PRJ_ROOT}/${options.PRJ_NAME}/3rdparty/${osName}/libs/*", "libs")

        dir("RadeonProRenderBlenderAddon") {
            withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
                checkOutBranchOrScm(options["projectBranch"], options["projectRepo"], false, options["prBranchName"], options["prRepoName"])
            }
        }

        outputEnvironmentInfo(osName)

        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
            switch(osName) {
                case "Windows":
                    executeBuildWindows(options)
                    break
                case "OSX":
                    println("Unsupported OS")
                    break
                default:
                    executeBuildLinux(osName, options)
                    
            }
        }
    } catch (e) {
        throw e
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}


def executePreBuild(Map options)
{
    // manual job with prebuilt plugin
    if (options.isPreBuilt) {
        println "[INFO] Build was detected as prebuilt. Build stage will be skipped"
        currentBuild.description = "<b>Project branch:</b> Prebuilt plugin<br/>"
        options.executeBuild = false
        options.executeTests = true
    // manual job
    } else if (options.forceBuild) {
        println "[INFO] Manual job launch detected"
        options['executeBuild'] = true
        options['executeTests'] = true
    // auto job
    } else {
        if (env.CHANGE_URL) {
            println "[INFO] Branch was detected as Pull Request"
            options.executeBuild = true
            options.executeTests = true
            options.testsPackage = "regression.json"
        } else if (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop") {
           println "[INFO] ${env.BRANCH_NAME} branch was detected"
           options['executeBuild'] = true
           options['executeTests'] = true
           options['testsPackage'] = "regression.json"
        } else {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
            options['testsPackage'] = "regression.json"
        }
    }

    // branch postfix
    options["branch_postfix"] = ""
    if (env.BRANCH_NAME && env.BRANCH_NAME != "master") {
        options["branch_postfix"] = env.BRANCH_NAME.replace('/', '-')
    } else if (options.projectBranch && options.projectBranch != "master" && options.projectBranch != "develop") {
        options["branch_postfix"] = options.projectBranch.replace('/', '-')
    }

    if (!options['isPreBuilt']) {
        dir('RadeonProRenderBlenderAddon') {
            withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
                checkOutBranchOrScm(options["projectBranch"], options["projectRepo"], true)
            }

            options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
            options.commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true).split('\r\n')[2].trim()
            options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            options.commitShortSHA = options.commitSHA[0..6]

            println(bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim())
            println "The last commit was written by ${options.commitAuthor}."
            println "Commit message: ${options.commitMessage}"
            println "Commit SHA: ${options.commitSHA}"
            println "Commit shortSHA: ${options.commitShortSHA}"

            if (options.projectBranch) {
                currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
            } else {
                currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
            }

            withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.INCREMENT_VERSION) {
                options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\hdusd\\__init__.py", '"version": (', ', ').replace(', ', '.')

                if (options['incrementVersion']) {
                    withNotifications(title: "Jenkins build configuration", printMessage: true, options: options, configuration: NotificationConfiguration.CREATE_GITHUB_NOTIFICATOR) {
                        GithubNotificator githubNotificator = new GithubNotificator(this, options)
                        githubNotificator.init(options)
                        options["githubNotificator"] = githubNotificator
                        githubNotificator.initPreBuild("${BUILD_URL}")
                    }
                    
                    if (env.BRANCH_NAME == "develop" && options.commitAuthor != "radeonprorender") {

                        options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\hdusd\\__init__.py", '"version": (', ', ')
                        println "[INFO] Incrementing version of change made by ${options.commitAuthor}."
                        println "[INFO] Current build version: ${options.pluginVersion}"

                        def new_version = version_inc(options.pluginVersion, 3, ', ')
                        println "[INFO] New build version: ${new_version}"
                        version_write("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\hdusd\\__init__.py", '"version": (', new_version, ', ')

                        options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\hdusd\\__init__.py", '"version": (', ', ', "true").replace(', ', '.')
                        println "[INFO] Updated build version: ${options.pluginVersion}"

                        bat """
                            git add src/hdusd/__init__.py
                            git commit -m "buildmaster: version update to ${options.pluginVersion}"
                            git push origin HEAD:develop
                        """

                        //get commit's sha which have to be build
                        options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
                        options.projectBranch = options.commitSHA
                        println "[INFO] Project branch hash: ${options.projectBranch}"
                    } else {
                        if (options.commitMessage.contains("CIS:BUILD")) {
                            options['executeBuild'] = true
                        }

                        if (options.commitMessage.contains("CIS:TESTS")) {
                            options['executeBuild'] = true
                            options['executeTests'] = true
                        }
                        // get a list of tests from commit message for auto builds
                        options.tests = utils.getTestsFromCommitMessage(options.commitMessage)
                        println "[INFO] Test groups mentioned in commit message: ${options.tests}"
                    }
                }
                currentBuild.description += "<b>Version:</b> ${options.pluginVersion}<br/>"
                currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
                currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
                currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"
            }
        }
    }

    if (env.BRANCH_NAME && options.githubNotificator) {
        options.githubNotificator.initChecks(options, "${BUILD_URL}")
    }

    def tests = []
    options.timeouts = [:]
    options.groupsUMS = []

    withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.CONFIGURE_TESTS) {
        dir('jobs_test_usdblender') {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_usdblender.git')

            options['testsBranch'] = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            dir('jobs_launcher') {
                options['jobsLauncherBranch'] = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            }
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

            if (options.testsPackage != "none") {
                def tempTests = []

                if (options.isPackageSplitted) {
                    println("[INFO] Tests package '${options.testsPackage}' can be splitted")
                } else {
                    // save tests which user wants to run with non-splitted tests package
                    if (options.tests) {
                        tempTests = options.tests.split(" ") as List
                    }
                    println("[INFO] Tests package '${options.testsPackage}' can't be splitted")
                }

                // modify name of tests package if tests package is non-splitted (it will be use for run package few time with different engines)
                String modifiedPackageName = "${options.testsPackage}~"
                options.groupsUMS = tempTests.clone()
                packageInfo["groups"].each() {
                    if (options.isPackageSplitted) {
                        tempTests << it.key
                        options.groupsUMS << it.key
                    } else {
                        if (tempTests.contains(it.key)) {
                            // add duplicated group name in name of package group name for exclude it
                            modifiedPackageName = "${modifiedPackageName},${it.key}"
                        } else {
                            options.groupsUMS << it.key
                        }
                    }
                }

                options.tests = utils.uniteSuites(this, "jobs/weights.json", tempTests)
                options.tests.each() {
                    options.engines.each { engine ->
                        tests << "${it}-${engine}"
                    }
                    def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }

                modifiedPackageName = modifiedPackageName.replace('~,', '~')

                if (options.isPackageSplitted) {
                    options.testsPackage = "none"
                } else {
                    options.testsPackage = modifiedPackageName
                    options.engines.each { engine ->
                        tests << "${modifiedPackageName}-${engine}"
                    }
                    options.timeouts[options.testsPackage] = options.NON_SPLITTED_PACKAGE_TIMEOUT + options.ADDITIONAL_XML_TIMEOUT
                }
            } else if (options.tests) {
                options.groupsUMS = options.tests.split(" ") as List
                options.tests = utils.uniteSuites(this, "jobs/weights.json", options.tests.split(" ") as List)
                options.tests.each() {
                    options.engines.each { engine ->
                        tests << "${it}-${engine}"
                    }
                    def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }
            } else {
                options.executeTests = false
            }
            options.tests = tests
        }

        if (env.CHANGE_URL) {
            options.githubNotificator.initPR(options, "${BUILD_URL}")
        }

        options.testsList = options.tests

        println "timeouts: ${options.timeouts}"
    }
}


def executeDeploy(Map options, List platformList, List testResultList) {
    cleanWS()
    try {
        if (options['executeTests'] && testResultList) {
            withNotifications(title: "Building test report", options: options, startUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_usdblender.git')
            }

            Map lostStashes = [:]
            options.engines.each { engine ->
                lostStashes[engine] = []
            }

            dir("summaryTestResults") {
                testResultList.each() {
                    String engine
                    String testName
                    options.engines.each { currentEngine ->
                        dir(currentEngine) {
                            unstashCrashInfo(options['nodeRetry'], currentEngine)
                        }
                    }
                    List testNameParts = it.split("-") as List
                    engine = testNameParts[-1]
                    testName = testNameParts.subList(0, testNameParts.size() - 1).join("-")
                    dir(engine) {
                        dir(testName.replace("testResult-", "")) {
                            try {
                                unstash "$it"
                            } catch(e) {
                                println("[ERROR] Failed to unstash ${it}")
                                lostStashes[engine].add("'${testName}'".replace("testResult-", ""))
                                println(e.toString())
                                println(e.getMessage())
                            }

                        }
                    }
                }
            }

            try {
                dir("jobs_launcher") {
                    options.engines.each {
                        bat """
                            count_lost_tests.bat \"${lostStashes[it]}\" .. ..\\summaryTestResults\\${it} \"${options.splitTestsExecution}\" \"${options.testsPackage}\" \"[]\" \"${it}\" \"{}\"
                        """
                    }
                }
            } catch (e) {
                println("[ERROR] Can't generate number of lost tests")
            }

            String branchName = env.BRANCH_NAME ?: options.projectBranch
            List reports = []
            List reportsNames = []

            try {
                GithubNotificator.updateStatus("Deploy", "Building test report", "in_progress", options, NotificationConfiguration.BUILDING_REPORT, "${BUILD_URL}")
                options.engines.each { engine ->
                    reports.add("${engine}/summary_report.html")
                }
                options.enginesNames.each { engine ->
                    reportsNames.add("Summary Report (${engine})")
                }
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}", "BUILD_NAME=${options.baseBuildName}"]) {
                    dir("jobs_launcher") {
                        for (int i = 0; i < options.engines.size(); i++) {
                            String engine = options.engines[i]
                            String engineName = options.enginesNames[i]
                            List retryInfoList = utils.deepcopyCollection(this, options.nodeRetry)
                            retryInfoList.each{ gpu ->
                                gpu['Tries'].each{ group ->
                                    group.each{ groupKey, retries ->
                                        if (groupKey.endsWith(engine)) {
                                            List testNameParts = groupKey.split("-") as List
                                            String parsedName = testNameParts.subList(0, testNameParts.size() - 1).join("-")
                                            group[parsedName] = retries
                                        }
                                        group.remove(groupKey)
                                    }
                                }
                                gpu['Tries'] = gpu['Tries'].findAll{ it.size() != 0 }
                            }
                            def retryInfo = JsonOutput.toJson(retryInfoList)
                            dir("..\\summaryTestResults\\${engine}") {
                                JSON jsonResponse = JSONSerializer.toJSON(retryInfo, new JsonConfig())
                                writeJSON file: 'retry_info.json', json: jsonResponse, pretty: 4
                            }
                            try {
                                if (options['isPreBuilt']) {
                                    bat """
                                        build_reports.bat ..\\summaryTestResults\\${engine} ${utils.escapeCharsByUnicode("Blender ")}${options.toolVersion} "PreBuilt" "PreBuilt" "PreBuilt" \"${utils.escapeCharsByUnicode(engineName)}\"
                                    """
                                } else {
                                    bat """
                                        build_reports.bat ..\\summaryTestResults\\${engine} ${utils.escapeCharsByUnicode("Blender ")}${options.toolVersion} ${options.commitSHA} ${branchName} \"${utils.escapeCharsByUnicode(options.commitMessage)}\" \"${utils.escapeCharsByUnicode(engineName)}\"
                                    """
                                }
                            } catch (e) {
                                String errorMessage = utils.getReportFailReason(e.getMessage())
                                GithubNotificator.updateStatus("Deploy", "Building test report", "failure", options, errorMessage, "${BUILD_URL}")
                                if (utils.isReportFailCritical(e.getMessage())) {
                                    throw e
                                } else {
                                    currentBuild.result = "FAILURE"
                                    options.problemMessageManager.saveGlobalFailReason(errorMessage)
                                }
                            }
                        }
                    }
                }
            } catch(e) {
                String errorMessage = utils.getReportFailReason(e.getMessage())
                options.problemMessageManager.saveSpecificFailReason(errorMessage, "Deploy")
                GithubNotificator.updateStatus("Deploy", "Building test report", "failure", options, errorMessage, "${BUILD_URL}")
                println("[ERROR] Failed to build test report.")
                println(e.toString())
                println(e.getMessage())
                if (!options.testDataSaved) {
                    try {
                        // Save test data for access it manually anyway
                        utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", reports.join(", "), "Test Report", reportsNames.join(", "))
                        options.testDataSaved = true 
                    } catch(e1) {
                        println("[WARNING] Failed to publish test data.")
                        println(e.toString())
                        println(e.getMessage())
                    }
                }
                throw e
            }

            try {
                dir("jobs_launcher") {
                    bat "get_status.bat ..\\summaryTestResults True"
                }
            } catch(e) {
                println("[ERROR] Failed to generate slack status.")
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
                }
                else if (summaryReport.failed > 0) {
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
                utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", reports.join(", "), "Test Report", reportsNames.join(", "))

                if (summaryTestResults) {
                    // add in description of status check information about tests statuses
                    // Example: Report was published successfully (passed: 69, failed: 11, error: 0)
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", options, "${NotificationConfiguration.REPORT_PUBLISHED} Results: passed - ${summaryTestResults.passed}, failed - ${summaryTestResults.failed}, error - ${summaryTestResults.error}.", "${BUILD_URL}/Test_20Report")
                } else {
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", options, NotificationConfiguration.REPORT_PUBLISHED, "${BUILD_URL}/Test_20Report")
                }
            }

            println "BUILD RESULT: ${currentBuild.result}"
            println "BUILD CURRENT RESULT: ${currentBuild.currentResult}"
        }
    } catch(e) {
        println(e.toString())
        println(e.getMessage())
        throw e
    }
}


def appendPlatform(String filteredPlatforms, String platform) {
    if (filteredPlatforms) {
        filteredPlatforms +=  ";" + platform
    } else {
        filteredPlatforms += platform
    }
    return filteredPlatforms
}


def call(String projectRepo = "git@github.com:GPUOpen-LibrariesAndSDKs/BlenderUSDHydraAddon.git",
    String projectBranch = "",
    String testsBranch = "master",
    String platforms = 'Windows;Ubuntu18',
    String updateRefs = 'No',
    Boolean enableNotifications = true,
    Boolean incrementVersion = true,
    String testsPackage = "",
    String tests = "",
    Boolean forceBuild = false,
    Boolean splitTestsExecution = true,
    String resX = '0',
    String resY = '0',
    String customBuildLinkWindows = "",
    String customBuildLinkLinux = "",
    String customBuildLinkOSX = "",
    String enginesNames = "RRP,GL",
    String tester_tag = "Blender2.8",
    String toolVersion = "2.91",
    String mergeablePR = "",
    String parallelExecutionTypeString = "TakeAllNodes",
    Integer testCaseRetries = 3
    )
{
    ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)
    Map options = [:]
    options["stage"] = "Init"
    options["problemMessageManager"] = problemMessageManager

    resX = (resX == 'Default') ? '0' : resX
    resY = (resY == 'Default') ? '0' : resY
    def nodeRetry = []
    Map errorsInSuccession = [:]

    try {
        withNotifications(options: options, configuration: NotificationConfiguration.INITIALIZATION) {
            withNotifications(options: options, configuration: NotificationConfiguration.DELEGATES_PARAM) {
                if (!enginesNames) {
                    throw new Exception()
                }
            }

            enginesNames = enginesNames.split(",") as List
            def formattedEngines = []
            enginesNames.each {
                formattedEngines.add((it == "RPR") ? "HdRprPlugin" : "HdStormRendererPlugin")
            }

            Boolean isPreBuilt = customBuildLinkWindows || customBuildLinkOSX || customBuildLinkLinux

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
                        default:
                            if (customBuildLinkLinux) {
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

            def parallelExecutionType = TestsExecutionType.valueOf(parallelExecutionTypeString)

            println "Platforms: ${platforms}"
            println "Tests: ${tests}"
            println "Tests package: ${testsPackage}"
            println "Split tests execution: ${splitTestsExecution}"
            println "Tests execution type: ${parallelExecutionType}"

            String prRepoName = ""
            String prBranchName = ""
            if (mergeablePR) {
                String[] prInfo = mergeablePR.split(";")
                prRepoName = prInfo[0]
                prBranchName = prInfo[1]
            }

            Integer deployTimeout = 150 * enginesNames.size()
            println "Calculated deploy timeout: ${deployTimeout}"

            options << [projectRepo:projectRepo,
                        projectBranch:projectBranch,
                        testsBranch:testsBranch,
                        updateRefs:updateRefs,
                        enableNotifications:enableNotifications,
                        PRJ_NAME:"BlenderUSDHydraPlugin",
                        PRJ_ROOT:"rpr-plugins",
                        incrementVersion:incrementVersion,
                        testsPackage:testsPackage,
                        tests:tests,
                        toolVersion:toolVersion,
                        isPreBuilt:isPreBuilt,
                        forceBuild:forceBuild,
                        reportName:'Test_20Report',
                        splitTestsExecution:splitTestsExecution,
                        gpusCount:gpusCount,
                        TEST_TIMEOUT:180,
                        ADDITIONAL_XML_TIMEOUT:30,
                        NON_SPLITTED_PACKAGE_TIMEOUT:60,
                        DEPLOY_TIMEOUT:deployTimeout,
                        TESTER_TAG:tester_tag,
                        resX: resX,
                        resY: resY,
                        customBuildLinkWindows: customBuildLinkWindows,
                        customBuildLinkLinux: customBuildLinkLinux,
                        customBuildLinkOSX: customBuildLinkOSX,
                        engines: formattedEngines,
                        enginesNames:enginesNames,
                        nodeRetry: nodeRetry,
                        errorsInSuccession: errorsInSuccession,
                        platforms:platforms,
                        prRepoName:prRepoName,
                        prBranchName:prBranchName,
                        parallelExecutionType:parallelExecutionType,
                        parallelExecutionTypeString: parallelExecutionTypeString,
                        testCaseRetries:testCaseRetries
                        ]
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy, options)
    } catch(e) {
        currentBuild.result = "FAILURE"
        println(e.toString())
        println(e.getMessage())
        throw e
    } finally {
        problemMessageManager.publishMessages()
    }

}
