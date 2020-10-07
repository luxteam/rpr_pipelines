def executeTestCommand(String osName)
{
    dir("Build/bin") {
        Boolean error_occured = false
        switch (osName) {
            case 'Windows':
                ['vk', 'dx', 'native'].each() {
                    outputEnvironmentInfo(osName, "..\\..\\${STAGE_NAME}.${it}")
                    try{
                        bat "Release\\test_${it}.exe --gtest_output=xml:..\\..\\${STAGE_NAME}.${it}.gtest.xml >> ..\\..\\${STAGE_NAME}.${it}.log 2>&1"
                    } catch(e) {
                        println("[INFO] error during ${it} tests")
                        println(e.toString())
                        error_occured = true
                    }
                }
                break;
            case 'OSX':
                ['mtl', 'native'].each() {
                    outputEnvironmentInfo(osName, "../${STAGE_NAME}.${it}")
                    try{
                        sh """
                        cd ..
                        export LD_LIBRARY_PATH=\$PWD/bin:\$LD_LIBRARY_PATH
                        export LD_LIBRARY_PATH=\$PWD/lib:\$LD_LIBRARY_PATH
                        ./test_${it} --gtest_output=xml:../${STAGE_NAME}.${it}.gtest.xml >> ../${STAGE_NAME}.${it}.log 2>&1
                        """
                    } catch(e) {
                        println("[INFO] error during ${it} tests")
                        println(e.toString())
                        error_occured = true
                    }
                }
                break;
            default:
                ['vk', 'native'].each() {
                    outputEnvironmentInfo(osName, "../../${STAGE_NAME}.${it}")
                    try{
                        sh """
                        export LD_LIBRARY_PATH=\$PWD:\$LD_LIBRARY_PATH
                        export LD_LIBRARY_PATH=\$PWD/../lib:\$LD_LIBRARY_PATH
                        ./test_${it} --gtest_output=xml:../../${STAGE_NAME}.${it}.gtest.xml >> ../../${STAGE_NAME}.${it}.log 2>&1
                        """
                    } catch(e) {
                        println("[INFO] error during ${it} tests")
                        println(e.toString())
                        error_occured = true
                    }
                }
        }
        if (error_occured) {
            error("Error has been occured during tests execution")
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectURL'])
        unstash "app${osName}"
        executeTestCommand(osName)
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        junit "*.gtest.xml"
    }
}

def executeBuildWindows()
{
    bat """
    mkdir Build
    cd Build
    cmake -G "Visual Studio 16 2019" -A "x64" -DENABLE_TESTING=ON -DEMBEDDED_KERNELS=ON -DENABLE_VULKAN=ON -DENABLE_DX12=ON -DCMAKE_PREFIX_PATH="C:/Program Files (x86)/spdlog" -DCMAKE_BUILD_TYPE=Release .. >> ..\\${STAGE_NAME}.log 2>&1 
    cmake --build . --config Release >> ..\\${STAGE_NAME}.log 2>&1
    """

    // TODO: on dev request implement second build with -DBUILD_PROPRIETARY=ON
}

def executeBuildOSX()
{
    sh """
    mkdir Build
    cd Build
    cmake -DENABLE_MTL=ON -DENABLE_TESTING=ON -DEMBEDDED_KERNELS=ON -DCMAKE_CXX_FLAGS="-std=c++17" -DCMAKE_MACOSX_RPATH=ON -DCMAKE_BUILD_TYPE=Release .. >> ../${STAGE_NAME}.log 2>&1
    make -j 4 >> ../${STAGE_NAME}.log 2>&1
    """
}

def executeBuildLinux()
{
    sh """
    mkdir Build
    cd Build
    cmake -DENABLE_VULKAN=ON -DENABLE_TESTING=ON -DEMBEDDED_KERNELS=ON -DCMAKE_CXX_FLAGS="-std=gnu++17" -DCMAKE_BUILD_TYPE=Release .. >> ../${STAGE_NAME}.log 2>&1
    make -j 4 >> ../${STAGE_NAME}.log 2>&1
    """
}

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectURL'])

    options.AUTHOR_NAME = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim().replace('\n', '')
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
    options.commitShortSHA = options.commitSHA[0..6]

    println "The last commit was written by ${options.AUTHOR_NAME}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"
    println "Commit shortSHA: ${options.commitShortSHA}"

    if (options.projectBranch){
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }

    currentBuild.description += "<b>Commit author:</b> ${options.AUTHOR_NAME}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"
}

def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectURL'])
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows': 
            executeBuildWindows(); 
            break;
        case 'OSX':
            executeBuildOSX();
            break;
        default: 
            executeBuildLinux();
        }
        stash includes: 'Build/**/*', name: "app${osName}"

        // for manual jon on dev branch
        if (!env.BRANCH_NAME && !env.CHANGE_BRANCH && options.projectBranch == "origin/dev") {
            zip archive: true, dir: 'Build', glob: '', zipFile: "RadeonRays_${osName}.zip"
        }
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{}

def call(String projectBranch = "",
         String projectURL = 'git@github.com:Radeon-Pro/RadeonRays.git',
         String platforms = 'Windows:AMD_RadeonVII;OSX;Ubuntu18:AMD_RadeonVII',
         Boolean enableNotifications = true)
{
    String PRJ_ROOT="rpr-core"
    String PRJ_NAME="RadeonRays"

    properties([[$class: 'BuildDiscarderProperty', strategy: 
                 [$class: 'LogRotator', artifactDaysToKeepStr: '', 
                  artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10']]]);
    
    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, null, 
                           [projectBranch:projectBranch,
                            enableNotifications:enableNotifications,
                            executeBuild:true,
                            executeTests:true,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            BUILD_TIMEOUT:'15',
                            TEST_TIMEOUT:'15',
                            BUILDER_TAG:'BuilderRays',
                            TESTER_TAG:'D3D12',
                            projectURL:projectURL,
                            slackChannel:"${SLACK_BAIKAL_CHANNEL}",
                            slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
                            slackTocken:"${SLACK_BAIKAL_TOCKEN}",
                            retriesForTestStage:1])
}
