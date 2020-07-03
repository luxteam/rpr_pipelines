import groovy.json.JsonOutput
import radeonpro_vulkanwrapper_pipeline;

def executeTests(String osName, String asicName, Map options)
{}

//TODO
def executeBuildWindows(Map options)
{
}

def executeBuild(String osName, Map options)
{
    dir("dependencies") {
        dir("RadeonProVulkanWrapper") {
            Map vulkanOptions = options.clone()
            options.projectBranch = "master"
            options.projectRepo = "git@github.com:Radeon-Pro/RadeonProVulkanWrapper.git"
            radeonpro_vulkanwrapper_pipeline.executeBuild(osName, vulkanOptions)
        }
        dir("RadeonImageFilter") {
            checkOutBranchOrScm("master", "git@github.com:GPUOpen-LibrariesAndSDKs/RadeonImageFilter.git")
        }
        dir("RadeonProRenderSDK") {
            checkOutBranchOrScm("master", "git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderSDK.git")
        }
    }

    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        outputEnvironmentInfo(osName)

        switch(osName) {
            case 'Windows':
                executeBuildWindows(options);
                break;
            case 'OSX':
                println "OS isn't supported."
                break;
            default:
                println "OS isn't supported."
        }
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectRepo'], true)

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim().replace('\n', '')
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"

    if (options.projectBranch) {
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }
    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    if (env.CHANGE_URL) {
        echo "branch was detected as Pull Request"
        options['isPR'] = true
        options.testsPackage = "PR"
    }
    else if(env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.testsPackage = "master"
    }
    else if(env.BRANCH_NAME) {
        options.testsPackage = "smoke"
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{}

def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_RadeonVII;Ubuntu18:AMD_RadeonVII',
         Boolean updateRefs = false,
         Boolean enableNotifications = true)
{
    def nodeRetry = []
    String PRJ_ROOT='rpr-core'
    String PRJ_NAME='USDViewer'
    String projectRepo='git@github.com:Radeon-Pro/RadeonProViewer.git'

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, null,
                           [projectBranch:projectBranch,
                            testsBranch:testsBranch,
                            updateRefs:updateRefs,
                            enableNotifications:enableNotifications,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            projectRepo:projectRepo,
                            BUILDER_TAG:'RPRUSDVIEWER',
                            executeBuild:true,
                            executeTests:true,
                            BUILD_TIMEOUT:90,
                            DEPLOY_TIMEOUT:45,
                            nodeRetry: nodeRetry])
}
