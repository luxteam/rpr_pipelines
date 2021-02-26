def deployRML(Map options, String buildType) {
    try {
        if (env.TAG_NAME) {
            dir("rml-deploy") {
                checkOutBranchOrScm("master", "${options.gitlabURLSSH}/servants/rml-deploy.git", true, null, null, false, true, "radeonprorender-gitlab")
                switch (env.CIS_OS) {
                    case "Windows":
                        bat """
                            if exist \"${CIS_OS}\\${buildType}\" RMDIR /S/Q \"${CIS_OS}\\${buildType}\"
                            MD \"${CIS_OS}\\${buildType}\"
                            xcopy \"..\\build-${buildType}\\${buildType}\\MIOpen.dll\" \"${CIS_OS}\\${buildType}"
                            xcopy \"..\\build-${buildType}\\${buildType}\\RadeonML.dll\" \"${CIS_OS}\\${buildType}"
                            xcopy \"..\\build-${buildType}\\${buildType}\\RadeonML-d.dll\" \"${CIS_OS}\\${buildType}"
                            xcopy \"..\\build-${buildType}\\${buildType}\\RadeonML.lib\" \"${CIS_OS}\\${buildType}"
                            xcopy \"..\\build-${buildType}\\${buildType}\\RadeonML-d.lib\" \"${CIS_OS}\\${buildType}"
                            xcopy \"..\\build-${buildType}\\${buildType}\\RadeonML_DirectML.dll\" \"${CIS_OS}\\${buildType}"
                            xcopy \"..\\build-${buildType}\\${buildType}\\RadeonML_DirectML-d.dll\" \"${CIS_OS}\\${buildType}"
                            xcopy \"..\\build-${buildType}\\${buildType}\\RadeonML_MIOpen.dll\" \"${CIS_OS}\\${buildType}"
                            xcopy \"..\\build-${buildType}\\${buildType}\\RadeonML_MIOpen-d.dll\" \"${CIS_OS}\\${buildType}"
                            git config --local user.name "${options.gitUser}"
                            git config --local user.email "${options.gitEmail}"
                            git add --all
                            git commit -m "${CIS_OS} ${buildType} ${env.TAG_NAME}"
                        """
                        break
                    case "OSX":
                        sh """
                            rm -rf \"${CIS_OS}/${buildType}\"
                            mkdir -p \"${CIS_OS}/${buildType}\"
                            cp -R ../build-${buildType}/${buildType}/libRadeonML.*dylib \"${CIS_OS}/${buildType}\"
                            cp -R ../build-${buildType}/${buildType}/libRadeonML_MPS.*dylib \"${CIS_OS}/${buildType}\"
                            git config --local user.name "${options.gitUser}"
                            git config --local user.email "${options.gitEmail}"
                            git add --all
                            git commit -m "${CIS_OS} ${buildType} ${env.TAG_NAME}"
                        """
                        break
                    default:
                        sh """
                            rm -rf \"${CIS_OS}/${buildType}\"
                            mkdir -p \"${CIS_OS}/${buildType}\"
                            cp -R ../build-${buildType}/${buildType}/libMIOpen.so* \"${CIS_OS}/${buildType}\"
                            cp -R ../build-${buildType}/${buildType}/libRadeonML_MIOpen.so* \"${CIS_OS}/${buildType}\"
                            cp -R ../build-${buildType}/${buildType}/libRadeonML.so* \"${CIS_OS}/${buildType}\"
                            git config --local user.name "${options.gitUser}"
                            git config --local user.email "${options.gitEmail}"
                            git add --all
                            git commit -m "${CIS_OS} ${buildType} ${env.TAG_NAME}"
                        """
                }
                // try 3 times to push new commit
                for (int i = 0; i < 3; i++) {
                    try {
                        switch (env.CIS_OS) {
                            case "Windows":
                                bat """
                                    git fetch origin master
                                    git rebase FETCH_HEAD
                                    git push origin HEAD:master
                                """
                                break
                            default:
                                sh """
                                    git fetch origin master
                                    git rebase FETCH_HEAD
                                    git push origin HEAD:master
                                """
                        }
                        break
                    } catch (e1) {
                        println("[ERROR] Failed to deploy ${buildType} for ${CIS_OS} (try #${i})")
                    }
                }
            }
        }
    } catch (e) {
        println("[ERROR] Failed to deploy ${buildType} for ${CIS_OS} at all")
        println(e.toString())
        println(e.getMessage())
    }
}

def executeUnitTestsCommand(String osName, Map options)
{
    switch (osName) {
        case 'Windows':
            bat """
                tests.exe --gtest_output=xml:${STAGE_NAME}.gtest.xml >> ${STAGE_NAME}.UnitTests.log 2>&1
            """
            break
        case 'OSX':
            sh """
                chmod +x tests
                export LD_LIBRARY_PATH=\$PWD:\$LD_LIBRARY_PATH
                ./tests --gtest_output=xml:${STAGE_NAME}.gtest.xml >> ${STAGE_NAME}.UnitTests.log 2>&1
            """
            break
        default:
            sh """
                chmod +x tests
                export LD_LIBRARY_PATH=\$PWD:\$LD_LIBRARY_PATH
                ./tests --gtest_output=xml:${STAGE_NAME}.gtest.xml >> ${STAGE_NAME}.UnitTests.log 2>&1
            """
    }
}

def executeFunctionalTestsCommand(String osName, String asicName, Map options) {
    ws("WS/${options.PRJ_NAME}-TestAssets") {
        checkOutBranchOrScm(options['assetsBranch'], "${options.gitlabURL}/rml/models.git", true, null, null, false, true, "radeonprorender-gitlab", true)
    }
    ws("WS/${options.PRJ_NAME}-FT") {
        checkOutBranchOrScm(options['testsBranch'], "${options.gitlabURL}/rml/ft_engine.git", true, null, null, false, true, "radeonprorender-gitlab", false)
        try {
            dir("rml_release") {
                unstash "app${osName}"
            }
            outputEnvironmentInfo(osName, "${STAGE_NAME}.ft")
            switch (osName) {
                case 'Windows':
                    withEnv(["PATH=C:\\Python38;C:\\Python38\\Scripts;${PATH}"]) {
                        bat """
                        pip install --user -r requirements.txt >> ${STAGE_NAME}.ft.log 2>&1
                        python -V >> ${STAGE_NAME}.ft.log 2>&1
                        python run_tests.py -t ../${options.PRJ_NAME}-TestAssets -e rml_release/test_app.exe -i ../${options.PRJ_NAME}-TestAssets -o results -c true >> ${STAGE_NAME}.ft.log 2>&1
                        rename ft-executor.log ${STAGE_NAME}.engine.log
                        """
                    }
                    break
                default:
                    sh """
                        export LD_LIBRARY_PATH=\$PWD/../${options.PRJ_NAME}-TestAssets:\$LD_LIBRARY_PATH
                        pip3.8 install --user -r requirements.txt >> ${STAGE_NAME}.ft.log 2>&1
                        python3.8 -V >> ${STAGE_NAME}.ft.log 2>&1
                        env >> ${STAGE_NAME}.ft.log 2>&1
                        python3.8 run_tests.py -t ../${options.PRJ_NAME}-TestAssets -e rml_release/test_app -i ../${options.PRJ_NAME}-TestAssets -o results -c true >> ${STAGE_NAME}.ft.log 2>&1
                        mv ft-executor.log ${STAGE_NAME}.engine.log
                    """
            }
        }
        catch(e) {
            println(e.toString())
            throw e
        }
        finally {
            archiveArtifacts "*.log"
            utils.publishReport(this, "${BUILD_URL}", "results", "report.html", "FT ${osName}-${asicName}", "FT ${osName}-${asicName}")
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    cleanWS(osName)
    String error_message = ""

    try {
        outputEnvironmentInfo(osName, "${STAGE_NAME}.UnitTests")
        unstash "app${osName}"

        executeUnitTestsCommand(osName, options)
    } catch (e) {
        println(e.toString())
        println(e.getMessage())
        error_message = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts "*.log"
        junit "*gtest.xml"

        if (env.CHANGE_ID) {
            String context = "[${options.PRJ_NAME}] [TEST] ${osName}-${asicName}"
            String description = error_message ? "Testing finished on UT with error message: ${error_message}" : "UT Testing finished"
            String status = error_message ? "failure" : "success"
            String url = "${env.BUILD_URL}/artifact/${STAGE_NAME}.UnitTests.log"
            pullRequest.createStatus(status, context, description, url)
            options['commitContexts'].remove(context)
        }
    }

    cleanWS(osName)

    if (options.executeFT) {
        try {
            outputEnvironmentInfo(osName, "${STAGE_NAME}.ft")
            executeFunctionalTestsCommand(osName, asicName, options)
        } catch (e) {
            println(e.toString());
            println(e.getMessage());
            error_message = e.getMessage()
            currentBuild.result = "FAILED"
            throw e
        } finally {
            archiveArtifacts "*.log"

            if (env.CHANGE_ID) {
                String context = "[${options.PRJ_NAME}] [TEST] ${osName}-${asicName}"
                String description = error_message ? "Testing finished on FT with error message: ${error_message}" : "UT and FT Testing finished"
                String status = error_message ? "failure" : "success"
                String url = "${env.BUILD_URL}/artifact/${STAGE_NAME}.ft.log"
                pullRequest.createStatus(status, context, description, url)
                options['commitContexts'].remove(context)
            }
        }
    }
}


def executeWindowsBuildCommand(Map options, String buildType){

    outputEnvironmentInfo("Windows", "${STAGE_NAME}_${buildType}")

    bat """
        :: SET DIRECTML_INCLUDE_PATH=".\\DirectML"
        :: SET DIRECTML_LIBRARY_PATH=".\\DirectML"
        mkdir build-${buildType}
        cd build-${buildType}
        cmake ${options.cmakeKeysWin} -DRML_TENSORFLOW_DIR=${WORKSPACE}/third_party/tensorflow -DMIOpen_INCLUDE_DIR=${WORKSPACE}/third_party/miopen -DMIOpen_LIBRARY_DIR=${WORKSPACE}/third_party/miopen .. >> ..\\${STAGE_NAME}_${buildType}.log 2>&1
        set msbuild=\"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe\"
        %msbuild% RadeonML.sln -property:Configuration=${buildType} >> ..\\${STAGE_NAME}_${buildType}.log 2>&1
    """
    
    bat """
        cd build-${buildType}
        xcopy ..\\third_party\\miopen\\MIOpen.dll ${buildType}
        xcopy ..\\third_party\\tensorflow\\windows\\* ${buildType}
        mkdir ${buildType}\\rml
        mkdir ${buildType}\\rml_internal
        xcopy ..\\rml\\include\\rml\\*.h* ${buildType}\\rml
        xcopy ..\\rml\\include\\rml_internal\\*.h* ${buildType}\\rml_internal
    """

    deployRML(options, buildType)

    zip dir: "build-${buildType}\\${buildType}", zipFile: "build-${buildType}\\${CIS_OS}_${buildType}.zip"
    archiveArtifacts "build-${buildType}\\${CIS_OS}_${buildType}.zip"
    
    zip archive: true, dir: "build-${buildType}\\${buildType}", glob: "RadeonML*.lib, RadeonML*.dll, MIOpen.dll, libtensorflow*, test*.exe", zipFile: "${CIS_OS}_${buildType}.zip"

}


def executeBuildWindows(Map options)
{
    bat """
        xcopy ..\\\\RML_thirdparty\\\\MIOpen third_party\\\\miopen /s/y/i
        xcopy ..\\\\RML_thirdparty\\\\tensorflow third_party\\\\tensorflow /s/y/i
    """

    options.cmakeKeysWin ='-G "Visual Studio 15 2017 Win64" -DRML_DIRECTML=ON -DRML_MIOPEN=ON -DRML_TENSORFLOW_CPU=ON -DRML_TENSORFLOW_CUDA=OFF -DRML_MPS=OFF'

    executeWindowsBuildCommand(options, "Release")
    executeWindowsBuildCommand(options, "Debug")

}


def executeOSXBuildCommand(Map options, String buildType){
    
    outputEnvironmentInfo("OSX", "${STAGE_NAME}_${buildType}")

    sh """
        #export DIRECTML_INCLUDE_PATH="./DirectML"
        #export DIRECTML_LIBRARY_PATH="./DirectML"
        mkdir build-${buildType}
        cd build-${buildType}
        cmake -DCMAKE_buildType=${buildType} ${options.cmakeKeysOSX} .. >> ../${STAGE_NAME}_${buildType}.log 2>&1
        make -j 4 >> ../${STAGE_NAME}_${buildType}.log 2>&1
    """
    
    sh """
        cd build-${buildType}
        mv bin ${buildType}
        rm ${buildType}/*.a
        mkdir ./${buildType}/rml
        mkdir ./${buildType}/rml_internal
        cp ../rml/include/rml/*.h* ./${buildType}/rml
        cp ../rml/include/rml_internal/*.h* ./${buildType}/rml_internal

        tar cf ${CIS_OS}_${buildType}.tar ${buildType}
    """

    deployRML(options, buildType)

    archiveArtifacts "build-${buildType}/${CIS_OS}_${buildType}.tar"
    zip archive: true, dir: "build-${buildType}/${buildType}", glob: "libRadeonML*.dylib, test*", zipFile: "${CIS_OS}_${buildType}.zip"
}


def executeBuildOSX(Map options)
{
    sh """
        cp -r ../RML_thirdparty/MIOpen/* ./third_party/miopen
        cp -r ../RML_thirdparty/tensorflow/* ./third_party/tensorflow
    """

    options.cmakeKeysOSX = "-DRML_DIRECTML=OFF -DRML_MIOPEN=OFF -DRML_TENSORFLOW_CPU=ON -DRML_TENSORFLOW_CUDA=OFF -DRML_MPS=ON -DRML_TENSORFLOW_DIR=${WORKSPACE}/third_party/tensorflow -DMIOpen_INCLUDE_DIR=${WORKSPACE}/third_party/miopen -DMIOpen_LIBRARY_DIR=${WORKSPACE}/third_party/miopen"
    
    executeOSXBuildCommand(options, "Release")
    executeOSXBuildCommand(options, "Debug")

}


def executeLinuxBuildCommand(Map options, String buildType){
    
    outputEnvironmentInfo("Linux", "${STAGE_NAME}_${buildType}")

    sh """
        #export DIRECTML_INCLUDE_PATH="./DirectML"
        #export DIRECTML_LIBRARY_PATH="./DirectML"
        mkdir build-${buildType}
        cd build-${buildType}
        cmake -DCMAKE_buildType=${buildType} ${options.cmakeKeysLinux[CIS_OS]} -DRML_TENSORFLOW_DIR=${WORKSPACE}/third_party/tensorflow -DMIOpen_INCLUDE_DIR=${WORKSPACE}/third_party/miopen -DMIOpen_LIBRARY_DIR=${WORKSPACE}/third_party/miopen .. >> ../${STAGE_NAME}_${buildType}.log 2>&1
        make -j 8 >> ../${STAGE_NAME}_${buildType}.log 2>&1
    """
    
    sh """
        cd build-${buildType}
        mv bin ${buildType}
        rm ${buildType}/*.a
        cp -R ../third_party/miopen/libMIOpen.so* ./${buildType}
        cp -R ../third_party/tensorflow/linux/* ./${buildType}
        mkdir ./${buildType}/rml
        mkdir ./${buildType}/rml_internal
        cp ../rml/include/rml/*.h* ./${buildType}/rml
        cp ../rml/include/rml_internal/*.h* ./${buildType}/rml_internal

        tar cf ${CIS_OS}_${buildType}.tar ${buildType}
    """

    deployRML(options, buildType)

    archiveArtifacts "build-${buildType}/${CIS_OS}_${buildType}.tar"
    zip archive: true, dir: "build-${buildType}/${buildType}", glob: "libRadeonML*.so, libMIOpen*.so, libtensorflow*.so, test*", zipFile: "${CIS_OS}_${buildType}.zip"
}


def executeBuildLinux(Map options)
{
    sh """
        cp -r ../RML_thirdparty/MIOpen/* ./third_party/miopen
        cp -r ../RML_thirdparty/tensorflow/* ./third_party/tensorflow
    """

    options.cmakeKeysLinux = [
        'Ubuntu18': '-DRML_DIRECTML=OFF -DRML_MIOPEN=ON -DRML_TENSORFLOW_CPU=ON -DRML_TENSORFLOW_CUDA=ON -DRML_MPS=OFF',
        'CentOS7': '-DRML_DIRECTML=OFF -DRML_MIOPEN=ON -DRML_TENSORFLOW_CPU=ON -DRML_TENSORFLOW_CUDA=OFF -DRML_MPS=OFF'
    ]

    executeLinuxBuildCommand(options, "Release")
    executeLinuxBuildCommand(options, "Debug")

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

    if (options.projectBranch) {
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }

    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    def commitContexts = []
    // set pending status for all
    if (env.CHANGE_ID) {

        options['platforms'].split(';').each()
                { platform ->
                    List tokens = platform.tokenize(':')
                    String osName = tokens.get(0)
                    // Statuses for builds
                    String context = "[${options.PRJ_NAME}] [BUILD] ${osName}"
                    commitContexts << context
                    pullRequest.createStatus("pending", context, "Scheduled", "${env.JOB_URL}")
                    if (tokens.size() > 1) {
                        gpuNames = tokens.get(1)
                        gpuNames.split(',').each()
                                { gpuName ->
                                    // Statuses for tests
                                    context = "[${options.PRJ_NAME}] [TEST] ${osName}-${gpuName}"
                                    commitContexts << context
                                    pullRequest.createStatus("pending", context, "Scheduled", "${env.JOB_URL}")
                                }
                    }
                }
        options['commitContexts'] = commitContexts
    }
}


def executeBuild(String osName, Map options)
{
    String error_message = ""
    String context = "[${options.PRJ_NAME}] [BUILD] ${osName}"

    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

        downloadFiles("/volume1/CIS/rpr-ml/MIOpen/${osName}/*", "../RML_thirdparty/MIOpen")
        downloadFiles("/volume1/CIS/rpr-ml/tensorflow/*", "../RML_thirdparty/tensorflow")
        downloadFiles("/volume1/CIS/rpr-ml/DirectML/*", "./DirectML")

        withEnv(["CIS_OS=${osName}"]) {
            switch (osName) {
                case 'Windows':
                    executeBuildWindows(options)
                    break
                case 'OSX':
                    executeBuildOSX(options)
                    break
                default:
                    executeBuildLinux(options)
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
        if (env.CHANGE_ID) {
            String status = error_message ? "failure" : "success"
            pullRequest.createStatus("${status}", context, "Build finished as '${status}'", "${env.BUILD_URL}/artifact/${STAGE_NAME}.log")
            options['commitContexts'].remove(context)
        }
        archiveArtifacts "*.log"
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    // set error statuses for PR, except if current build has been superseded by new execution
    if (env.CHANGE_ID && !currentBuild.nextBuild) {
        // if jobs was aborted or crushed remove pending status for unfinished stages
        options['commitContexts'].each() {
            pullRequest.createStatus("error", it, "Build has been terminated unexpectedly", "${env.BUILD_URL}")
        }
    }
}

def call(String projectBranch = "",
         String testsBranch = "master",
         String assestsBranch = "master",
         String platforms = 'Windows:AMD_RadeonVII,NVIDIA_RTX2080;Ubuntu18:AMD_RadeonVII,NVIDIA_RTX2070;CentOS7;OSX:AMD_RXVEGA',
         String projectRepo='git@github.com:Radeon-Pro/RadeonML.git',
         Boolean enableNotifications = true,
         Boolean executeFT = true)
{
    String PRJ_ROOT='rpr-ml'
    String PRJ_NAME='RadeonML'

    def gitlabURL, gitUser, gitEmail, gitlabURLSSH
    withCredentials([string(credentialsId: 'gitlabURL', variable: 'GITLAB_URL'), string(credentialsId: 'gitlabURLSSH', variable: 'GITLAB_URL_SSH'),
        string(credentialsId: 'gitUser', variable: 'GIT_USER'), string(credentialsId: 'gitEmail', variable: 'GIT_EMAIL')]) 
    {
        gitlabURL = GITLAB_URL
        gitlabURLSSH = GITLAB_URL_SSH
        gitUser = GIT_USER
        gitEmail = GIT_EMAIL
    }

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
            [platforms:platforms,
             projectBranch:projectBranch,
             testsBranch:testsBranch,
             assetsBranch:assestsBranch,
             enableNotifications:enableNotifications,
             PRJ_NAME:PRJ_NAME,
             PRJ_ROOT:PRJ_ROOT,
             projectRepo:projectRepo,
             BUILDER_TAG:'BuilderML',
             TESTER_TAG:'ML',
             BUILD_TIMEOUT:'45',
             TEST_TIMEOUT:'40',
             executeBuild:true,
             executeTests:true,
             executeFT:executeFT,
             slackChannel:"${SLACK_ML_CHANNEL}",
             slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
             slackTocken:"slack-ml-channel",
             retriesForTestStage:1,
             gitlabURL:gitlabURL,
             gitlabURLSSH:gitlabURLSSH,
             gitUser:gitUser,
             gitEmail:gitEmail])
}
