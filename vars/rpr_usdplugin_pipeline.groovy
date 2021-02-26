import groovy.transform.Field
import groovy.json.JsonOutput
import utils
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import TestsExecutionType
import universe.*


@Field final String PRODUCT_NAME = "AMD%20Radeon™%20ProRender%20for%20USDPlugin"


def installHoudiniPlugin(String osName, Map options) {
    switch(osName) {
        case 'Windows':
            unstash "appWindows"
            bat """
                %CIS_TOOLS%\\7-Zip\\7z.exe -aoa e hdRpr_${osName}.tar.gz
                %CIS_TOOLS%\\7-Zip\\7z.exe -aoa x tmpPackage.tar 
                cd hdRpr*
                echo y | activateHoudiniPlugin.exe >> \"${options.stageName}_${options.currentTry}.install.log\"  2>&1
            """
            break

        case "OSX":
            unstash "appOSX"
            sh """
                tar -xzf hdRpr_${osName}.tar.gz 
                cd hdRpr*
                chmod +x activateHoudiniPlugin
                echo y | ./activateHoudiniPlugin >> \"${options.stageName}_${options.currentTry}.install.log\" 2>&1
            """
            break

        default:
            unstash "app${osName}"
            sh """
                tar -xzf hdRpr_${osName}.tar.gz
                cd hdRpr*
                chmod +x activateHoudiniPlugin
                echo y | ./activateHoudiniPlugin \"${options.stageName}_${options.currentTry}.install.log\" 2>&1
            """
    }
}


def buildRenderCache(String osName, Map options) {
    dir("scripts") {
        switch(osName) {
            case 'Windows':
                bat """
                    build_rpr_cache.bat \"${options.win_tool_path}\\bin\\husk.exe\" >> \"..\\${options.stageName}_${options.currentTry}.cb.log\"  2>&1
                """
                break
            case 'OSX':
                sh """
                    chmod +x build_rpr_cache.sh
                    ./build_rpr_cache.sh \"${options.osx_tool_path}/bin/husk\" >> \"../${options.stageName}_${options.currentTry}.cb.log\" 2>&1
                """
                break
            default:
                sh """
                    chmod +x build_rpr_cache.sh
                    ./build_rpr_cache.sh \"/home/user/${options.unix_tool_path}/bin/husk\" >> \"../${options.stageName}_${options.currentTry}.cb.log\" 2>&1
                """     
        }
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
            default:
                sh """
                    chmod +x make_results_baseline.sh
                    ./make_results_baseline.sh ${delete}
                """
        }
    }
}


def executeTestCommand(String osName, String asicName, Map options) {
    timeout(time: options.TEST_TIMEOUT, unit: 'MINUTES') {
        dir('scripts') {
            UniverseManager.executeTests(osName, asicName, options) {
                switch(osName) {
                    case 'Windows':
                        if (options.enableRIFTracing) {
                            bat """
                                mkdir -p "${env.WORKSPACE}\\${env.STAGE_NAME}_RIF_Trace"
                                set RIF_TRACING_ENABLED=1
                                set RIF_TRACING_PATH=${env.WORKSPACE}\\${env.STAGE_NAME}_RIF_Trace
                                run.bat ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.updateRefs} \"${options.win_tool_path}\\bin\\husk.exe\" >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                            """
                        } else {
                            bat """
                                run.bat ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.updateRefs} \"${options.win_tool_path}\\bin\\husk.exe\" >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                            """
                        }
                        break

                    case 'OSX':
                        if (options.enableRIFTracing) {
                            sh """
                                mkdir -p "${env.WORKSPACE}/${env.STAGE_NAME}_RIF_Trace"
                                export RIF_TRACING_ENABLED=1
                                export RIF_TRACING_PATH=${env.WORKSPACE}/${env.STAGE_NAME}_RIF_Trace
                                chmod +x run.sh
                                ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.updateRefs} \"${options.osx_tool_path}/bin/husk\" >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                            """
                        } else {
                            sh """
                                chmod +x run.sh
                                ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.updateRefs} \"${options.osx_tool_path}/bin/husk\" >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                            """
                        }
                        break

                    default:
                        if (options.enableRIFTracing) {
                            sh """
                                mkdir -p "${env.WORKSPACE}/${env.STAGE_NAME}_RIF_Trace"
                                export RIF_TRACING_ENABLED=1
                                export RIF_TRACING_PATH=${env.WORKSPACE}/${env.STAGE_NAME}_RIF_Trace
                                chmod +x run.sh
                                ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.updateRefs} \"/home/user/${options.unix_tool_path}/bin/husk\" >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                            """
                        } else {
                            sh """
                                chmod +x run.sh
                                ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.updateRefs} \"/home/user/${options.unix_tool_path}/bin/husk\" >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                            """
                        }
                }
            }
        }
    }
}


def executeTests(String osName, String asicName, Map options) {
     if (options.buildType == "Houdini") {
        withNotifications(title: options["stageName"], options: options, logUrl: "${BUILD_URL}", configuration: NotificationConfiguration.INSTALL_HOUDINI) {
            timeout(time: "20", unit: "MINUTES") {
                houdini_python3 = options.houdini_python3 ? "--python3" : ''
                withCredentials([[$class: "UsernamePasswordMultiBinding", credentialsId: "sidefxCredentials", usernameVariable: "USERNAME", passwordVariable: "PASSWORD"]]) {
                    println python3("${CIS_TOOLS}/houdini_api.py --client_id \"$USERNAME\" --client_secret_key \"$PASSWORD\" --version \"${options.houdiniVersion}\" ${houdini_python3}")
                }
            }
        }
    }
    if (options.sendToUMS) {
        options.universeManager.startTestsStage(osName, asicName, options)
    }
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true
    try {
        withNotifications(title: options["stageName"], options: options, logUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
            timeout(time: "5", unit: "MINUTES") {
                cleanWS(osName)
                checkOutBranchOrScm(options["testsBranch"], "git@github.com:luxteam/jobs_test_houdini.git")
                println "[INFO] Preparing on ${env.NODE_NAME} successfully finished."
            }
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_SCENES) {
            String assetsDir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_usdplugin_autotests_assets" : "/mnt/c/TestResources/rpr_usdplugin_autotests_assets"
            downloadFiles("/volume1/Assets/rpr_usdplugin_autotests/", assetsDir)
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.INSTALL_PLUGIN) {
            timeout(time: "10", unit: "MINUTES") {
                installHoudiniPlugin(osName, options)
            }
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.BUILD_CACHE) {
            timeout(time: "5", unit: "MINUTES") {
                buildRenderCache(osName, options)
                if (!fileExists("./Work/Results/Houdini/cache_building.jpg")) {
                    println "[ERROR] Failed to build cache on ${env.NODE_NAME}. No output image found."
                    throw new ExpectedExceptionWrapper("No output image after cache building.", new Exception("No output image after cache building."))
                }
            }
        }

        String REF_PATH_PROFILE="/volume1/Baselines/rpr_usdplugin_autotests/${asicName}-${osName}"
        outputEnvironmentInfo(osName, options.stageName, options.currentTry)

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
                String baselineDir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_houdini_autotests_baselines" : "/mnt/c/TestResources/rpr_houdini_autotests_baselines"
                println "[INFO] Downloading reference images for ${options.testsPackage}"
                options.tests.split(" ").each { downloadFiles("${REF_PATH_PROFILE}/${it}", baselineDir) }
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
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, e.getMessage(), "${BUILD_URL}")
            throw new ExpectedExceptionWrapper(e.getMessage(), e.getCause())
        } else {
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, NotificationConfiguration.REASON_IS_NOT_IDENTIFIED, "${BUILD_URL}")
            throw new ExpectedExceptionWrapper(NotificationConfiguration.REASON_IS_NOT_IDENTIFIED, e)
        }
    } finally {
        try {
            dir(options.stageName) {
                utils.moveFiles(this, osName, "../*.log", ".")
                utils.moveFiles(this, osName, "../scripts/*.log", ".")
                utils.renameFile(this, osName, "launcher.engine.log", "${options.stageName}_engine_${options.currentTry}.log")
            }
            archiveArtifacts artifacts: "${options.stageName}/*.log", allowEmptyArchive: true
            archiveArtifacts artifacts: "${env.STAGE_NAME}_RIF_Trace/**/*.*", allowEmptyArchive: true
            if (options.sendToUMS) {
                options.universeManager.sendToMINIO(options, osName, "../${options.stageName}", "*.log")
            }
            if (stashResults) {
                dir('Work') {
                    if (fileExists("Results/Houdini/session_report.json")) {
                        def sessionReport = readJSON file: 'Results/Houdini/session_report.json'
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

                        println "Total: ${sessionReport.summary.total}"
                        println "Error: ${sessionReport.summary.error}"
                        println "Skipped: ${sessionReport.summary.skipped}"
                        if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped || sessionReport.summary.total == 0) {
                            if (sessionReport.summary.total != sessionReport.summary.skipped){
                                // collectCrashInfo(osName, options, options.currentTry)
                                String errorMessage = (options.currentTry < options.nodeReallocateTries) ?
                                        "All tests were marked as error. The test group will be restarted." :
                                        "All tests were marked as error."
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


def executeBuildWindows(String osName, Map options) {
    clearBinariesWin()

    if (options.rebuildUSD) {
        dir ("USD") {
            bat """
                set PATH=c:\\python36\\;c:\\python36\\scripts\\;%PATH%;
                call "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\VC\\Auxiliary\\Build\\vcvarsall.bat" amd64 >> ${STAGE_NAME}_USD.log 2>&1
                
                if exist USDgen rmdir /s/q USDgen
                if exist USDinst rmdir /s/q USDinst

                python build_scripts\\build_usd.py -v --build USDgen/build --src USDgen/src USDinst >> ${STAGE_NAME}_USD.log 2>&1
            """
        }
    }

    dir ("RadeonProRenderUSD") {
        GithubNotificator.updateStatus("Build", osName, "in_progress", options, NotificationConfiguration.BUILD_SOURCE_CODE_START_MESSAGE, "${BUILD_URL}/artifact/Build-Windows.log")
        if (options.buildType == "Houdini") {
            options.win_houdini_python3 = options.houdini_python3 ? " Python3" : ""
            options.win_tool_path = "C:\\Program Files\\Side Effects Software\\Houdini ${options.houdiniVersion}${options.win_houdini_python3}"
            bat """
                mkdir build
                set PATH=c:\\python35\\;c:\\python35\\scripts\\;%PATH%;
                set HFS=${options.win_tool_path}
                python pxr\\imaging\\plugin\\hdRpr\\package\\generatePackage.py -i "." -o "build" >> ..\\${STAGE_NAME}.log 2>&1
            """
        } else {
            bat """
                mkdir build
                set PATH=c:\\python35\\;c:\\python35\\scripts\\;%PATH%;
                python pxr\\imaging\\plugin\\hdRpr\\package\\generatePackage.py -i "." -o "build" --cmake_options " -Dpxr_DIR=../USD/USDinst" >> ..\\${STAGE_NAME}.log 2>&1
            """
        } 

        dir("build") {
            if (options.buildType == "Houdini") {
                options.win_houdini_python3 = options.houdini_python3 ? "py3" : "py2.7"
                options.win_build_name = "hdRpr-${options.pluginVersion}-Houdini-${options.houdiniVersion}-${options.win_houdini_python3}-${osName}"
            } else if (options.buildType == "USD") {
                options.win_build_name = "hdRpr-${options.pluginVersion}-USD-${osName}"
            }
            archiveArtifacts "hdRpr*.tar.gz"
            String buildName = "${options.win_build_name}.tar.gz"
            String pluginUrl = "${BUILD_URL}/artifact/${buildName}"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${pluginUrl}">[BUILD: ${BUILD_ID}] ${buildName}</a></h3>"""
            if (options.sendToUMS) {
                // WARNING! call sendToMinio in build stage only from parent directory
                dir("../..") {
                    options.universeManager.sendToMINIO(options, osName, "..\\RadeonProRenderUSD\\build", buildName, false)
                }
            }
            bat "rename hdRpr* hdRpr_${osName}.tar.gz"
            stash includes: "hdRpr_${osName}.tar.gz", name: "app${osName}"
            GithubNotificator.updateStatus("Build", osName, "success", options, NotificationConfiguration.BUILD_SOURCE_CODE_END_MESSAGE, pluginUrl)
        }
    }
}


def executeBuildOSX(String osName, Map options) {
    clearBinariesUnix()

    if (options.rebuildUSD) {
        dir ("USD") {
            sh """
                if [ -d "./USDgen" ]; then
                    rm -fdr ./USDgen
                fi

                if [ -d "./USDinst" ]; then
                    rm -fdr ./USDinst
                fi

                export OS=Darwin
                python3 build_scripts/build_usd.py -vvv --build USDgen/build --src USDgen/src USDinst >> ${STAGE_NAME}_USD.log 2>&1
            """
        }
    }

    dir ("RadeonProRenderUSD") {
        GithubNotificator.updateStatus("Build", osName, "in_progress", options, NotificationConfiguration.BUILD_SOURCE_CODE_START_MESSAGE, "${BUILD_URL}/artifact/Build-OSX.log")
        if (options.buildType == "Houdini") {
            options.osx_houdini_python3 = options.houdini_python3 ? "-py3" : "-py2"
            options.osx_tool_path = "/Applications/Houdini/Houdini${options.houdiniVersion}${options.osx_houdini_python3}/Frameworks/Houdini.framework/Versions/Current/Resources"
            sh """
                mkdir build
                export HFS=${options.osx_tool_path}
                python3 pxr/imaging/plugin/hdRpr/package/generatePackage.py -i "." -o "build" >> ../${STAGE_NAME}.log 2>&1
            """
        } else {
            sh """
                mkdir build
                python3 pxr/imaging/plugin/hdRpr/package/generatePackage.py -i "." -o "build" --cmake_options " -Dpxr_DIR=../USD/USDinst" >> ../${STAGE_NAME}.log 2>&1
            """
        }

        dir("build") {
            if (options.buildType == "Houdini") {
                options.osx_houdini_python3 = options.houdini_python3 ? "py3" : "py2.7"
                options.osx_build_name = "hdRpr-${options.pluginVersion}-Houdini-${options.houdiniVersion}-${options.osx_houdini_python3}-macOS"
            } else if (options.buildType == "USD") {
                options.osx_build_name = "hdRpr-${options.pluginVersion}-USD-macOS"
            }
            archiveArtifacts "hdRpr*.tar.gz"
            String buildName = "${options.osx_build_name}.tar.gz"
            String pluginUrl = "${BUILD_URL}/artifact/${buildName}"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${pluginUrl}">[BUILD: ${BUILD_ID}] ${buildName}</a></h3>"""
            if (options.sendToUMS) {
                // WARNING! call sendToMinio in build stage only from parent directory
                dir("../..") {
                    options.universeManager.sendToMINIO(options, osName, "../RadeonProRenderUSD/build", buildName, false)
                }
            }
            sh "mv hdRpr*.tar.gz hdRpr_${osName}.tar.gz"
            stash includes: "hdRpr_${osName}.tar.gz", name: "app${osName}"
            GithubNotificator.updateStatus("Build", osName, "success", options, NotificationConfiguration.BUILD_SOURCE_CODE_END_MESSAGE, pluginUrl)
        }
    }
}


def executeBuildUnix(String osName, Map options) {
    clearBinariesUnix()

    if (options.rebuildUSD) {
        dir ("USD") {
            sh """
                if [ -d "./USDgen" ]; then
                    rm -fdr ./USDgen
                fi

                if [ -d "./USDinst" ]; then
                    rm -fdr ./USDinst
                fi

                export OS=
                python3 build_scripts/build_usd.py -v --build USDgen/build --src USDgen/src USDinst >> ${STAGE_NAME}_USD.log 2>&1
            """
        }
    }

    dir("RadeonProRenderUSD") {
        GithubNotificator.updateStatus("Build", osName, "in_progress", options, NotificationConfiguration.BUILD_SOURCE_CODE_START_MESSAGE, "${BUILD_URL}/artifact/Build-Ubuntu18.log")
        String installation_path
        if (env.HOUDINI_INSTALLATION_PATH) {
            installation_path = "${env.HOUDINI_INSTALLATION_PATH}"
        } else {
            installation_path = "/home/admin"
        }
        if (options.buildType == "Houdini") {
            options.unix_houdini_python3 = options.houdini_python3 ? "-py3" : "-py2"
            options.unix_tool_path = "Houdini/hfs${options.houdiniVersion}${options.unix_houdini_python3}"
            sh """
                mkdir build
                export HFS=${installation_path}/${options.unix_tool_path}
                python3 pxr/imaging/plugin/hdRpr/package/generatePackage.py -i "." -o "build" >> ../${STAGE_NAME}.log 2>&1
            """
        } else {
            sh """
                mkdir build
                python3 pxr/imaging/plugin/hdRpr/package/generatePackage.py -i "." -o "build" --cmake_options " -Dpxr_DIR=../USD/USDinst" >> ../${STAGE_NAME}.log 2>&1
            """
        }

        dir("build") {
            if (options.buildType == "Houdini") {
                options.unix_houdini_python3 = options.houdini_python3 ? "py3" : "py2.7"
                if (osName == "Ubuntu18") {
                    options.ubuntu_build_name = "hdRpr-${options.pluginVersion}-Houdini-${options.houdiniVersion}-${options.unix_houdini_python3}-ubuntu18.04"
                } else {
                    options.centos_build_name = "hdRpr-${options.pluginVersion}-Houdini-${options.houdiniVersion}-${options.unix_houdini_python3}-${osName}"
                }
            } else if (options.buildType == "USD") {
                if (osName == "Ubuntu18") {
                    options.ubuntu_build_name = "hdRpr-${options.pluginVersion}-USD-ubuntu18.04"
                } else {
                    options.centos_build_name = "hdRpr-${options.pluginVersion}-USD-${osName}"
                }
            }
            if (osName == "Ubuntu18") options.unix_build_name = options.ubuntu_build_name else options.unix_build_name = options.centos_build_name
            archiveArtifacts "hdRpr*.tar.gz"
            String buildName = "${options.unix_build_name}.tar.gz"
            String pluginUrl = "${BUILD_URL}/artifact/${buildName}"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${pluginUrl}">[BUILD: ${BUILD_ID}] ${buildName}</a></h3>"""
            if (options.sendToUMS) {
                // WARNING! call sendToMinio in build stage only from parent directory
                dir("../..") {
                    options.universeManager.sendToMINIO(options, osName, "../RadeonProRenderUSD/build", buildName, false)
                }
            }
            sh "mv hdRpr*.tar.gz hdRpr_${osName}.tar.gz"
            stash includes: "hdRpr_${osName}.tar.gz", name: "app${osName}"
            GithubNotificator.updateStatus("Build", osName, "success", options, NotificationConfiguration.BUILD_SOURCE_CODE_END_MESSAGE, pluginUrl)
        }
    }
}


def executeBuild(String osName, Map options) {
    if (options.sendToUMS) {
        options.universeManager.startBuildStage(osName)
    }
    if (options.buildType == "Houdini") {
        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.INSTALL_HOUDINI) {
            timeout(time: "20", unit: "MINUTES") {
                houdini_python3 = options.houdini_python3 ? "--python3" : ''
                withCredentials([[$class: "UsernamePasswordMultiBinding", credentialsId: "sidefxCredentials", usernameVariable: "USERNAME", passwordVariable: "PASSWORD"]]) {
                    println python3("${CIS_TOOLS}/houdini_api.py --client_id \"$USERNAME\" --client_secret_key \"$PASSWORD\" --version \"${options.houdiniVersion}\" ${houdini_python3}")
                }
            }
        }
    }

    try {
        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
            dir ("RadeonProRenderUSD") {
                checkOutBranchOrScm(options.projectBranch, options.projectRepo)
            }
        }

        if (options.rebuildUSD) {
            withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_USD_REPO) {
                dir('USD') {
                    checkOutBranchOrScm(options["usdBranch"], "git@github.com:PixarAnimationStudios/USD.git")
                }
            }
        }

        outputEnvironmentInfo(osName)
        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
            switch(osName) {
                case "Windows":
                    executeBuildWindows(osName, options)
                    break
                case "OSX":
                    executeBuildOSX(osName, options)
                    break
                default:
                    executeBuildUnix(osName, options)
            }
        }
    } catch (e) {
        throw e
    } finally {
        archiveArtifacts "*.log"
        if (options.rebuildUSD) {
            archiveArtifacts "USD/*.log"
        }
        if (options.sendToUMS) {
            options.universeManager.sendToMINIO(options, osName, "..", "*.log")
            if (options.rebuildUMS) {
                options.universeManager.sendToMINIO(options, osName, "../UMS/", "*.log")
            }
            options.universeManager.finishBuildStage(osName)
        }
    }
}

def executePreBuild(Map options) {
    // manual job
    if (options.forceBuild) {
        options.executeBuild = true
        options.executeTests = true
    // auto job
    } else {
        if (env.CHANGE_URL) {
            println "[INFO] Branch was detected as Pull Request"
            options.executeBuild = true
            options.executeTests = true
            options.testsPackage = "Full.json"
        } else if (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop") {
           println "[INFO] ${env.BRANCH_NAME} branch was detected"
           options.executeBuild = true
           options.executeTests = true
           options.testsPackage = "Full.json"
        } else {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
            options.testsPackage = "Full.json"
        }
    }

    dir('RadeonProRenderUSD') {
        withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
            checkOutBranchOrScm(options["projectBranch"], options["projectRepo"], true)
        }

        options.commitAuthor = utils.getBatOutput(this, "git show -s --format=%%an HEAD ")
        options.commitMessage = utils.getBatOutput(this, "git log --format=%%B -n 1")
        options.commitSHA = utils.getBatOutput(this, "git log --format=%%H -1 ")
        println """
            The last commit was written by ${options.commitAuthor}.
            Commit message: ${options.commitMessage}
            Commit SHA: ${options.commitSHA}
        """
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch ?: env.BRANCH_NAME}<br/>"

        withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.INCREMENT_VERSION) {
            options.majorVersion = version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_MAJOR_VERSION "', '')
            options.minorVersion = version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_MINOR_VERSION "', '')
            options.patchVersion = version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_PATCH_VERSION "', '')
            options.pluginVersion = "${options.majorVersion}.${options.minorVersion}.${options.patchVersion}"

            currentBuild.description += "<b>Version:</b> ${options.majorVersion}.${options.minorVersion}.${options.patchVersion}<br/>"
            currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
            currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"

            if (options['incrementVersion']) {
                withNotifications(title: "Jenkins build configuration", printMessage: true, options: options, configuration: NotificationConfiguration.CREATE_GITHUB_NOTIFICATOR) {
                    GithubNotificator githubNotificator = new GithubNotificator(this, options)
                    githubNotificator.init(options)
                    options["githubNotificator"] = githubNotificator
                    githubNotificator.initPreBuild("${BUILD_URL}")
                }
                
                if (env.BRANCH_NAME == "develop" && options.commitAuthor != "radeonprorender") {
                    println "[INFO] Incrementing version of change made by ${options.commitAuthor}."
                    println "[INFO] Current build version: ${options.majorVersion}.${options.minorVersion}.${options.patchVersion}"

                    newVersion = version_inc(options.patchVersion, 1, ' ')
                    println "[INFO] New build version: ${newVersion}"

                    version_write("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_PATCH_VERSION "', newVersion, '')
                    options.patchVersion = version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_PATCH_VERSION "', '')
                    options.pluginVersion = "${options.majorVersion}.${options.minorVersion}.${options.patchVersion}"
                    println "[INFO] Updated build version: ${options.patchVersion}"

                    bat """
                        git add cmake/defaults/Version.cmake
                        git commit -m "buildmaster: version update to ${options.majorVersion}.${options.minorVersion}.${options.patchVersion}"
                        git push origin HEAD:develop
                    """

                    //get commit's sha which have to be build
                    options['projectBranch'] = utils.getBatOutput(this, "git log --format=%%H -1 ")
                    println "[INFO] Project branch hash: ${options.projectBranch}"
                }
            }
        }
    }

    options.groupsUMS = []
    withNotifications(title: "Jenkins build configuration", options: options, configuration: NotificationConfiguration.CONFIGURE_TESTS) {
        dir('jobs_test_houdini') {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_houdini.git')
            dir('jobs_launcher') {
                options['jobsLauncherBranch'] = utils.getBatOutput(this, "git log --format=%%H -1 ")
            }
            options['testsBranch'] = utils.getBatOutput(this, "git log --format=%%H -1 ")
            println "[INFO] Test branch hash: ${options['testsBranch']}"

            if (options.testsPackage != "none") {
                def groupNames = readJSON(file: "jobs/${options.testsPackage}")["groups"].collect { it.key }
                // json means custom test suite. Split doesn't supported
                options.tests = groupNames
                options.groupsUMS = groupNames
                options.testsPackage = "none"
            } else {
                options.groupsUMS = options.tests.split(" ")
            }
            options.testsList = ['']
            options.tests = options.tests.join(" ")
        }
        if (env.BRANCH_NAME && options.githubNotificator) {
            options.githubNotificator.initChecks(options, "${BUILD_URL}")
        }
        if (options.sendToUMS) {
            options.universeManager.createBuilds(options)
        }
    }
}


def executeDeploy(Map options, List platformList, List testResultList) {
    try {
        if (options['executeTests'] && testResultList) {
            withNotifications(title: "Building test report", options: options, startUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
                checkOutBranchOrScm(options["testsBranch"], "git@github.com:luxteam/jobs_test_houdini.git")
            }
            List lostStashes = []
            dir("summaryTestResults") {
                unstashCrashInfo(options['nodeRetry'])
                testResultList.each {
                    dir(it.replace("testResult-", "")) {
                        try {
                            unstash it
                        } catch (e) {
                            println "Can't unstash ${it}"
                            lostStashes << "'$it'".replace("testResult-", "")
                            println e.toString()
                        }
                    }
                }
            }

            try {
                dir("jobs_launcher") {
                    bat "count_lost_tests.bat \"${lostStashes}\" .. ..\\summaryTestResults \"${options.splitTestsExecution}\" \"${options.testsPackage}\" \"${options.tests.toString()}\" \"\" \"{}\""
                }
            } catch (e) {
                println("[ERROR] Can't generate number of lost tests")
            }

            try {
                GithubNotificator.updateStatus("Deploy", "Building test report", "in_progress", options, NotificationConfiguration.BUILDING_REPORT, "${BUILD_URL}")
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}", "BUILD_NAME=${options.baseBuildName}"]) {
                    dir("jobs_launcher") {
                        options.branchName = options.projectBranch ?: env.BRANCH_NAME
                        if (options.incrementVersion) {
                            options.branchName = "develop"
                        }
                        options.commitMessage = options.commitMessage.replace("'", "")
                        options.commitMessage = options.commitMessage.replace('"', '')

                        def retryInfo = JsonOutput.toJson(options.nodeRetry)
                        dir("..\\summaryTestResults") {
                            writeJSON file: 'retry_info.json', json: JSONSerializer.toJSON(retryInfo, new JsonConfig()), pretty: 4
                        }
                        if (options.sendToUMS) {
                            options.universeManager.sendStubs(options, "..\\summaryTestResults\\lost_tests.json", "..\\summaryTestResults\\skipped_tests.json", "..\\summaryTestResults\\retry_info.json")
                        }
                        if (options.buildType == "Houdini") {
                            def python3 = options.houdini_python3 ? "py3" : "py2.7"
                            def tool = "Houdini ${options.houdiniVersion} ${python3}"
                            bat """
                                build_reports.bat ..\\summaryTestResults \"${utils.escapeCharsByUnicode(tool)}\" ${options.commitSHA} ${options.branchName} \"${utils.escapeCharsByUnicode(options.commitMessage)}\"
                            """
                        } else {
                            bat """
                                build_reports.bat ..\\summaryTestResults USD ${options.commitSHA} ${options.branchName} \"${utils.escapeCharsByUnicode(options.commitMessage)}\"
                            """
                        }
                        bat "get_status.bat ..\\summaryTestResults"
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
                            utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", "Test Report", "Summary Report, Performance Report, Compare Report")
                            options.testDataSaved = true
                        } catch(e1) {
                            println """
                                [WARNING] Failed to publish test data.
                                ${e1.toString()}
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
                println """
                    [ERROR] CAN'T GET TESTS STATUS
                    ${e.toString()}
                """
                options.problemMessageManager.saveUnstableReason(NotificationConfiguration.CAN_NOT_GET_TESTS_STATUS)
                currentBuild.result = "UNSTABLE"
            }

            try {
                options.testsStatus = readFile("summaryTestResults/slack_status.json")
            } catch(e) {
                println e.toString()
                options.testsStatus = ""
            }

            withNotifications(title: "Building test report", options: options, configuration: NotificationConfiguration.PUBLISH_REPORT) {
                utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", \
                    "Test Report", "Summary  Report, Performance Rep ort, Compare Report")
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
        println e.toString()
        throw e
    }
}


def call(String projectRepo = "git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderUSD.git",
        String projectBranch = "",
        String usdBranch = "master",
        String testsBranch = "master",
        String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,AMD_RadeonVII,AMD_RX5700XT,NVIDIA_GF1080TI,NVIDIA_RTX2080,AMD_RX6800;OSX:AMD_RXVEGA;Ubuntu18:AMD_RadeonVII,NVIDIA_RTX2070;CentOS7',
        String buildType = "Houdini",
        Boolean rebuildUSD = false,
        String houdiniVersion = "18.5.462",
        Boolean houdini_python3 = false,
        String updateRefs = 'No',
        String testsPackage = "Full.json",
        String tests = "",
        Boolean enableRIFTracing = false,
        String width = "0",
        String height = "0",
        String tester_tag = "Houdini",
        Boolean splitTestsExecution = false,
        Boolean incrementVersion = true,
        String parallelExecutionTypeString = "TakeOneNodePerGPU",
        Boolean enableNotifications = true,
        Boolean forceBuild = false,
        Boolean sendToUMS = true || updateRefs.contains('Update')) {
    ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)
    Map options = [stage: "Init", problemMessageManager: problemMessageManager]
    try {
        withNotifications(options: options, configuration: NotificationConfiguration.INITIALIZATION) {
            Integer gpusCount = platforms.split(";").sum {
                platforms.tokenize(":").with {
                    (it.size() > 1) ? it[1].split(",").size() : 0
                }
            }
            
            def parallelExecutionType = TestsExecutionType.valueOf(parallelExecutionTypeString)
            options << [projectRepo: projectRepo,
                        projectBranch: projectBranch,
                        usdBranch: usdBranch,
                        testsBranch: testsBranch,
                        updateRefs: updateRefs,
                        enableNotifications: enableNotifications,
                        PRJ_NAME: "RadeonProRenderUSDPlugin",
                        PRJ_ROOT: "rpr-plugins",
                        BUILDER_TAG: 'BuilderHoudini',
                        TESTER_TAG: tester_tag,
                        incrementVersion: incrementVersion,
                        testsPackage: testsPackage,
                        tests: tests.replace(',', ' '),
                        forceBuild: forceBuild,
                        reportName: 'Test_20Report',
                        splitTestsExecution: splitTestsExecution,
                        BUILD_TIMEOUT: 45,
                        TEST_TIMEOUT: 45,
                        buildType: buildType,
                        rebuildUSD: rebuildUSD,
                        houdiniVersion: houdiniVersion,
                        houdini_python3: houdini_python3,
                        width: width,
                        gpusCount: gpusCount,
                        height: height,
                        enableRIFTracing:enableRIFTracing,
                        nodeRetry: [],
                        problemMessageManager: problemMessageManager,
                        platforms: platforms,
                        parallelExecutionType: parallelExecutionType,
                        sendToUMS: sendToUMS,
                        universePlatforms: convertPlatforms(platforms),
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
        if (sendToUMS) {
            options.universeManager.closeBuild(problemMessage, options)
        }
    }
}
