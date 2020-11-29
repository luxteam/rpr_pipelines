def executeGenTestRefCommand(String osName)
{
    switch(osName)
    {
    case 'Windows':
        bat """
        echo 'sample image' > .\\ReferenceImages\\sample_image.txt
        """
        break;
    case 'MacOS':
        sh """
        echo 'sample image' > ./ReferenceImages/sample_image.txt
        """
        break;
    default:
        sh """
        echo 'sample image' > ./ReferenceImages/sample_image.txt
        """
    }
}

def executeTestCommand(String osName)
{
    switch(osName)
    {
    case 'Windows':
        bat """
        echo 'sample image' > .\\OutputImages\\sample_image.txt
        """
        break;
    case 'MacOS':
        sh """
        echo 'sample image' > ./OutputImages/sample_image.txt
        """
        break;
    default:
        sh """
        echo 'sample image' > ./OutputImages/sample_image.txt
        """
    }
}

def executeTests(String osName, String asicName, Map options)
{
    String PRJ_PATH="builds/sample-projects/MultiplatformSampleProject"
    String REF_PATH="${PRJ_PATH}/ReferenceImages/${asicName}-${osName}"
    String JOB_PATH="${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}/${asicName}-${osName}".replace('%2F', '_')

    try {
        //checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/MultiplatformSampleProject.git')

        outputEnvironmentInfo(osName)
        
        //unstash "app${osName}"

        dir('Tests')
        {
            if(options['updateRefs'])
            {
                executeGenTestRefCommand(osName)
                sendFiles(osName, './ReferenceImages/*.*', REF_PATH)

            }
            else
            {
                receiveFiles(osName, "${REF_PATH}/*", './ReferenceImages/')
                executeTestCommand(osName)
            }
        }                    
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        println(e.getStackTrace());

        dir('Tests')
        {
            if(options['updateRefs'])
            {
                executeGenTestRefCommand(osName)
                sendFiles(osName, './ReferenceImages/*.*', REF_PATH)

            }
            else
            {
                receiveFiles(osName, "${REF_PATH}/*", './ReferenceImages/')
                executeTestCommand(osName)
            }
        }
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        sendFiles(osName, '*.log', "${PRJ_PATH}")
    }
}

def executeBuildWindows()
{
    bat """
    HOSTNAME > ${STAGE_NAME}.log

    """
}

def executeBuildMacOS()
{
    sh """
    uname -a > ${STAGE_NAME}.log

    """
}

def executeBuildLinux()
{
    sh """
    uname -a > ${STAGE_NAME}.log

    """
}
def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/MultiplatformSampleProject.git')
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
        }
        
        //stash includes: 'Bin/**/*', name: "app${osName}"
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        sendFiles(osName, '*.log', "${PRJ_PATH}")
        sendFiles(osName, '*.gtest.xml', "${PRJ_PATH}")
    }                        

}

def executeDeploy(Map options)
{
}

def call(String projectBranch = "", 
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;MacOS:Intel_Iris;Ubuntu;AMD_RX460', 
         Boolean updateRefs = false, Boolean enableNotifications = true) {
    
    multiplatform_pipeline(platforms, this.&executeBuild, this.&executeTests, null, 
                           [projectBranch:projectBranch,
                           updateRefs:updateRefs, 
                           enableNotifications:enableNotifications])
}
