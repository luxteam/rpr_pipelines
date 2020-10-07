
def executeGenTestRefCommand(String osName)
{
    switch(osName)
    {
    case 'Windows':
        bat """
        """
        break;
    case 'OSX':
        sh """
        """
        break;
    default:
        sh """
        """
    }
}

def executeTestCommand(String osName)
{
    switch(osName)
    {
    case 'Windows':
        bat """
        """
        break;
    case 'OSX':
        sh """
        """
        break;
    default:
        sh """
        """
    }
}

def executeTests(String osName, String asicName, Map options)
{
    String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
    String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

        outputEnvironmentInfo(osName)
        unstash "app${osName}"
        
        dir('Tests')
        {
            if(options['updateRefs'])
            {
                executeGenTestRefCommand(osName)
                sendFiles('./ReferenceImages/*.*', "${REF_PATH_PROFILE}")
            }
            else
            {
                receiveFiles("${REF_PATH_PROFILE}/*", './ReferenceImages/')
                executeTestCommand(osName)
            }
        }                    
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        println(e.getStackTrace());    
        
        dir('BaikalTest')
        {
            sendFiles('./ReferenceImages/*.*', "${JOB_PATH_PROFILE}/ReferenceImages")
            sendFiles('./OutputImages/*.*', "${JOB_PATH_PROFILE}/OutputImages")
        }
        currentBuild.result = "FAILED"
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
    HOSTNAME > ${STAGE_NAME}.log
    """
    bat """
    set msbuild="C:\\Program Files (x86)\\MSBuild\\14.0\\Bin\\MSBuild.exe"
    set target=build
    set maxcpucount=/maxcpucount 
    set solution=./amf/public/proj/vs2015/AmfMediaCommon.sln
    %msbuild% /target:%target% %maxcpucount% /property:Configuration=Release;Platform=x64 %parameters% %solution% >> ${STAGE_NAME}.log 2>&1
    """

    bat """
    set msbuild="C:\\Program Files (x86)\\MSBuild\\14.0\\Bin\\MSBuild.exe"
    set target=build
    set maxcpucount=/maxcpucount 
    set solution=./amf/public/samples/CPPSamples_vs2015.sln
    %msbuild% /target:%target% %maxcpucount% /property:Configuration=Release;Platform=x64 %parameters% %solution% >> ${STAGE_NAME}.log 2>&1
    """
}

def executeBuildOSX()
{
    sh """
    uname -a > ${STAGE_NAME}.log
    echo Not supported
    exit -1
    """
}

def executeBuildLinux()
{
    sh """
    uname -a > ${STAGE_NAME}.log
    echo Not supported
    exit -1
    """
}
def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
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
        
        stash includes: 'amf/bin/**/*', excludes: 'bin/obj', name: "app${osName}"
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
    }
}

def executeDeploy(Map options)
{
}

def call(String projectBranch = "", 
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100', 
         String PRJ_ROOT='amf-core',
         String PRJ_NAME='AMF',
         String projectRepo='git@github.com:GPUOpen-LibrariesAndSDKs/AMF.git',
         Boolean updateRefs = false, 
         Boolean enableNotifications = true) {

    multiplatform_pipeline(platforms, this.&executeBuild, this.&executeTests, null, 
                           [projectBranch:projectBranch,
                            updateRefs:updateRefs, 
                            enableNotifications:false,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            projectRepo:projectRepo,
                            BUILDER_TAG:'BuilderS',
                            retriesForTestStage:1
                           ])
}
