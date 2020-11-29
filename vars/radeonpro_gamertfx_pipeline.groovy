
def executeTestCommand(String osName, Map options)
{
    switch(osName)
    {
    case 'Windows':
        dir('WindowsNoEditor')
        {
            bat "RunAndProfile.bat >> ..\\${options.stageName}.log 2>&1"
        }
        break;
    default:
        echo "empty"
    }
}

def executeTests(String osName, String asicName, Map options)
{
    cleanWS(osName)
    
    try {
        outputEnvironmentInfo(osName, options.stageName)
        unstash "app${osName}"
        
        executeTestCommand(osName, options)

        dir('WindowsNoEditor/ShooterGame/Saved/Profiling/RTE')
        {
            bat """FOR /F "tokens=* USEBACKQ" %%F IN (`DIR /B /A:D "%CD%"`) DO (rename "%%F" ${options.stageName})"""
            archiveArtifacts '**/*'
        }
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally {
        archiveArtifacts "*.log"
    }
}

def executeBuildWindows(Map options)
{
    bat """
    %CIS_TOOLS%\\receiveFilesSync.bat ${options.PRJ_ROOT}/${options.PRJ_NAME}/UnrealAssets/ /mnt/c/TestResources/UnrealAssets
    """
    
    bat "xcopy C:\\TestResources\\UnrealAssets\\ShooterGame ShooterGame /s/y/i"
    
    dir("Engine\\Source\\ThirdParty\\RTEffects") {
        bat "build.bat >> ../../../../${STAGE_NAME}.log 2>&1"
    }

    bat "Setup.bat >> ${STAGE_NAME}.log 2>&1"
    
    bat "MakePackage.bat >> ${STAGE_NAME}.log 2>&1"
}

def executeBuildMacOS(Map options)
{

}

def executeBuildLinux(Map options)
{

}

def executePreBuild(Map options)
{
    dir('RadeonProGameRTFX')
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
        case 'MacOS':
            executeBuildMacOS(options);
            break;
        default: 
            executeBuildLinux(options);
        }

        dir("Package")
        {
            stash includes: 'WindowsNoEditor/**', name: "app${osName}"
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
{
    if(options['executeTests'] && testResultList)
    {
        dir("summaryTestResults")
        {
            testResultList.each()
            {
                dir("$it".replace("testResult-", ""))
                {
                    try
                    {
                        unstash "$it"
                    }
                    catch(e)
                    {
                        echo "Can't unstash ${it}"
                        println(e.toString())
                        println(e.getMessage())
                    }
                }
            }
        }
    }
}

def call(String projectBranch = "",
         String platforms = 'Windows:AMD_RXVEGA,NVIDIA_GF1080TI',
         Boolean updateRefs = false,
         Boolean enableNotifications = true) {

    String PRJ_ROOT='rpr-core'
    String PRJ_NAME='RadeonGameRTFX'
    String projectRepo='git@github.com:Radeon-Pro/RadeonGameRTFX.git'

    multiplatform_pipeline(platforms, null, this.&executeBuild, this.&executeTests, null,
                           [projectBranch:projectBranch,
                            updateRefs:updateRefs, 
                            enableNotifications:enableNotifications,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            projectRepo:projectRepo,
                            BUILDER_TAG:'BuilderU',
                            executeBuild:true,
                            executeTests:true,
                            BUILD_TIMEOUT:90])
}
