def executeTestCommand(String osName)
{

}

def executeTests(String osName, String asicName, Map options)
{

}

def executeBuildWindows()
{
    bat """
    set msbuild=\"C:\\Program Files (x86)\\MSBuild\\14.0\\Bin\\MSBuild.exe\"
    if not exist %msbuild% (
        set msbuild=\"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe\"
    )
    set target=build
    set maxcpucount=/maxcpucount
    set PATH=C:\\Python27\\;%PATH%
    .\\Tools\\premake\\win\\premake5 vs2015    >> ${STAGE_NAME}.log 2>&1
    set solution=Build\\ProRenderGLTF.sln
    %msbuild% /target:%target% %maxcpucount% /property:Configuration=Release;Platform=x64 %parameters% %solution% >> ${STAGE_NAME}.log 2>&1
    """
}

def executeBuildMacOS()
{
     sh """
    chmod +x Tools/premake/macos/premake5
    Tools/premake/macos/premake5 gmake   >> ${STAGE_NAME}.log 2>&1
    cd Build
    make config=release_x64 >> ../${STAGE_NAME}.log 2>&1
    """
}

def executeBuildLinux()
{
    sh """
    chmod +x Tools/premake/linux64/premake5
    Tools/premake/linux64/premake5 gmake   >> ${STAGE_NAME}.log 2>&1
    cd Build
    make config=release_x64 >> ../${STAGE_NAME}.log 2>&1
    """
}

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], 'git@github.com:Radeon-Pro/RadeonProRender-GLTF.git')

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
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:Radeon-Pro/RadeonProRender-GLTF.git')
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows':
            executeBuildWindows();
            break;
        case 'MacOS':
            executeBuildMacOS();
            break;
        default:
            executeBuildLinux();
            break;
        }

       // stash includes: 'Bin/**/*', name: "app${osName}"

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
    try {
        platformList.each()
        {
            String osName = it;
            dir(osName)
            {
                //unstash "app${osName}"
            }
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

def call(String projectBranch = "",
         String platforms = 'Windows;Ubuntu18;MacOS',
         Boolean updateRefs = false, Boolean enableNotifications = true) {

    String PRJ_NAME="RadeonProRender-GLTF"
    String PRJ_ROOT="rpr-core"
    properties([[$class: 'BuildDiscarderProperty', strategy:
                 [$class: 'LogRotator', artifactDaysToKeepStr: '',
                  artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10']]]);

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, this.&executeDeploy,
                           [projectBranch:projectBranch,
                            enableNotifications:enableNotifications,
                            BUILDER_TAG:'BuilderS',
                            BUILD_TIMEOUT:'10',
                            executeBuild:true,
                            executeTests:false,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT])
}
