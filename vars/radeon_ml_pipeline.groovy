def executeUnitTestsCommand(String osName, Map options)
{
    switch (osName) {
        case 'Windows':
            bat """
                tests.exe --gtest_output=xml:${STAGE_NAME}.gtest.xml >> ${STAGE_NAME}.UnitTests.log 2>&1
            """
            break;
        case 'OSX':
            sh """
                chmod +x tests
                export LD_LIBRARY_PATH=\$PWD:\$LD_LIBRARY_PATH
                ./tests --gtest_output=xml:${STAGE_NAME}.gtest.xml >> ${STAGE_NAME}.UnitTests.log 2>&1
            """
            break;
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
        checkOutBranchOrScm(options['assetsBranch'], "https://gitlab.cts.luxoft.com/rml/models.git", true, null, null, false, true, "radeonprorender-gitlab", true)
    }
    ws("WS/${options.PRJ_NAME}-FT") {
        checkOutBranchOrScm(options['testsBranch'], "https://gitlab.cts.luxoft.com/rml/ft_engine.git", true, null, null, false, true, "radeonprorender-gitlab", false)
        try {
            dir("rml_release") {
                unstash "app${osName}"
            }
            outputEnvironmentInfo(osName, "${STAGE_NAME}.ft")
            switch (osName) {
                case 'Windows':
                    withEnv(["PATH=C:\\Python38;C:\\Python38\\Scripts;${PATH}"]) {
                        bat """
                        pip install -r requirements.txt >> ${STAGE_NAME}.ft.log 2>&1
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
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        error_message = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
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

    if(options.executeFT) {
        try {
            outputEnvironmentInfo(osName, "${STAGE_NAME}.ft")
            executeFunctionalTestsCommand(osName, asicName, options)
        }
        catch (e) {
            println(e.toString());
            println(e.getMessage());
            error_message = e.getMessage()
            currentBuild.result = "FAILED"
            throw e
        }
        finally {
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


def executeBuildWindows(Map options)
{
    bat """
    xcopy ..\\\\RML_thirdparty\\\\MIOpen third_party\\\\miopen /s/y/i
    xcopy ..\\\\RML_thirdparty\\\\tensorflow third_party\\\\tensorflow /s/y/i
    """

    cmakeKeysWin ='-G "Visual Studio 15 2017 Win64" -DRML_DIRECTML=ON -DRML_MIOPEN=ON -DRML_TENSORFLOW_CPU=ON -DRML_TENSORFLOW_CUDA=OFF -DRML_MPS=OFF'

    bat """
        mkdir build
        cd build
        cmake ${cmakeKeysWin} -DRML_TENSORFLOW_DIR=${WORKSPACE}/third_party/tensorflow -DMIOpen_INCLUDE_DIR=${WORKSPACE}/third_party/miopen -DMIOpen_LIBRARY_DIR=${WORKSPACE}/third_party/miopen .. >> ..\\${STAGE_NAME}.log 2>&1
        set msbuild=\"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe\"
        %msbuild% RadeonML.sln -property:Configuration=Release >> ..\\${STAGE_NAME}.log 2>&1
    """
    
    bat """
        cd build
        xcopy ..\\third_party\\miopen\\MIOpen.dll .\\Release\\MIOpen.dll*
        xcopy ..\\third_party\\tensorflow\\windows\\* .\\Release
        mkdir .\\Release\\rml
        mkdir .\\Release\\rml\\rml
        mkdir .\\Release\\rml\\rml_internal
        xcopy ..\\rml\\include\\rml\\*.h* .\\Release\\rml\\rml
        xcopy ..\\rml\\include\\rml_internal\\*.h* .\\Release\\rml\\rml_internal
    """
    zip archive: true, dir: 'build/Release', glob: 'RadeonML*.lib, RadeonML*.dll, MIOpen.dll, libtensorflow*, test*.exe', zipFile: "${CIS_OS}_Release.zip"
}

def executeBuildOSX(Map options)
{
    sh """
        cp -r ../RML_thirdparty/MIOpen/* ./third_party/miopen
        cp -r ../RML_thirdparty/tensorflow/* ./third_party/tensorflow
    """

    cmakeKeysOSX = "-DRML_DIRECTML=OFF -DRML_MIOPEN=OFF -DRML_TENSORFLOW_CPU=ON -DRML_TENSORFLOW_CUDA=OFF -DRML_MPS=ON -DRML_TENSORFLOW_DIR=${WORKSPACE}/third_party/tensorflow -DMIOpen_INCLUDE_DIR=${WORKSPACE}/third_party/miopen -DMIOpen_LIBRARY_DIR=${WORKSPACE}/third_party/miopen"
    sh """
        mkdir build
        cd build
        cmake ${cmakeKeysOSX} .. >> ../${STAGE_NAME}.log 2>&1
        make -j 4 >> ../${STAGE_NAME}.log 2>&1
    """
    
    sh """
        cd build
        mv bin Release
        mkdir ./Release/rml
        mkdir ./Release/rml/rml
        mkdir ./Release/rml/rml_internal
        cp ../rml/include/rml/*.h* ./Release/rml/rml
        cp ../rml/include/rml_internal/*.h* ./Release/rml/rml_internal

        tar cf ${CIS_OS}_Release.tar Release
    """

    archiveArtifacts "build/${CIS_OS}_Release.tar"
    zip archive: true, dir: 'build/Release', glob: 'libRadeonML*.dylib, test*', zipFile: "${CIS_OS}_Release.zip"
}

def executeBuildLinux(Map options)
{
    sh """
        cp -r ../RML_thirdparty/MIOpen/* ./third_party/miopen
        cp -r ../RML_thirdparty/tensorflow/* ./third_party/tensorflow
    """
    cmakeKeysLinux = [
        'Ubuntu18': '-DRML_DIRECTML=OFF -DRML_MIOPEN=ON -DRML_TENSORFLOW_CPU=ON -DRML_TENSORFLOW_CUDA=ON -DRML_MPS=OFF',
        'CentOS7_6': '-DRML_DIRECTML=OFF -DRML_MIOPEN=ON -DRML_TENSORFLOW_CPU=ON -DRML_TENSORFLOW_CUDA=OFF -DRML_MPS=OFF'
    ]

    sh """
        mkdir build
        cd build
        cmake ${cmakeKeysLinux[CIS_OS]} -DRML_TENSORFLOW_DIR=${WORKSPACE}/third_party/tensorflow -DMIOpen_INCLUDE_DIR=${WORKSPACE}/third_party/miopen -DMIOpen_LIBRARY_DIR=${WORKSPACE}/third_party/miopen .. >> ../${STAGE_NAME}.log 2>&1
        make -j 4 >> ../${STAGE_NAME}.log 2>&1
    """

    sh """
        cd build
        mv bin Release
        cp ../third_party/miopen/libMIOpen.so* ./Release
        cp ../third_party/tensorflow/linux/* ./Release

        mkdir ./Release/rml
        mkdir ./Release/rml/rml
        mkdir ./Release/rml/rml_internal
        cp ../rml/include/rml/*.h* ./Release/rml/rml
        cp ../rml/include/rml_internal/*.h* ./Release/rml/rml_internal

        tar cf ${CIS_OS}_Release.tar Release
    """
    zip archive: true, dir: 'build/Release', glob: 'libRadeonML*.so, libMIOpen*.so, libtensorflow*.so, test*', zipFile: "${CIS_OS}_Release.zip"
    archiveArtifacts "build/${CIS_OS}_Release.tar"
}

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

    AUTHOR_NAME = bat (
            script: "git show -s --format=%%an HEAD ",
            returnStdout: true
    ).split('\r\n')[2].trim()

    echo "The last commit was written by ${AUTHOR_NAME}."
    options.AUTHOR_NAME = AUTHOR_NAME

    commitMessage = bat ( script: "git log --format=%%B -n 1", returnStdout: true ).split('\r\n')[2].trim()
    echo "Commit message: ${commitMessage}"
    options.commitMessage = commitMessage

    def commitContexts = []
    // set pending status for all
    if(env.CHANGE_ID) {

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

    try
    {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

        receiveFiles("rpr-ml/MIOpen/*", "../RML_thirdparty/MIOpen")
        receiveFiles("rpr-ml/tensorflow/*", "../RML_thirdparty/tensorflow")

        outputEnvironmentInfo(osName)

        withEnv(["CIS_OS=${osName}"]) {
            switch (osName) {
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

        dir('build/Release') {
            stash includes: '*', name: "app${osName}"
        }
    }
    catch (e)
    {
        println(e.getMessage())
        error_message = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    }
    finally
    {
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
         String platforms = 'Windows:AMD_RadeonVII,NVIDIA_RTX2080;Ubuntu18:AMD_RadeonVII,NVIDIA_RTX2070;CentOS7_6;OSX:AMD_RXVEGA',
         String projectRepo='git@github.com:Radeon-Pro/RadeonML.git',
         Boolean enableNotifications = true,
         Boolean executeFT = true)
{
    String PRJ_ROOT='rpr-ml'
    String PRJ_NAME='RadeonML'

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
             BUILD_TIMEOUT:'15',
             TEST_TIMEOUT:'40',
             executeBuild:true,
             executeTests:true,
             executeFT:executeFT,
             slackChannel:"${SLACK_ML_CHANNEL}",
             slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
             slackTocken:"slack-ml-channel",
             retriesForTestStage:1])
}
