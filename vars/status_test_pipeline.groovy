def setCommitStatus(String sha, String context, String repository, String backref, String message, String status)
{
    step([$class: 'GitHubCommitStatusSetter',
        commitShaSource: [$class: 'ManuallyEnteredShaSource', sha: "${sha}"],
        contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: "${context}"],
        reposSource: [$class: 'ManuallyEnteredRepositorySource', url: "${repository}"],
        statusBackrefSource: [$class: 'ManuallyEnteredBackrefSource', backref: "${backref}"],
        statusResultSource: [$class: 'ConditionalStatusResultSource',results: [[$class: 'AnyBuildResult', message: "${message}", state: "${status}"]]]
        ])
}


def executeGenTestRefCommand(String osName)
{
    switch(osName)
    {
    case 'Windows':
        bat """
        echo 'sample image' > .\\ReferenceImages\\sample_image.txt
        """
        break;
    case 'OSX':
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

def executeTestCommand(String osName, Map options)
{
    switch(osName)
    {
    case 'Windows':
        bat """
        echo 'sample image' > ${STAGE_NAME}.${options.RENDER_QUALITY}.log
        """
        break;
    case 'OSX':
        sh """
        echo 'sample image' > ${STAGE_NAME}.${options.RENDER_QUALITY}.log
        """
        break;
    default:
        sh """
        echo 'sample image' > ${STAGE_NAME}.${options.RENDER_QUALITY}.log
        """
    }
}

def executeTestsCustomQuality(String osName, String asicName, Map options)
{
    cleanWs()
    executeTestCommand(osName, options)
}

def executeTests(String osName, String asicName, Map options)
{
    options['testsQuality'].split(",").each() {
        options['RENDER_QUALITY'] = "${it}"
        String status = "success"
        try {
            executeTestsCustomQuality(osName, asicName, options)
        } catch(e) {
            println(e.toString());
            println(e.getMessage());
            status = "failure"
            println("Exception during [${options.RENDER_QUALITY}] quality tests execution")
        }
        finally {
            archiveArtifacts "*.log"
            if (env.CHANGE_ID)
            {
                String context = "[TEST] ${osName}-${asicName}-${it}"
                
                pullRequest.createStatus("${status}", "${context}",
                    "Testing finished as '${status}', with error message: '${e.getMessage()}'",
                    "${env.BUILD_URL}/artifact/${STAGE_NAME}.${options.RENDER_QUALITY}.log")
                options['commitContexts'].remove(context)
            }
        }
    }
}

def executeBuildWindows()
{
    bat """
    HOSTNAME > ${STAGE_NAME}.log

    """
}

def executeBuildOSX()
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

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

    // set pending status for all
    if(env.CHANGE_ID)
    {
        def commitContexts = []
        options['platforms'].split(';').each()
        { platform ->
            List tokens = platform.tokenize(':')
            String osName = tokens.get(0)
            // Statuses for builds
            String context = "[BUILD] ${osName}"
            commitContexts << context
            pullRequest.createStatus("pending", context, "Scheduled", "${env.JOB_URL}")
            if (tokens.size() > 1)
            {
                gpuNames = tokens.get(1)
                gpuNames.split(',').each()
                { gpuName ->
                    options['testsQuality'].split(",").each()
                    { testQuality ->
                        // Statuses for tests
                        context = "[TEST] ${osName}-${gpuName}-${testQuality}"
                        commitContexts << context
                        pullRequest.createStatus("pending", context, "Scheduled", "${env.JOB_URL}")
                    }
                }
            }
        }
        options['commitContexts'] = commitContexts
        println(options['commitContexts'])
    }
}

def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], 'https://github.com/luxteam/MultiplatformSampleProject.git')
        outputEnvironmentInfo(osName)

        if (env.CHANGE_ID)
        {
            pullRequest.createStatus("pending",
                "[BUILD] ${osName}", "Checkout has been finished. Trying to build...",
                "${env.BUILD_URL}/artifact/${STAGE_NAME}.log")
        }

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
        
        //stash includes: 'Bin/**/*', name: "app${osName}"
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        if (env.CHANGE_ID)
        {
            String status = currentBuild.result ? "failure" : "success"
            pullRequest.createStatus("${status}",
                "[BUILD] ${osName}", "Build finished as '${status}'",
                "${env.BUILD_URL}/artifact/${STAGE_NAME}.log")

            options['commitContexts'].remove("[BUILD] ${osName}")
        }
    }                        

}

def executeDeploy(Map options, List platformList, List testResultList)
{
    if (env.CHANGE_ID)
    {
        // if jobs was aborted or crushed remove pending status for unfinished stages
        options['commitContexts'].each()
        {
            pullRequest.createStatus("error", it, "Build has been terminated unexpectedly")
        }

        // TODO: parse test results from junit xmls
        // TODO: when html report will be finished - add link to comment message
        def comment = pullRequest.comment("Checks for ${pullRequest.head} has been finished as ${currentBuild.result}")
    }
}

def call(String projectBranch = "", 
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;OSX:RadeonPro560;Ubuntu:AMD_WX7100', 
         String testsQuality = "low,medium",
         Boolean updateRefs = false,
         Boolean enableNotifications = false) {
    
    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                           [projectBranch:projectBranch,
                           updateRefs:updateRefs, 
                           enableNotifications:enableNotifications,
                           platforms:platforms,
                           executeTests:true,
                           executeBuild:true,
                           testsQuality:testsQuality])
}
