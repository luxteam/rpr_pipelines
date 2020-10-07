def executeGenTestRefCommand(String osName, Map options)
{

}

def executeTestCommand(String osName, Map options)
{
    dir("Build/unittests")
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                call Release\\RTF_UnitTests.exe --gtest_output=xml:../../${STAGE_NAME}.gtest.xml >> ..\\..\\${STAGE_NAME}.log 2>&1
                """
                break;
            case 'OSX':
                echo "pass"
                break;
            default:
                echo "pass"
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    cleanWS(osName)
    String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
    String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"
    
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        
        outputEnvironmentInfo(osName)
        unstash "app${osName}"
        bat "rmdir /s /q shaders"
        unstash "shaders${osName}"
        
        if(options['updateRefs']) {
            echo "Updating Reference Images"
            executeGenTestRefCommand(osName, options)
            
        } else {
            echo "Execute Tests"
            executeTestCommand(osName, options)
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

def executeBuildWindows(Map options)
{
    bat """
    mkdir Build
    cd Build
    cmake ${options['cmakeKeys']} -G "Visual Studio 15 2017 Win64" .. >> ..\\${STAGE_NAME}.log 2>&1
    cmake --build . --config Release >> ..\\${STAGE_NAME}.log 2>&1
    """
}

def executeBuildOSX(Map options)
{
    sh """
    mkdir Build
    cd Build
    cmake ${options['cmakeKeys']} .. >> ../${STAGE_NAME}.log 2>&1
    make >> ../${STAGE_NAME}.log 2>&1
    """
}

def executeBuildLinux(Map options)
{
    sh """
    mkdir Build
    cd Build
    cmake ${options['cmakeKeys']} .. >> ../${STAGE_NAME}.log 2>&1
    make >> ../${STAGE_NAME}.log 2>&1
    """
}

def executePreBuild(Map options)
{
    dir('RadeonProVulkanWrapper')
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
    }
}

def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows': 
            executeBuildWindows(options); 
            break;
        case 'OSX':
            executeBuildOSX(options);
            break;
        default: 
            executeBuildLinux(options);
        }
        
        stash includes: 'Build/**/*', name: "app${osName}"
        stash includes: 'shaders/**/*/', name: "shaders${osName}"
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        //zip archive: true, dir: 'Build', glob: '', zipFile: "${osName}Build.zip"
    }                        
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    if(options['executeTests'] && testResultList)
    {
        cleanWS()

        dir("BuildsArtifacts")
        {
            platformList.each()
            {
                try {
                    dir(it)
                    {
                        unstash "app${it}"
                    }
                } catch(e) {
                    println(e.toString())
                    println("Can't unstash ${osName} build")
                }
            }
        }
        zip archive: true, dir: 'BuildsArtifacts', glob: '', zipFile: "BuildsArtifacts.zip"
    }
}

def call(String projectBranch = "", 
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI',
         String PRJ_ROOT='rpr-core',
         String PRJ_NAME='RadeonProRTFilters',
         String projectRepo='git@github.com:Radeon-Pro/RadeonProRTFilters.git',
         Boolean updateRefs = false, 
         Boolean enableNotifications = true,
         String cmakeKeys = "") {

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                           [projectBranch:projectBranch,
                            updateRefs:updateRefs, 
                            enableNotifications:enableNotifications,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            projectRepo:projectRepo,
                            BUILDER_TAG:'BuilderS',
                            executeBuild:true,
                            executeTests:true,
                            BUILD_TIMEOUT:'10',
                            TEST_TIMEOUT:'20',
                            slackChannel:"${SLACK_BAIKAL_CHANNEL}",
                            slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
                            slackTocken:"${SLACK_BAIKAL_TOCKEN}",
                            cmakeKeys:cmakeKeys,
                            retriesForTestStage:1])
}
