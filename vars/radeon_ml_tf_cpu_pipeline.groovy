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
                chmod +x tests
                tests --gtest_output=xml:../../${STAGE_NAME}.gtest.xml >> ../../${STAGE_NAME}.log 2>&1
                """
        }
    }
}


def executeTests(String osName, String asicName, Map options)
{
    cleanWS()
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
    dir('RadeonML') {
        bat """
        mkdir build
        cd build
        call "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\VC\\Auxiliary\\Build\\vcvarsall.bat" amd64 >> ..\\..\\${STAGE_NAME}.log 2>&1
        cmake -G "Visual Studio 15 2017 Win64" ${options['cmakeKeys']} -DRML_TENSORFLOW_DIR=${WORKSPACE}\\tensorflow_cc .. >> ..\\..\\${STAGE_NAME}.log 2>&1
        MSBuild.exe RadeonML.sln -property:Configuration=Release >> ..\\..\\${STAGE_NAME}.log 2>&1
        """

        if (env.TAG_NAME) {
            dir("rml-deploy") {
                checkOutBranchOrScm("master", "ssh://git@gitlab.cts.luxoft.com:30122/servants/rml-deploy.git", true, null, null, false, true, "radeonprorender-gitlab")
                bat """
                    MD tf_cpu\\${CIS_OS}
                    RMDIR /S/Q tf_cpu\\${CIS_OS}
                    MD tf_cpu\\${CIS_OS}
                    xcopy ..\\build\\Release tf_cpu\\${CIS_OS} /s/y/i
                    git config --local user.name "radeonbuildmaster"
                    git config --local user.email "radeonprorender.buildmaster@gmail.com"
                    git add --all
                    git commit -m "${CIS_OS} release ${env.TAG_NAME}"
                    git push origin HEAD:master
                """
            }
        }
    }
}

def executeBuildOSX(Map options)
{
}

def executeBuildLinux(Map options)
{
    dir('RadeonML') {
        sh """
        mkdir build
        cd build
        cmake ${options['cmakeKeys']} -DRML_TENSORFLOW_DIR=${WORKSPACE}/tensorflow_cc .. >> ../../${STAGE_NAME}.log 2>&1
        make -j >> ../../${STAGE_NAME}.log 2>&1
        """
     
        sh """
        cd build
        make
        mv bin Release
        
        tar cf ${CIS_OS}_Release.tar Release
        """
        archiveArtifacts "build/${CIS_OS}_Release.tar"

        if (env.TAG_NAME) {
            dir("rml-deploy") {
                checkOutBranchOrScm("master", "ssh://git@gitlab.cts.luxoft.com:30122/servants/rml-deploy.git", true, null, null, false, true, "radeonprorender-gitlab")
                sh """
                    mkdir -p tf_cpu/${CIS_OS}
                    rm -fdr tf_cpu/${CIS_OS}
                    mkdir -p tf_cpu/${CIS_OS}
                    cp -r ../build/Release/* ./tf_cpu/${CIS_OS}
                    git config --local user.name "radeonbuildmaster"
                    git config --local user.email "radeonprorender.buildmaster@gmail.com"
                    git add --all
                    git commit -m "${CIS_OS} release ${env.TAG_NAME}"
                    git push origin HEAD:master
                """
            }
        }
    }
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

    try
    {
        /*dir('tensorflow') {
            checkOutBranchOrScm(options['tfRepoVersion'], options['tfRepo'])
        }*/
        dir('tensorflow_cc') {
            receiveFiles("${options.PRJ_ROOT}/tensorflow_cc/", "./")
        }
        dir('RadeonML') {
            checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        }
        outputEnvironmentInfo(osName)

        withEnv(["CIS_OS=${osName}"]) {
            switch (osName) {
                case 'Windows':
                    executeBuildWindows(options);
                    break;
                case 'OSX':
                    executeBuildOSX(options);
                    break;
                default:
                    executeBuildLinux(options);
            }
        }

        stash includes: 'RadeonML/build/Release/**/*', name: "app${osName}"
    }
    catch (e)
    {
        println(e.getMessage())
        error_message = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    }
    finally
    {
        if (env.CHANGE_ID) {
            String status = error_message ? "failure" : "success"
            pullRequest.createStatus("${status}", context, "Build finished as '${status}'", "${env.BUILD_URL}/artifact/${STAGE_NAME}.log")
            options['commitContexts'].remove(context)
        }

        archiveArtifacts "*.log"
        dir('RadeonML') {
            zip archive: true, dir: 'build/Release', glob: '', zipFile: "${osName}_Release.zip"
        }
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

// TODO: add tests
def call(String projectBranch = "",
         String platforms = 'Windows;Ubuntu18',
         String PRJ_ROOT='rpr-ml',
         String PRJ_NAME='TF-CPU',
         String projectRepo='git@github.com:Radeon-Pro/RadeonML.git',
         String tfRepo='git@github.com:tensorflow/tensorflow.git',
         String tfRepoVersion='v1.13.1',
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         String cmakeKeys = "-DRML_DIRECTML=OFF -DRML_MIOPEN=OFF -DRML_TENSORFLOW_CPU=ON -DRML_TENSORFLOW_CUDA=OFF"
         ) {

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                           [platforms:platforms,
                            projectBranch:projectBranch,
                            tfRepo:tfRepo,
                            tfRepoVersion:tfRepoVersion,
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
