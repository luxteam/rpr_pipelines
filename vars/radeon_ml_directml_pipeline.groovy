 def executeGenTestRefCommand(String osName, Map options)
{
}

def executeTestCommand(String osName, Map options)
{
    dir('build-direct/Release') {
        switch(osName) {
            case 'Windows':
                bat """
                tests.exe --gtest_output=xml:..\\..\\${STAGE_NAME}.gtest.xml >> ..\\..\\${STAGE_NAME}.log 2>&1
                """
                break;
            case 'OSX':
                sh """
                echo "skip"
                """
                break;
            default:
                sh """
                echo "skip"
                """
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    cleanWS(osName)
    String error_message = ""

    try {
        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        outputEnvironmentInfo(osName)
        unstash "app${osName}"

        executeTestCommand(osName, options)
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        error_message = e.getMessage()
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        junit "*gtest.xml"

        if (env.CHANGE_ID) {
            String context = "[${options.PRJ_NAME}] [TEST] ${osName}-${asicName}"
            String description = error_message ? "Testing finished with error message: ${error_message}" : "Testing finished"
            String status = error_message ? "failure" : "success"
            String url = "${env.BUILD_URL}/artifact/${STAGE_NAME}.log"
            pullRequest.createStatus(status, context, description, url)
            options['commitContexts'].remove(context)
        }
    }
}

def executeBuildWindows(Map options)
{
    bat """
    mkdir build-direct
    cd build-direct
    cmake ${options['cmakeKeys']} .. >> ..\\${STAGE_NAME}.Release.log 2>&1
    set msbuild=\"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe\"
    %msbuild% RadeonML.sln -property:Configuration=Release >> ..\\${STAGE_NAME}.Release.log 2>&1
    """

    if (env.TAG_NAME) {
        dir("rml-deploy") {
            checkOutBranchOrScm("master", "ssh://git@gitlab.cts.luxoft.com:30122/servants/rml-deploy.git", true, null, null, false, true, "radeonprorender-gitlab")
            bat """
                MD "directml\\${CIS_OS}"
                RMDIR /S/Q "directml\\${CIS_OS}"
                MD "directml\\${CIS_OS}"
                xcopy ..\\build-direct\\Release "directml\\${CIS_OS}" /s/y/i
                git config --local user.name "radeonbuildmaster"
                git config --local user.email "radeonprorender.buildmaster@gmail.com"
                git add --all
                git commit -m "${CIS_OS} release ${env.TAG_NAME}"
                git push origin HEAD:master
            """
        }
    }

    bat """
    mkdir build-direct-debug
    cd build-direct-debug
    cmake ${options['cmakeKeys']} -DRML_LOG_LEVEL=Debug .. >> ..\\${STAGE_NAME}.Debug.log 2>&1
    set msbuild=\"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe\"
    %msbuild% RadeonML.sln -property:Configuration=Debug >> ..\\${STAGE_NAME}.Debug.log 2>&1
    """
}

def executeBuildOSX(Map options)
{
}

def executeBuildLinux(Map options)
{
}

def executePreBuild(Map options)
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

    def commitContexts = []
    // set pending status for all
    if(env.CHANGE_ID) {

        options['platforms'].split(';').each()
        { platform ->
            List tokens = platform.tokenize(':')
            String osName = tokens.get(0)
            // Statuses for builds
            String context = "[${options.PRJ_NAME}] [BUILD] ${osName}"
            commitContexts << context
            pullRequest.createStatus("pending", context, "Scheduled", "${env.JOB_URL}")
            if (tokens.size() > 1) {
                gpuNames = tokens.get(1)
                gpuNames.split(',').each()
                { gpuName ->
                    // Statuses for tests
                    context = "[${options.PRJ_NAME}] [TEST] ${osName}-${gpuName}"
                    commitContexts << context
                    pullRequest.createStatus("pending", context, "Scheduled", "${env.JOB_URL}")
                }
            }
        }
        options['commitContexts'] = commitContexts
    }
}

def executeBuild(String osName, Map options)
{
    String error_message = ""
    String context = "[${options.PRJ_NAME}] [BUILD] ${osName}"

    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        outputEnvironmentInfo(osName, "${STAGE_NAME}.Release")
        outputEnvironmentInfo(osName, "${STAGE_NAME}.Debug")

        if (env.CHANGE_ID) {
            pullRequest.createStatus("pending", context, "Checkout has been finished. Trying to build...", "${env.JOB_URL}")
        }

        switch(osName) {
        case 'Windows':
            executeBuildWindows(options);
            break;
        case 'OSX':
            executeBuildOSX(options);
            break;
        default:
            executeBuildLinux(options);
        }

        stash includes: 'build-direct/Release/**/*', name: "app${osName}"
    }
    catch (e) {
        println(e.getMessage())
        error_message = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        if (env.CHANGE_ID) {
            String status = error_message ? "failure" : "success"
            pullRequest.createStatus("${status}", context, "Build finished as '${status}'", "${env.BUILD_URL}/artifact/${STAGE_NAME}.log")
            options['commitContexts'].remove(context)
        }

        archiveArtifacts "${STAGE_NAME}.*.log"
        zip archive: true, dir: 'build-direct/Release', glob: '', zipFile: "${osName}_Release.zip"
        zip archive: true, dir: 'build-direct-debug/Debug', glob: '', zipFile: "${osName}_Debug.zip"
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    // set error statuses for PR, except if current build has been superseded by new execution
    if (env.CHANGE_ID && !currentBuild.nextBuild) {
        // if jobs was aborted or crushed remove pending status for unfinished stages
        options['commitContexts'].each() {
            pullRequest.createStatus("error", it, "Build has been terminated unexpectedly", "${env.BUILD_URL}")
        }
    }
}

def call(String projectBranch = "",
         String platforms = 'Windows:AMD_RadeonVII,NVIDIA_RTX2080',
         String PRJ_ROOT='rpr-ml',
         String PRJ_NAME='DirectML',
         String projectRepo='git@github.com:Radeon-Pro/RadeonML.git',
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         String cmakeKeys = '-G "Visual Studio 15 2017 Win64" -DRML_DIRECTML=ON -DRML_MIOPEN=OFF -DRML_TENSORFLOW_CPU=OFF -DRML_TENSORFLOW_CUDA=OFF') {

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                           [platforms:platforms,
                            projectBranch:projectBranch,
                            updateRefs:updateRefs,
                            enableNotifications:enableNotifications,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            projectRepo:projectRepo,
                            BUILDER_TAG:'BuilderML',
                            executeBuild:true,
                            executeTests:true,
                            cmakeKeys:cmakeKeys,
                            slackChannel:"${SLACK_ML_CHANNEL}",
                            slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
                            slackTocken:"slack-ml-channel",
                            retriesForTestStage:1])
}
