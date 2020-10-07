def executeGenTestRefCommand(String osName, Map options)
{
    dir('BaikalNext/RprTest')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                ..\\bin\\RprTest -quality ${options.RENDER_QUALITY} -genref 1 --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ..\\..\\${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                """
                break;
            case 'OSX':
                sh """
                export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                ../bin/RprTest -quality ${options.RENDER_QUALITY} -genref 1 --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ../../${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                """
                break;
            default:
                sh """
                export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                ../bin/RprTest -quality ${options.RENDER_QUALITY} -genref 1 --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ../../${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                """
        }
    }
}

def executeTestCommand(String osName, Map options)
{
    dir('BaikalNext/RprTest')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                ..\\bin\\RprTest -quality ${options.RENDER_QUALITY} --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ..\\..\\${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                """
                break;
            case 'OSX':
                sh """
                export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                ../bin/RprTest -quality ${options.RENDER_QUALITY} --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ../../${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                """
                break;
            default:
                sh """
                export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                ../bin/RprTest -quality ${options.RENDER_QUALITY} --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ../../${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                """
        }
    }
}

def executeTestsCustomQuality(String osName, String asicName, Map options)
{
    cleanWS(osName)
    String parsedAsicName = asicName.replace("_Beta", "")
    String REF_PATH_PROFILE="${options.REF_PATH}/${options.RENDER_QUALITY}/${parsedAsicName}-${osName}"
    String JOB_PATH_PROFILE="${options.JOB_PATH}/${options.RENDER_QUALITY}/${parsedAsicName}-${osName}"
    String error_message = ""

    try {
        outputEnvironmentInfo(osName, "${STAGE_NAME}.${options.RENDER_QUALITY}")
        unstash "app${osName}"
        switch(osName)
        {
            case 'Windows':
                unzip dir: '.', glob: '', zipFile: 'BaikalNext_Build-Windows.zip'
                break
            default:
                sh "tar -xJf BaikalNext_Build*"
        }

        if(options['updateRefs']) {
            echo "Updating Reference Images"
            executeGenTestRefCommand(osName, options)
            sendFiles('./BaikalNext/RprTest/ReferenceImages/*.*', "${REF_PATH_PROFILE}")
        } else {
            echo "Execute Tests"
            receiveFiles("${REF_PATH_PROFILE}/*", './BaikalNext/RprTest/ReferenceImages/')
            executeTestCommand(osName, options)
        }
    }
    catch (e) {
        println("Exception during [${options.RENDER_QUALITY}] quality tests execution")
        println(e.getMessage())
        error_message = e.getMessage()

        try {
            dir('HTML_Report') {
                checkOutBranchOrScm('master', 'git@github.com:luxteam/HTMLReportsShared')
                python3("-m pip install -r requirements.txt")
                python3("hybrid_report.py --xml_path ../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml --images_basedir ../BaikalNext/RprTest --report_path ../${asicName}-${osName}-${options.RENDER_QUALITY}_failures")
            }

            stash includes: "${asicName}-${osName}-${options.RENDER_QUALITY}_failures/**/*", name: "testResult-${asicName}-${osName}-${options.RENDER_QUALITY}", allowEmpty: true

            utils.publishReport(this, "${BUILD_URL}", "${asicName}-${osName}-${options.RENDER_QUALITY}_failures", "report.html", "${STAGE_NAME}_${options.RENDER_QUALITY}_failures", "${STAGE_NAME}_${options.RENDER_QUALITY}_failures")

        } catch (err) {
            println("Error during HTML report publish")
            println(err.getMessage())
        }

        //error("Failure")
    }
    finally {
        archiveArtifacts "*.log"
        junit "*.gtest.xml"

        if (env.CHANGE_ID)
        {
            String context = "[TEST] ${osName}-${asicName}-${options.RENDER_QUALITY}"
            String description = error_message ? "Testing finished with error message: ${error_message}" : "Testing finished"
            String status = error_message ? "failure" : "success"
            String url = error_message ? "${env.BUILD_URL}/${STAGE_NAME}_${options.RENDER_QUALITY}_failures" : "${env.BUILD_URL}/artifact/${STAGE_NAME}.${options.RENDER_QUALITY}.log"
            pullRequest.createStatus(status, context, description, url)
            options['commitContexts'].remove(context)
        }
    }
}


def executeTests(String osName, String asicName, Map options)
{
    Boolean some_stage_fail = false
    options['testsQuality'].split(",").each() {
        try {
            options['RENDER_QUALITY'] = "${it}"
            executeTestsCustomQuality(osName, asicName, options)
        }
        catch (e) {
            // suppress exception for start next quality test
            some_stage_fail = true
            println(e.toString())
            println(e.getMessage())
        }
    }
    if (some_stage_fail) {
        // send error signal for mark stage as failed
        error "Error during tests execution"
    }
}


def executeBuildWindows(Map options)
{
    if (fileExists("3rdparty\\PKFXSDK_AMD_VRS.7z") && fileExists("Resources\\PopcornFX.7z")) {
        bat """
            %CIS_TOOLS%\\7-Zip\\7z.exe x 3rdparty\\PKFXSDK_AMD_VRS.7z -o3rdparty -y
            %CIS_TOOLS%\\7-Zip\\7z.exe x Resources\\PopcornFX.7z -oResources -y
        """
    }
    
    String build_type = options['cmakeKeys'].contains("-DCMAKE_BUILD_TYPE=Debug") ? "Debug" : "Release"
    bat """
        mkdir Build
        cd Build
        cmake ${options['cmakeKeys']} -G "Visual Studio 16 2019" -A "x64" .. >> ..\\${STAGE_NAME}.log 2>&1
        cmake --build . --target PACKAGE --config ${build_type} >> ..\\${STAGE_NAME}.log 2>&1
        rename BaikalNext.zip BaikalNext_${STAGE_NAME}.zip
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
    checkOutBranchOrScm(options.projectBranch, "git@github.com:Radeon-Pro/Ludashi-Hybrid.git", true)

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true)
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${commitMessage}"
    println "Commit SHA: ${options.commitSHA}"
    
    if (commitMessage.contains("[CIS:GENREF]") && env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.updateRefs = true
        println("[CIS:GENREF] has been founded in comment")
    }

    if (env.CHANGE_URL) {
        echo "branch was detected as Pull Request"
    }

    options.commitMessage = []
    commitMessage = commitMessage.split('\r\n')
    commitMessage[2..commitMessage.size()-1].collect(options.commitMessage) { it.trim() }
    options.commitMessage = options.commitMessage.join('\n')

    println "Commit list message: ${options.commitMessage}"

    // set pending status for all
    if(env.CHANGE_ID) {

        def commitContexts = []
        options['platforms'].split(';').each()
        { platform ->
            List tokens = platform.tokenize(':')
            String osName = tokens.get(0)
            // Statuses for builds
            String context = "[BUILD] ${osName}"
            commitContexts << context
            pullRequest.createStatus("pending", context, "Scheduled", "${env.JOB_URL}")
            if (tokens.size() > 1) {
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
    }
}

def executeBuild(String osName, Map options)
{
    String error_message = ""
    String context = "[BUILD] ${osName}"
    try
    {
        checkOutBranchOrScm(options.projectBranch, "git@github.com:Radeon-Pro/Ludashi-Hybrid.git")
        outputEnvironmentInfo(osName)

        if (env.CHANGE_ID)
        {
            pullRequest.createStatus("pending", context, "Checkout has been finished. Trying to build...", "${env.JOB_URL}")
        }

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

        dir('Build')
        {
            stash includes: "BaikalNext_${STAGE_NAME}*", name: "app${osName}"
        }
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
        archiveArtifacts "*.log"
        archiveArtifacts "Build/BaikalNext_${STAGE_NAME}*"
        if (env.CHANGE_ID)
        {
            String status = error_message ? "failure" : "success"
            pullRequest.createStatus("${status}", context, "Build finished as '${status}'", "${env.BUILD_URL}/artifact/${STAGE_NAME}.log")
            options['commitContexts'].remove(context)
        }
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    cleanWS()
    if(options['executeTests'] && testResultList) {
        try {
            String reportFiles = ""
            dir("SummaryReport") {
                options['testsQuality'].split(",").each() { quality ->
                    testResultList.each() {
                        try {
                            unstash "${it}-${quality}"
                            reportFiles += ", ${it}-${quality}_failures/report.html".replace("testResult-", "")
                        }
                        catch(e) {
                            echo "Can't unstash ${it} ${quality}"
                            println(e.toString());
                            println(e.getMessage());
                        }
                    }
                }
            }
            publishHTML([allowMissing: true,
                         alwaysLinkToLastBuild: false,
                         keepAll: true,
                         reportDir: "SummaryReport",
                         reportFiles: "$reportFiles",
                         reportName: "HTML Failures"])
        }
        catch(e) {
            println(e.toString())
        }
    }

    // set error statuses for PR, except if current build has been superseded by new execution
    if (env.CHANGE_ID && !currentBuild.nextBuild)
    {
        // if jobs was aborted or crushed remove pending status for unfinished stages
        options['commitContexts'].each()
        {
            pullRequest.createStatus("error", it, "Build has been terminated unexpectedly", "${env.BUILD_URL}")
        }
        String status = currentBuild.result ? "${currentBuild.result} \n failures report: ${env.BUILD_URL}/HTML_20Failures/" : "success"
        def comment = pullRequest.comment("Jenkins build for ${pullRequest.head} finished as ${status}")
    }
}

def call(String projectBranch = "",
         String platforms = 'Windows:AMD_RadeonVII_Beta',
         String testsQuality = "low,medium",
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         String cmakeKeys = "-DCMAKE_BUILD_TYPE=Release -DBAIKAL_ENABLE_RPR=ON -DBAIKAL_NEXT_EMBED_KERNELS=ON") {

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                           [platforms:platforms,
                            projectBranch:projectBranch,
                            updateRefs:updateRefs,
                            testsQuality:testsQuality,
                            enableNotifications:enableNotifications,
                            PRJ_NAME:'RadeonProRender-LudashiHybrid',
                            PRJ_ROOT:'rpr-core',
                            BUILDER_TAG:'BuilderS && VS2019',
                            TESTER_TAG:'Ludashi',
                            executeBuild:true,
                            executeTests:true,
                            slackChannel:"${SLACK_BAIKAL_CHANNEL}",
                            slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
                            slackTocken:"${SLACK_BAIKAL_TOCKEN}",
                            TEST_TIMEOUT:20,
                            cmakeKeys:cmakeKeys,
                            retriesForTestStage:1])
}
