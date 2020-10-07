def executeTestCommand(String osName)
{
    switch(osName)
    {
    case 'Windows':
        bat "..\\Build\\bin\\Release\\UnitTest.exe  --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ..\\${STAGE_NAME}.log  2>&1"
        break;
    case 'OSX':
        sh """
        export LD_LIBRARY_PATH=\$LD_LIBRARY_PATH:../Build/bin
        ../Build/bin/UnitTest           --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../${STAGE_NAME}.log  2>&1
        """
        break;
    default:
        sh """
        export LD_LIBRARY_PATH=\$LD_LIBRARY_PATH:../Build/bin
        ../Build/bin/UnitTest           --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../${STAGE_NAME}.log  2>&1
        """
    }  
}

def executeTests(String osName, String asicName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectURL'])

        outputEnvironmentInfo(osName)
        unstash "app${osName}"

        dir('UnitTest')
        {
            executeTestCommand(osName)
        }                
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
    /*set msbuild=\"C:\\Program Files (x86)\\MSBuild\\14.0\\Bin\\MSBuild.exe\"
    if not exist %msbuild% (
        set msbuild=\"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe\"
    )*/
    bat """
    mkdir Build
    cd Build
    cmake -DCMAKE_BUILD_TYPE=Release -G "Visual Studio 15 2017 Win64" .. >> ..\\${STAGE_NAME}.log 2>&1
    cmake --build . --config Release >> ..\\${STAGE_NAME}.log 2>&1
    """
}

def executeBuildOSX()
{
    sh """
    mkdir Build
    cd Build
    cmake -DCMAKE_BUILD_TYPE=Release .. >> ../${STAGE_NAME}.log 2>&1
    make >> ../${STAGE_NAME}.log 2>&1
    """
}

def executeBuildLinux()
{
    sh """
    mkdir Build
    cd Build
    cmake -DCMAKE_BUILD_TYPE=Release .. >> ../${STAGE_NAME}.log 2>&1
    make >> ../${STAGE_NAME}.log 2>&1
    """
}

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectURL'])

    AUTHOR_NAME = bat (
            script: "git show -s --format=%%an HEAD ",
            returnStdout: true
            ).split('\r\n')[2].trim()

    echo "The last commit was written by ${AUTHOR_NAME}."
    options.AUTHOR_NAME = AUTHOR_NAME

    commitMessage = bat ( script: "git log --format=%%B -n 1", returnStdout: true ).split('\r\n')[2].trim()
    echo "Commit message: ${commitMessage}"
    options.commitMessage = commitMessage
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
       
        stash includes: 'Build/bin/**/*', name: "app${osName}"
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
{
}

def call(String projectBranch = "", String projectURL = 'git@github.com:GPUOpen-LibrariesAndSDKs/RadeonRays_SDK.git',
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;OSX:AMD_RXVEGA;Ubuntu18',
         String PRJ_NAME="RadeonRays_SDK",
         Boolean enableNotifications = true) {

    
    String PRJ_ROOT="rpr-core"
    
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
                            BUILD_TIMEOUT:'10',
                            TEST_TIMEOUT:'10',
                            BUILDER_TAG:'BuilderS',
                            projectURL:projectURL,
                            slackChannel:"${SLACK_BAIKAL_CHANNEL}",
                            slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
                            slackTocken:"${SLACK_BAIKAL_TOCKEN}",
                            retriesForTestStage:1])
}
