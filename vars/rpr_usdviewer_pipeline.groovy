import groovy.transform.Field
import groovy.json.JsonOutput
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import TestsExecutionType
import universe.*


@Field final String PRODUCT_NAME = "AMD%20Radeon™%20ProRender%20for%20USDViewer"


def getViewerTool(String osName, Map options) {
    switch (osName) {
        case "Windows":
            if (!fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip")) {
                clearBinariesWin()
                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "appWindows"
                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    copy RadeonProUSDViewer_Windows.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip"
                """
            } else {
                println "[INFO] The plugin ${options.pluginWinSha}.zip exists in the storage."
                bat """
                    copy "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip" RadeonProUSDViewer_Windows.zip
                """
            }
            unzip zipFile: "RadeonProUSDViewer_Windows.zip", dir: "USDViewer", quiet: true
            break

        case "OSX":
            println "OSX isn't supported"
            break

        default:
            println "Linux isn't supported"
    }
}


def executeGenTestRefCommand(String osName, Map options, Boolean delete) {
    dir("script") {
        switch (osName) {
            case "Windows":
                bat """
                    make_results_baseline.bat ${delete}
                """
                break

            case "OSX":
                println "OSX isn't supported"
                break

            default:
                println "Linux isn't supported"
        }
    }
}


def executeTestCommand(String osName, String asicName, Map options) {
    def testTimeout = options.timeouts[options.tests]
    String testsNames = options.tests
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

    println "Set timeout to ${testTimeout}"
    timeout(time: testTimeout, unit: 'MINUTES') {
        UniverseManager.executeTests(osName, asicName, options) {
            switch (osName) {
                case "Windows":
                    dir('scripts') {
                        bat """
                            run.bat \"${testsPackageName}\" \"${testsNames}\" ${options.testCaseRetries} ${options.updateRefs} 1>> \"../${options.stageName}_${options.currentTry}.log\"  2>&1
                        """
                    }
                    break

                case "OSX":
                    println "OSX isn't supported"
                    break

                default:
                    println "Linux isn't supported"
            }
        }
    }
}


def executeTests(String osName, String asicName, Map options) {
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true
    if (options.sendToUMS) {
        options.universeManager.startTestsStage(osName, asicName, options)
    }

    try {
        withNotifications(title: options["stageName"], options: options, logUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
            timeout(time: "10", unit: "MINUTES") {
                cleanWS(osName)
                checkOutBranchOrScm(options["testsBranch"], "git@github.com:luxteam/jobs_test_usdviewer.git")
            }
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_PACKAGE) {
            timeout(time: "25", unit: "MINUTES") {
                getViewerTool(osName, options)
            }
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_SCENES) {
            String assetsDir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_usdviewer_autotests_assets" : "/mnt/c/TestResources/rpr_usdviewer_autotests_assets"
            downloadFiles("/volume1/Assets/rpr_usdviewer_autotests/", assetsDir)
        }

        String REF_PATH_PROFILE="/volume1/Baselines/rpr_usdviewer_autotests/${asicName}-${osName}"
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
                        bat """
                            if exist Work\\GeneratedBaselines rmdir /Q /S Work\\GeneratedBaselines
                        """
                        break

                    default:
                        sh """
                            rm -rf ./Work/GeneratedBaselines
                        """
                }
            }
        } else {
            withNotifications(title: options["stageName"], printMessage: true, options: options, configuration: NotificationConfiguration.COPY_BASELINES) {
                String baselineDir = isUnix() ? "${CIS_TOOLS}/../TestResources/usd_viewer_autotests_baselines" : "/mnt/c/TestResources/usd_viewer_autotests_baselines"
                println "[INFO] Downloading reference images for ${options.tests}"
                options.tests.split(" ").each { downloadFiles("${REF_PATH_PROFILE}/${it.contains(".json") ? "" : it}", baselineDir) }
            }
            withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.EXECUTE_TESTS) {
                executeTestCommand(osName, asicName, options)
            }
        }
        options.executeTestsFinished = true

    } catch (e) {
        if (options.currentTry < options.nodeReallocateTries) {
            stashResults = false
        } 
        println e.toString()
        if (e instanceof ExpectedExceptionWrapper) {
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, "${e.getMessage()}", "${BUILD_URL}")
            throw new ExpectedExceptionWrapper("${e.getMessage()}", e.getCause())
        } else {
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, "${NotificationConfiguration.REASON_IS_NOT_IDENTIFIED}", "${BUILD_URL}")
            throw new ExpectedExceptionWrapper("${NotificationConfiguration.REASON_IS_NOT_IDENTIFIED}", e)
        }
    } finally {
        try {
            dir(options.stageName) {
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
                    if (fileExists("Results/USDViewer/session_report.json")) {

                        def sessionReport = readJSON file: 'Results/USDViewer/session_report.json'
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
                        stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true
                        // reallocate node if there are still attempts
                        if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped || sessionReport.summary.total == 0) {
                            if (sessionReport.summary.total != sessionReport.summary.skipped) {
                                // remove broken usdviewer
                                removeInstaller(osName: osName, options: options, extension: "zip")
                                collectCrashInfo(osName, options, options.currentTry)
                                if (osName == "Ubuntu18") {
                                    sh """
                                        echo "Restarting Unix Machine...."
                                        hostname
                                        (sleep 3; sudo shutdown -r now) &
                                    """
                                    sleep(60)
                                }
                                String errorMessage = (options.currentTry < options.nodeReallocateTries) ? "All tests were marked as error. The test group will be restarted." : "All tests were marked as error."
                                throw new ExpectedExceptionWrapper(errorMessage, new Exception(errorMessage))
                            }
                        }
                    }
                }
            }
        } catch(e) {
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
    withEnv(["PATH=c:\\python37\\;c:\\python37\\scripts\\;${PATH}", "WORKSPACE=${env.WORKSPACE.toString().replace('\\', '/')}"]) {
        outputEnvironmentInfo("Windows", "${STAGE_NAME}.EnvVariables")

        // vcvars64.bat sets VS/msbuild env
        withNotifications(title: "Windows", options: options, logUrl: "${BUILD_URL}/artifact/${STAGE_NAME}.HdRPRPlugin.log", configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
            bat """
                call "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\VC\\Auxiliary\\Build\\vcvars64.bat" >> ${STAGE_NAME}.EnvVariables.log 2>&1
                cd USDPixar
                git apply ../usd_dev.patch  >> ${STAGE_NAME}.USDPixar.log 2>&1
                cd ..

                python USDPixar/build_scripts/build_usd.py --build RPRViewer/binary/windows/build --src RPRViewer/binary/windows/deps RPRViewer/binary/windows/inst >> ${STAGE_NAME}.USDPixar.log 2>&1
                
                set PXR_DIR=%CD%\\USDPixar
                set INSTALL_PREFIX_DIR=%CD%\\RPRViewer\\binary\\windows\\inst
                cd HdRPRPlugin
                cmake -B build -G "Visual Studio 15 2017 Win64" -Dpxr_DIR=%PXR_DIR% -DCMAKE_INSTALL_PREFIX=%INSTALL_PREFIX_DIR% -DRPR_BUILD_AS_HOUDINI_PLUGIN=FALSE ^
                    -DPXR_USE_PYTHON_3=ON -DMATERIALX_BUILD_PYTHON=ON -DMATERIALX_INSTALL_PYTHON=ON >> ..\\${STAGE_NAME}.HdRPRPlugin.log 2>&1
                cmake --build build --config Release --target install >> ..\\${STAGE_NAME}.HdRPRPlugin.log 2>&1
            """
        }
        String buildName = "RadeonProUSDViewer_Windows.zip"
        withNotifications(title: "Windows", options: options, artifactUrl: "${BUILD_URL}/artifact/${buildName}", configuration: NotificationConfiguration.BUILD_PACKAGE) {
            // delete files before zipping
            bat """
                del RPRViewer\\binary\\windows\\inst\\pxrConfig.cmake
                rmdir /Q /S RPRViewer\\binary\\windows\\inst\\cmake
                rmdir /Q /S RPRViewer\\binary\\windows\\inst\\include
                rmdir /Q /S RPRViewer\\binary\\windows\\inst\\lib\\cmake
                rmdir /Q /S RPRViewer\\binary\\windows\\inst\\lib\\pkgconfig
                del RPRViewer\\binary\\windows\\inst\\bin\\*.lib
                del RPRViewer\\binary\\windows\\inst\\bin\\*.pdb
                del RPRViewer\\binary\\windows\\inst\\lib\\*.lib
                del RPRViewer\\binary\\windows\\inst\\lib\\*.pdb
                del RPRViewer\\binary\\windows\\inst\\plugin\\usd\\*.lib
            """

            // TODO: filter files for archive
            
            zip archive: false, dir: "RPRViewer\\binary\\windows\\inst", glob: '', zipFile: "RadeonProUSDViewer_Windows.zip"
            stash includes: "RadeonProUSDViewer_Windows.zip", name: "appWindows"
            options.pluginWinSha = sha1 "RadeonProUSDViewer_Windows.zip"

            withEnv(["PYTHONPATH=%INST%\\lib\\python;%INST%\\lib"]) {
                try {
                    bat """
                        set INSTALL_PREFIX_DIR=%CD%/RPRViewer/binary/windows/inst
                        set PACKAGE_PATH=%INSTALL_PREFIX_DIR%/dist
                        set PYTHONPATH=%INSTALL_PREFIX_DIR%/lib/python;%INSTALL_PREFIX_DIR%/lib;%INSTALL_PREFIX_DIR%/lib/python/pxr/Usdviewq/HdRPRPlugin/python

                        pyinstaller %CD%/RPRViewer/tools/usdview_windows.spec --noconfirm --clean --distpath %PACKAGE_PATH% ^
                            --workpath %INSTALL_PREFIX_DIR%/build >> ${STAGE_NAME}.USDViewerPackage.log 2>&1
                    """

                    dir("RPRViewer") {
                        bat """
                            "C:\\Program Files (x86)\\Inno Setup 6\\ISCC.exe" installer.iss >> ../${STAGE_NAME}.USDViewerInstaller.log 2>&1
                        """
                        archiveArtifacts artifacts: "RPRViewer_Setup.exe", allowEmptyArchive: false
                    
                        /* due to the weight of the artifact, its sending is postponed until the logic for removing old builds is added to UMS
                        if (options.sendToUMS) {
                            // WARNING! call sendToMinio in build stage only from parent directory
                            options.universeManager.sendToMINIO(options, "Windows", "..", "RPRViewer_Setup.exe", false)
                        }*/
                    }
                } catch (e) {
                    println(e.toString())
                    println(e.getMessage())
                    println(e.getStackTrace())
                    currentBuild.result = "FAILURE"
                    println "[ERROR] Failed to build USD Viewer installer"
                }
            }
        }
    }
}


def executeBuildOSX(Map options)
{
    
    outputEnvironmentInfo("OSX", "${STAGE_NAME}.EnvVariables")

    // vcvars64.bat sets VS/msbuild env
    withNotifications(title: "OSX", options: options, logUrl: "${BUILD_URL}/artifact/${STAGE_NAME}.HdRPRPlugin.log", configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
        sh """
            export OS=Darwin
            export PATH="~/Qt/5.15.2/clang_64/bin:\$PATH"
            
            echo \$PATH

            rm -rf RPRViewer/binary/mac/*

            export PYENV_ROOT="\$HOME/.pyenv"
            export PATH="\$PYENV_ROOT/shims:\$PYENV_ROOT/bin:\$PATH"
            pyenv rehash

            # USD
            python USDPixar/build_scripts/build_usd.py --build RPRViewer/binary/mac/build --src RPRViewer/binary/mac/deps RPRViewer/binary/mac/inst >> ${STAGE_NAME}.USDPixar.log 2>&1
            # HdRprPlugin
            export PXR_DIR=\$(pwd)/USDPixar
            export INSTALL_PREFIX_DIR=\$(pwd)/RPRViewer/binary/mac/inst
            
            cd HdRPRPlugin
            cmake -B build -Dpxr_DIR=\$PXR_DIR -DCMAKE_INSTALL_PREFIX=\$INSTALL_PREFIX_DIR -DRPR_BUILD_AS_HOUDINI_PLUGIN=FALSE \
                -DMATERIALX_PYTHON_PYBIND11_DIR=\$(pwd)/deps/MaterialX/source/PyMaterialX/PyBind11 \
                -DPXR_USE_PYTHON_3=ON -DMATERIALX_BUILD_PYTHON=ON -DMATERIALX_INSTALL_PYTHON=ON >> ../${STAGE_NAME}.HdRPRPlugin.log 2>&1
            cmake --build build --config Release --target install >> ../${STAGE_NAME}.HdRPRPlugin.log 2>&1
        """
    }

    // delete files before zipping
    withNotifications(title: "OSX", options: options, artifactUrl: "${BUILD_URL}/artifact/RadeonProUSDViewer_OSX.zip", configuration: NotificationConfiguration.BUILD_PACKAGE) {
        withEnv(["PYTHONPATH=%INST%/lib/python;%INST%/lib"]) {
            try {
                sh """
                    export PYENV_ROOT="\$HOME/.pyenv"
                    export PATH="\$PYENV_ROOT/shims:\$PYENV_ROOT/bin:\$PATH"
                    pyenv rehash
                    
                    python --version
                
                    export INSTALL_PREFIX_DIR=\$(pwd)/RPRViewer/binary/mac/inst
                    export MATERIALX_DIR=\$(python -c "import MaterialX as _; import os; print(os.path.dirname(_.__file__))")/
                    export PYTHONPATH=\$INSTALL_PREFIX_DIR/lib/python:\$INSTALL_PREFIX_DIR/lib:\$INSTALL_PREFIX_DIR/lib/python/pxr/Usdviewq/HdRPRPlugin/python
                    export PACKAGE_PATH=\$INSTALL_PREFIX_DIR/dist

                    python -m PyInstaller \$(pwd)/RPRViewer/tools/usdview_mac.spec --noconfirm --clean --distpath \
                        \$PACKAGE_PATH --workpath \$INSTALL_PREFIX_DIR/build >> ${STAGE_NAME}.USDViewerPackage.log 2>&1

                    install_name_tool -add_rpath @loader_path/../../.. \$PACKAGE_PATH/RPRViewer/hdrpr/plugin/usd/hdRpr.dylib
                    rm \$PACKAGE_PATH/RPRViewer/librprUsd.dylib
                    install_name_tool -change @loader_path/../../librprUsd.dylib @loader_path/../../hdrpr/lib/librprUsd.dylib \
                        \$PACKAGE_PATH/RPRViewer/rpr/RprUsd/_rprUsd.so >> ${STAGE_NAME}.USDViewerPackage.log 2>&1
                    install_name_tool -add_rpath @loader_path/../.. \$PACKAGE_PATH/RPRViewer/hdrpr/lib/librprUsd.dylib
                    cp \$PACKAGE_PATH/RPRViewer/hdrpr/lib/*.dylib \$PACKAGE_PATH/RPRViewer/
                    install_name_tool -change \$INSTALL_PREFIX_DIR/lib/libGLEW.2.0.0.dylib @rpath/libGLEW.2.0.0.dylib \
                        \$PACKAGE_PATH/RPRViewer/hdrpr/plugin/usd/hdRpr.dylib >> ${STAGE_NAME}.USDViewerPackage.log 2>&1
                    install_name_tool -change \$INSTALL_PREFIX_DIR/lib/libGLEW.2.0.0.dylib @rpath/libGLEW.2.0.0.dylib \
                        \$PACKAGE_PATH/RPRViewer/hdrpr/lib/librprUsd.dylib >> ${STAGE_NAME}.USDViewerPackage.log 2>&1
                """

                zip archive: true, dir: "RPRViewer/binary/mac/inst/dist/RPRViewer", glob: '', zipFile: "RadeonProUSDViewer_Package_OSX.zip"
            
                /* due to the weight of the artifact, its sending is postponed until the logic for removing old builds is added to UMS
                if (options.sendToUMS) {
                    // WARNING! call sendToMinio in build stage only from parent directory
                    options.universeManager.sendToMINIO(options, "OSX", "..", "RadeonProUSDViewer_Package_OSX.zip", false)
                }*/
            } catch (e) {
                println(e.toString())
                println(e.getMessage())
                println(e.getStackTrace())
                currentBuild.result = "FAILURE"
                println "[ERROR] Failed to build USD Viewer Package"
            }
        }
    }
    
}


def executeBuild(String osName, Map options) {
    if (options.sendToUMS) {
        options.universeManager.startBuildStage(osName)
    }
    try {
        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
            checkOutBranchOrScm(options["projectBranch"], options["projectRepo"])
        }
        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
            switch (osName) {
                case "Windows":
                    executeBuildWindows(options)
                    break
                case "OSX":
                    executeBuildOSX(options)
                    break
                default:
                    println "Linux isn't supported"
            }
        }
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        if (options.sendToUMS) {
            options.universeManager.sendToMINIO(options, osName, "..", "*.log")
            options.universeManager.finishBuildStage(osName)
        }
    }
}


def executePreBuild(Map options) {
    if (env.CHANGE_URL) {
        echo "[INFO] Branch was detected as Pull Request"
        options.testsPackage = "PR.json"
    } else if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.testsPackage = "master.json"
    } else if (env.BRANCH_NAME) {
        options.testsPackage = "smoke.json"
    }

    withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
        checkOutBranchOrScm(options["projectBranch"], options["projectRepo"], true)
    }
    options.commitAuthor = utils.getBatOutput(this, "git show -s --format=%%an HEAD ")
    options.commitMessage = utils.getBatOutput(this, "git log --format=%%s -n 1").replace('\n', '')
    options.commitSHA = utils.getBatOutput(this, "git log --format=%%H -1 ")
    println """
        The last commit was written by ${options.commitAuthor}.
        Commit message: ${options.commitMessage}
        Commit SHA: ${options.commitSHA}
    """

    currentBuild.description = "<b>Project branch:</b> ${options.projectBranch ?: env.BRANCH_NAME}<br/>"
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

    def tests = []
    options.timeouts = [:]
    options.groupsUMS = []

    withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.CONFIGURE_TESTS) {
        dir('jobs_test_usdviewer') {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_usdviewer.git')
            options['testsBranch'] = utils.getBatOutput(this, "git log --format=%%H -1 ")
            dir('jobs_launcher') {
                options['jobsLauncherBranch'] = utils.getBatOutput(this, "git log --format=%%H -1 ")
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
                if (options.isPackageSplitted) {
                    println "[INFO] Tests package '${options.testsPackage}' can be splitted"
                } else {
                    // save tests which user wants to run with non-splitted tests package
                    if (options.tests) {
                        tests = options.tests.split(" ") as List
                    }
                    println "[INFO] Tests package '${options.testsPackage}' can't be splitted"
                }
                // modify name of tests package if tests package is non-splitted (it will be use for run package few time with different engines)
                String modifiedPackageName = "${options.testsPackage}~"
                packageInfo["groups"].each {
                    if (options.isPackageSplitted) {
                        tests << it.key
                        options.groupsUMS << it.key
                    } else {
                        if (tests.contains(it.key)) {
                            // add duplicated group name in name of package group name for exclude it
                            modifiedPackageName = "${modifiedPackageName},${it.key}"
                        } else {
                            options.groupsUMS << it.key
                        }
                    }
                }
                options.tests = utils.uniteSuites(this, "jobs/weights.json", tests)
                options.tests.each {
                    def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }
                modifiedPackageName = modifiedPackageName.replace('~,', '~')

                if (options.isPackageSplitted) {
                    options.testsPackage = "none"
                } else {
                    options.testsPackage = modifiedPackageName
                    if (options.engines.count(",") > 0) {
                        options.engines.split(",").each { tests << "${modifiedPackageName}-${it}" }
                    } else {
                        tests << modifiedPackageName
                    }
                    options.timeouts[options.testsPackage] = options.NON_SPLITTED_PACKAGE_TIMEOUT + options.ADDITIONAL_XML_TIMEOUT
                }

                options.tests = tests
            } else {
                options.groupsUMS = options.tests.split(" ") as List
                options.tests = utils.uniteSuites(this, "jobs/weights.json", options.tests.split(" ") as List)
                options.tests.each {
                    def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }
            }

            options.skippedTests = [:]
            if (options.updateRefs == "No") {
                options.platforms.split(';')
                        .findAll { it }
                        .collect { it.tokenize(':') }
                        .findAll { it.size() > 1 && it.get(1) != '' }
                        .collectEntries { [it.get(0), it.get(1).split(',')] }
                        .each {
                            def osName = it.key
                            it.value.each { gpuName ->
                                options.tests.each { test ->
                                    try {
                                        dir("jobs_launcher") {
                                            String output = bat(script: "is_group_skipped.bat ${gpuName} ${osName} \"\" \"..\\jobs\\Tests\\${test}\\test.cases.json\"", returnStdout: true).trim()
                                            if (output.contains("True")) {
                                                if (!options.skippedTests.containsKey(test)) {
                                                    options.skippedTests[test] = []
                                                }
                                                options.skippedTests[test].add("${gpuName}-${osName}")
                                            }
                                        }
                                    } catch (Exception e) {
                                        println e.toString()
                                    }
                                }
                            }
                        }
                println """
                    Skipped test groups:
                    ${options.skippedTests.inspect()}
                """
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


def executeDeploy(Map options, List platformList, List testResultList) {
    try {
        if (options['executeTests'] && testResultList) {
            withNotifications(title: "Building test report", options: options, startUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
                checkOutBranchOrScm(options["testsBranch"], "git@github.com:luxteam/jobs_test_usdviewer.git")
            }

            List lostStashes = []
            dir("summaryTestResults") {
                unstashCrashInfo(options['nodeRetry'])
                testResultList.each {
                    dir("$it".replace("testResult-", "")) {
                        try {
                            unstash "$it"
                        } catch (e) {
                            println """
                                [ERROR] Failed to unstash ${it}
                                ${e.toString()}
                            """
                            lostStashes << ("'${it}'".replace("testResult-", ""))
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
                println "[ERROR] Can't generate number of lost tests"
            }
            
            String branchName = env.BRANCH_NAME ?: options.projectBranch
            try {
                GithubNotificator.updateStatus("Deploy", "Building test report", "in_progress", options, NotificationConfiguration.BUILDING_REPORT, "${BUILD_URL}")
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}", "BUILD_NAME=${options.baseBuildName}"]) {
                    dir("jobs_launcher") {
                        def retryInfo = JsonOutput.toJson(options.nodeRetry)
                        dir("..\\summaryTestResults") {
                            writeJSON file: 'retry_info.json', json: JSONSerializer.toJSON(retryInfo, new JsonConfig()), pretty: 4
                        }
                        if (options.sendToUMS) {
                            options.universeManager.sendStubs(options, "..\\summaryTestResults\\lost_tests.json", "..\\summaryTestResults\\skipped_tests.json", "..\\summaryTestResults\\retry_info.json")
                        }
                        bat """
                            build_reports.bat ..\\summaryTestResults "USDViewer" ${options.commitSHA} ${branchName} \"${utils.escapeCharsByUnicode(options.commitMessage)}\"
                        """
                    }
                }
            } catch (e) {
                String errorMessage = utils.getReportFailReason(e.getMessage())
                GithubNotificator.updateStatus("Deploy", "Building test report", "failure", options, errorMessage, "${BUILD_URL}")
                if (utils.isReportFailCritical(e.getMessage())) {
                    options.problemMessageManager.saveSpecificFailReason(errorMessage, "Deploy")
                    println """
                        [ERROR] Failed to build test report.
                        ${e.toString()}
                    """
                    if (!options.testDataSaved) {
                        try {
                            // Save test data for access it manually anyway
                            utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", \
                                "Test Report", "Summary Report, Performance Report, Compare Report")
                            options.testDataSaved = true 
                        } catch (e1) {
                            println """
                                [WARNING] Failed to publish test data.
                                ${e.toString()}
                            """
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
                    bat """
                        get_status.bat ..\\summaryTestResults
                    """
                }
            } catch (e) {
                println """
                    [ERROR] during slack status generation.
                    ${e.toString()}
                """
            }

            try {
                dir("jobs_launcher") {
                    archiveArtifacts "launcher.engine.log"
                }
            } catch(e) {
                println """
                    [ERROR] during archiving launcher.engine.log
                    ${e.toString()}
                """
            }

            Map summaryTestResults = [:]
            try {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                summaryTestResults = [passed: summaryReport.passed, failed: summaryReport.failed, error: summaryReport.error]
                if (summaryReport.error > 0) {
                    println "[INFO] Some tests marked as error. Build result = FAILURE."
                    currentBuild.result = "FAILURE"
                    options.problemMessageManager.saveGlobalFailReason(NotificationConfiguration.SOME_TESTS_ERRORED)
                } else if (summaryReport.failed > 0) {
                    println "[INFO] Some tests marked as failed. Build result = UNSTABLE."
                    currentBuild.result = "UNSTABLE"
                    options.problemMessageManager.saveUnstableReason(NotificationConfiguration.SOME_TESTS_FAILED)
                }
            } catch(e) {
                println """
                    [ERROR] CAN'T GET TESTS STATUS
                    ${e.toString()}
                """
                options.problemMessageManager.saveUnstableReason(NotificationConfiguration.CAN_NOT_GET_TESTS_STATUS)
                currentBuild.result = "UNSTABLE"
            }

            try {
                options.testsStatus = readFile("summaryTestResults/slack_status.json")
            } catch (e) {
                println e.toString()
                options.testsStatus = ""
            }

            withNotifications(title: "Building test report", options: options, configuration: NotificationConfiguration.PUBLISH_REPORT) {
                utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", \
                    "Test Report", "Summary Report, Performance Report, Compare Report")
                if (summaryTestResults) {
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", options,
                            "${NotificationConfiguration.REPORT_PUBLISHED} Results: passed - ${summaryTestResults.passed}, failed - ${summaryTestResults.failed}, error - ${summaryTestResults.error}.", "${BUILD_URL}/Test_20Report")
                } else {
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", options,
                            NotificationConfiguration.REPORT_PUBLISHED, "${BUILD_URL}/Test_20Report")
                }
            }
        }
    } catch (e) {
        println e.toString()
        throw e
    }
}


def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_WX9100,AMD_RXVEGA,AMD_RadeonVII,AMD_RX5700XT;OSX',
         String updateRefs = 'No',
         Boolean enableNotifications = true,
         String testsPackage = "",
         String tests = "",
         Boolean splitTestsExecution = true,
         String tester_tag = 'USDViewer',
         String parallelExecutionTypeString = "TakeAllNodes",
         Integer testCaseRetries = 3,
         Boolean sendToUMS = true) {
    ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)
    Map options = [stage: "Init", problemMessageManager: problemMessageManager]
    try {
        withNotifications(options: options, configuration: NotificationConfiguration.INITIALIZATION) {
            println """
                Platforms: ${platforms}
                Tests: ${tests}
                Tests package: ${testsPackage}
                Tests execution type: ${parallelExecutionTypeString}
            """
            options << [projectBranch: projectBranch,
                        testsBranch: testsBranch,
                        updateRefs: updateRefs,
                        enableNotifications: enableNotifications,
                        PRJ_NAME: 'USDViewer',
                        PRJ_ROOT: 'rpr-core',
                        projectRepo: 'git@github.com:Radeon-Pro/RPRViewer.git',
                        BUILDER_TAG: 'BuilderUSDViewer',
                        TESTER_TAG: tester_tag,
                        executeBuild: true,
                        executeTests: true,
                        splitTestsExecution: splitTestsExecution,
                        DEPLOY_FOLDER: "USDViewer",
                        testsPackage: testsPackage,
                        BUILD_TIMEOUT: 90,
                        TEST_TIMEOUT: 45,
                        ADDITIONAL_XML_TIMEOUT: 15,
                        NON_SPLITTED_PACKAGE_TIMEOUT: 45,
                        DEPLOY_TIMEOUT: 45,
                        tests: tests,
                        nodeRetry: [],
                        problemMessageManager: problemMessageManager,
                        platforms: platforms,
                        parallelExecutionType: TestsExecutionType.valueOf(parallelExecutionTypeString),
                        parallelExecutionTypeString: parallelExecutionTypeString,
                        testCaseRetries: testCaseRetries,
                        universePlatforms: convertPlatforms(platforms),
                        sendToUMS: sendToUMS
                        ]
            if (sendToUMS) {
                UniverseManager manager = UniverseManagerFactory.get(this, options, env, PRODUCT_NAME)
                manager.init()
                options["universeManager"] = manager
            }
        }
        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy, options)
    } catch(e) {
        currentBuild.result = "FAILURE"
        println e.toString()
        throw e
    } finally {
        String problemMessage = options.problemMessageManager.publishMessages()
        if (options.sendToUMS) {
            options.universeManager.closeBuild(problemMessage, options)
        }
    }
}
