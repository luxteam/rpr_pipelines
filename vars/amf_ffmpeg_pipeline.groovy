def executeGenTestRefCommand(String osName, Map options)
{
}

def executeTestCommand(String osName, Map options)
{
    switch(osName)
    {
    case 'Windows':
        dir('scripts')
        {
            bat """
            run.bat >> ../${STAGE_NAME}.log  2>&1
            """
        }
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
    try {

        checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_core.git')

        // update assets
        if(isUnix())
        {
            sh """
            ${CIS_TOOLS}/receiveFiles.sh ${options.PRJ_ROOT}/${options.PRJ_NAME}/CoreAssets/* ${CIS_TOOLS}/../TestResources/CoreAssets
            """
        }
        else
        {
            bat """
            %CIS_TOOLS%\\receiveFiles.bat ${options.PRJ_ROOT}/${options.PRJ_NAME}/CoreAssets/* /mnt/c/TestResources/CoreAssets
            """
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        outputEnvironmentInfo(osName)

        if(options['updateRefs'])
        {
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else
        {
            receiveFiles("${REF_PATH_PROFILE}/*", './Work/Baseline/')
            executeTestCommand(osName, options)
        }
    }
    catch (e)
    {
        println(e.toString());
        println(e.getMessage());
        currentBuild.result = "FAILED"
        throw e
    }
    finally
    {}
}

def executeBuildWindows(Map options)
{

}

def executeBuildMacOS(Map options)
{
}

def executeBuildLinux(Map options)
{

}

def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options.projectBranch, options.projectRepo)

        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows':
            executeBuildWindows(options);
            break;
        case 'MacOS':
            executeBuildMacOS(options);
            break;
        default:
            executeBuildLinux(options);
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

def executePreBuild(Map options)
{
}

def executeDeploy(Map options, List platformList, List testResultList)
{
}


def call(String projectRepo = "git@github.com:amfdev/FFmpeg.git",
         String projectBranch = "",
         String platforms = "Windows;Ubuntu18",
         String PRJ_ROOT = "amfdev",
         String PRJ_NAME = "FFmpeg",
         Boolean updateRefs = false,
         Boolean enableNotifications = false) {
    try
    {

        multiplatform_pipeline(platforms, null, this.&executeBuild, this.&executeTests, null,
                               [projectRepo:projectRepo,
                                projectBranch:projectBranch,
                                updateRefs:updateRefs,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                BUILDER_TAG:'BuilderU',
                                BUILD_TIMEOUT:240,
                                executeBuild:true,
                                executeTests:true])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
