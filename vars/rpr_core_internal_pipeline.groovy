import UniverseClient
import groovy.transform.Field
import groovy.json.JsonOutput;
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import TestsExecutionType

@Field String UniverseURLProd
@Field String UniverseURLDev
@Field String ImageServiceURL
@Field String ProducteName = "AMD%20Radeon™%20ProRender%20Core"
@Field UniverseClient universeClientProd
@Field UniverseClient universeClientDev


def getCoreSDK(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':

            if (!fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip")) {

                clearBinariesWin()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "WindowsSDK"

                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    copy binWin64.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip"
                """

            } else {
                println "[INFO] The plugin ${options.pluginWinSha}.zip exists in the storage."
                bat """
                    copy "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip" binWin64.zip
                """
            }

            unzip zipFile: "binWin64.zip", dir: "rprSdk", quiet: true

            break;

        case 'OSX':

            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip")) {

                clearBinariesUnix()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "OSXSDK"

                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                    cp binMacOS.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip"
                """

            } else {
                println "[INFO] The plugin ${options.pluginOSXSha}.zip exists in the storage."
                sh """
                    cp "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip" binMacOS.zip
                """
            }

            unzip zipFile: "binMacOS.zip", dir: "rprSdk", quiet: true

            break;

        default:

            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip")) {

                clearBinariesUnix()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "Ubuntu18SDK"

                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                    cp binUbuntu18.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip"
                """

            } else {

                println "[INFO] The plugin ${options.pluginUbuntuSha}.zip exists in the storage."
                sh """
                    cp "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip" binUbuntu18.zip
                """
            }

            unzip zipFile: "binUbuntu18.zip", dir: "rprSdk", quiet: true

            break;
    }
}

def executeGenTestRefCommand(String osName, Map options, Boolean delete)
{

    dir('scripts')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                make_results_baseline.bat ${delete}
                """
                break;
            case 'OSX':
                sh """
                ./make_results_baseline.sh ${delete}
                """
                break;
            default:
                sh """
                ./make_results_baseline.sh ${delete}
                """
        }
    }
}

def executeUnitTestCommand(String osName)
{
    switch(osName)
    {
        case 'Windows':
            dir("RadeonProRenderSDK/Northstar/UnitTest")
            {
                bat """
                ..\\dist\\release\\bin\\x86_64\\UnitTest64.exe -referencePath ..\\..\\..\\frUnittestdata\\northstar\\gpu\\ --gtest_filter=*NEXT_* --gtest_output=xml:..\\..\\..\\${STAGE_NAME}.gtest.xml >> ..\\..\\..\\${STAGE_NAME}.log  2>&1
                """
            }
            break;
        case 'OSX':
            // Tests for OSX aren't supported now
            break;
        default:
            // Tests for Linux aren't supported now
            break;
    }
}

def executeUnitTests(String osName, String asicName, Map options)
{
    dir("RadeonProRenderSDK")
    {
        withNotifications(title: options["stageName"], options: options, beginUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_RPR_SDK_REPO) {
            timeout(time: "10", unit: "MINUTES") {
                cleanWS(osName)
                checkOutBranchOrScm(options["projectBranch"], "git@github.com:amdadvtech/RadeonProRenderSDKInternal.git", false, options["prBranchName"], options["prRepoName"])
            }
        }
    }

    dir("frUnittestdata")
    {
        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_UNIT_TESTS_REPO) {
            timeout(time: "30", unit: "MINUTES") {
                checkOutBranchOrScm(options["unitTestsBranch"], "git@github.com:amdadvtech/frUnittestdata.git")
            }
        }
    }

    try {
        GithubNotificator.updateStatus("Test", options['stageName'], "pending", options, NotificationConfiguration.EXECUTE_UNIT_TESTS, "${BUILD_URL}")
        executeUnitTestCommand(osName)
    } catch (e) {
        dir("HTML_Report") {
            checkOutBranchOrScm("master", "git@github.com:luxteam/HTMLReportsShared")
            python3("-m pip install --user -r requirements.txt")
            python3("hybrid_report.py --xml_path ../${STAGE_NAME}.gtest.xml --images_basedir ../RadeonProRenderSDK/Northstar/UnitTest/result  --report_path ../${asicName}-${osName}_failures --tool_name \"Core Internal\" --compare_with_refs=False")
        }

        stash includes: "${asicName}-${osName}_failures/**/*", name: "unitTestFailures-${asicName}-${osName}", allowEmpty: true

        utils.publishReport(this, "${BUILD_URL}", "${asicName}-${osName}_failures", "report.html", "${STAGE_NAME}_failures", "${STAGE_NAME}_failures")
    } finally {
        archiveArtifacts "*.log, *.gtest.xml"
        junit "*.gtest.xml"
    }
}

def executeTestCommand(String osName, String asicName, Map options)
{
    withCredentials([usernamePassword(credentialsId: 'image_service', usernameVariable: 'IS_USER', passwordVariable: 'IS_PASSWORD'),
        usernamePassword(credentialsId: 'universeMonitoringSystem', usernameVariable: 'UMS_USER', passwordVariable: 'UMS_PASSWORD'),
        string(credentialsId: 'minioEndpoint', variable: 'MINIO_ENDPOINT'),
        usernamePassword(credentialsId: 'minioService', usernameVariable: 'MINIO_ACCESS_KEY', passwordVariable: 'MINIO_SECRET_KEY')])
    {
        withEnv(["UMS_USE=${options.sendToUMS}", "UMS_ENV_LABEL=${osName}-${asicName}",
            "UMS_BUILD_ID_PROD=${options.buildIdProd}", "UMS_JOB_ID_PROD=${options.jobIdProd}", "UMS_URL_PROD=${universeClientProd.url}", 
            "UMS_LOGIN_PROD=${UMS_USER}", "UMS_PASSWORD_PROD=${UMS_PASSWORD}",
            "UMS_BUILD_ID_DEV=${options.buildIdDev}", "UMS_JOB_ID_DEV=${options.jobIdDev}", "UMS_URL_DEV=${universeClientDev.url}",
            "UMS_LOGIN_DEV=${UMS_USER}", "UMS_PASSWORD_DEV=${UMS_PASSWORD}",
            "IS_LOGIN=${IS_USER}", "IS_PASSWORD=${IS_PASSWORD}", "IS_URL=${options.isUrl}",
            "MINIO_ENDPOINT=${MINIO_ENDPOINT}", "MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}", "MINIO_SECRET_KEY=${MINIO_SECRET_KEY}"])
        {
            switch(osName) {
                case 'Windows':
                    dir('scripts')
                    {
                        bat """
                        run.bat ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} ${options.updateRefs} >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                        """
                    }
                    break;
                case 'OSX':
                    dir('scripts')
                    {
                        withEnv(["LD_LIBRARY_PATH=../rprSdk:\$LD_LIBRARY_PATH"]) {
                            sh """
                            ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} ${options.updateRefs} >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                            """
                        }
                    }
                    break;
                default:
                    dir('scripts')
                    {
                        withEnv(["LD_LIBRARY_PATH=../rprSdk:\$LD_LIBRARY_PATH"]) {
                            sh """
                            ./run.sh ${options.testsPackage} \"${options.tests}\" ${options.width} ${options.height} ${options.iterations} ${options.updateRefs} >> \"../${STAGE_NAME}_${options.currentTry}.log\" 2>&1
                            """
                        }
                    }
            }
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{

    executeUnitTests(osName, asicName, options)

    // check that current platform is in list of platforms for which render should be executed
    if (!(options.renderPlatforms.contains(osName) && options.renderPlatforms.contains(asicName))) {
        return
    }

    // TODO: improve envs, now working on Windows testers only
    if (options.sendToUMS){
        universeClientProd.stage("Tests-${osName}-${asicName}", "begin")
        universeClientDev.stage("Tests-${osName}-${asicName}", "begin")
    }

    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {
        withNotifications(title: options["stageName"], options: options, beginUrl: "${BUILD_URL}", configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
            timeout(time: "5", unit: "MINUTES") {
                cleanWS(osName)
                checkOutBranchOrScm(options["testsBranch"], "git@github.com:luxteam/jobs_test_core.git")
            }
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_RPR_SDK_PACKAGE) {
            getCoreSDK(osName, options)
        }

        withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.DOWNLOAD_SCENES) {
            downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/CoreAssets/", "CoreAssets")
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, "", options.currentTry)

        if (options["updateRefs"].contains("Update")) {
            withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.EXECUTE_TESTS) {
                executeTestCommand(osName, asicName, options)
                executeGenTestRefCommand(osName, options, options["updateRefs"].contains("clean"))
                sendFiles("./Work/GeneratedBaselines/", REF_PATH_PROFILE)
                // delete generated baselines when they're sent 
                switch(osName) {
                    case "Windows":
                        bat "if exist Work\\GeneratedBaselines rmdir /Q /S Work\\GeneratedBaselines"
                        break;
                    default:
                        sh "rm -rf ./Work/GeneratedBaselines"        
                }
            }
        } else {
            withNotifications(title: options["stageName"], printMessage: true, options: options, configuration: NotificationConfiguration.COPY_BASELINES) {
                String baseline_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_core_autotests_baselines" : "/mnt/c/TestResources/rpr_core_autotests_baselines"
                println "[INFO] Downloading reference images for ${options.tests}"
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", baseline_dir)
                }
            }
            withNotifications(title: options["stageName"], options: options, configuration: NotificationConfiguration.EXECUTE_TESTS) {
                executeTestCommand(osName, asicName, options)
            }
        }
        options.executeTestsFinished = true

    } catch (e) {
        if (options.currentTry < options.nodeReallocateTries) {
            stashResults = false
        }
        println(e.toString())
        println(e.getMessage())
        if (e instanceof ExpectedExceptionWrapper) {
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, e.getMessage(), "${BUILD_URL}")
            throw e
        } else {
            String errorMessage = "The reason is not automatically identified. Please contact support."
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, errorMessage, "${BUILD_URL}")
            throw new ExpectedExceptionWrapper(errorMessage, e)
        }
    }
    finally {
        try {
            dir("${options.stageName}") {
                utils.moveFiles(this, osName, "../*.log", ".")
                utils.moveFiles(this, osName, "../scripts/*.log", ".")
                utils.renameFile(this, osName, "launcher.engine.log", "${options.stageName}_engine_${options.currentTry}.log")
            }
            archiveArtifacts artifacts: "${options.stageName}/*.log", allowEmptyArchive: true
            if (options.sendToUMS) {
                dir("jobs_launcher") {
                    sendToMINIO(options, osName, "../${options.stageName}", "*.log")
                }
            }
            if (stashResults) {
                dir('Work')
                {
                    if (fileExists("Results/Core/session_report.json")) {

                        def sessionReport = null
                        sessionReport = readJSON file: 'Results/Core/session_report.json'

                        if (options.sendToUMS)
                        {
                            universeClientProd.stage("Tests-${osName}-${asicName}", "end")
                            universeClientDev.stage("Tests-${osName}-${asicName}", "end")
                        }

                        if (sessionReport.summary.error > 0) {
                            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, NotificationConfiguration.SOME_TESTS_ERRORED, "${BUILD_URL}")
                        } else if (sessionReport.summary.failed > 0) {
                            GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, NotificationConfiguration.SOME_TESTS_FAILED, "${BUILD_URL}")
                        } else {
                            GithubNotificator.updateStatus("Test", options['stageName'], "success", options, NotificationConfiguration.ALL_TESTS_PASSED, "${BUILD_URL}")
                        }

                        echo "Stashing test results to : ${options.testResultsName}"
                        stash includes: '**/*', excludes: '**/cache/**', name: "${options.testResultsName}", allowEmpty: true

                        // reallocate node if there are still attempts
                        if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped || sessionReport.summary.total == 0) {
                            if (sessionReport.summary.total != sessionReport.summary.skipped){
                                collectCrashInfo(osName, options, options.currentTry)
                                if (osName == "Ubuntu18"){
                                    sh """
                                        echo "Restarting Unix Machine...."
                                        hostname
                                        (sleep 3; sudo shutdown -r now) &
                                    """
                                    sleep(60)
                                }
                                String errorMessage
                                if (options.currentTry < options.nodeReallocateTries) {
                                    errorMessage = "All tests were marked as error. The test group will be restarted."
                                } else {
                                    errorMessage = "All tests were marked as error."
                                }
                                throw new ExpectedExceptionWrapper(errorMessage, new Exception(errorMessage))
                            }
                        }
                    }
                }
            }
        } catch (e) {
            // throw exception in finally block only if test stage was finished
            if (options.executeTestsFinished) {
                if (e instanceof ExpectedExceptionWrapper) {
                    GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, e.getMessage(), "${BUILD_URL}")
                    throw e
                } else {
                    GithubNotificator.updateStatus("Test", options['stageName'], "failure", options, NotificationConfiguration.FAILED_TO_SAVE_RESULTS, "${BUILD_URL}")
                    throw new ExpectedExceptionWrapper(NotificationConfiguration.FAILED_TO_SAVE_RESULTS, e)
                }
            }
        }
    }
}

def executeBuildWindows(Map options) {
    withNotifications(title: "WIndows", options: options, beginUrl: "${BUILD_URL}/artifact/Build-Windows.log",
        endUrl: "${BUILD_URL}/artifact/binWin64.zip", configuration: NotificationConfiguration.BUILDING_RPR_SDK_PACKAGE) {
        dir("RadeonProRenderSDK/RPR/RadeonProRender/lib/x64") {
            zip archive: true, dir: ".", glob: "", zipFile: "binWin64.zip"
            stash includes: "binWin64.zip", name: 'WindowsSDK'
            options.pluginWinSha = sha1 "binWin64.zip"
        }
        if (options.sendToUMS) {
            dir("jobs_launcher") {
                sendToMINIO(options, "Windows", "..\\RadeonProRenderSDK\\RadeonProRender\\binWin64", "binWin64.zip", false)                            
            }
        }
    }
}

def executeBuildOSX(Map options) {
    withNotifications(title: "OSX", options: options, beginUrl: "${BUILD_URL}/artifact/Build-OSX.log",
        endUrl: "${BUILD_URL}/artifact/binMacOS.zip", configuration: NotificationConfiguration.BUILDING_RPR_SDK_PACKAGE) {
        dir("RadeonProRenderSDK/RadeonProRender/binMacOS") {
            zip archive: true, dir: ".", glob: "", zipFile: "binMacOS.zip"
            stash includes: "binMacOS.zip", name: "OSXSDK"
            options.pluginOSXSha = sha1 "binMacOS.zip"
        }
        if (options.sendToUMS) {
            dir("jobs_launcher") {
                sendToMINIO(options, "OSX", "../RadeonProRenderSDK/RadeonProRender/binMacOS", "binMacOS.zip", false)                            
            }
        }
    }
}

def executeBuildLinux(Map options) {
    withNotifications(title: "Ubuntu18", options: options, beginUrl: "${BUILD_URL}/artifact/Build-Ubuntu18.log",
        endUrl: "${BUILD_URL}/artifact/binUbuntu18.zip", configuration: NotificationConfiguration.BUILDING_RPR_SDK_PACKAGE) {
        dir("RadeonProRenderSDK/RadeonProRender/binUbuntu18") {
            zip archive: true, dir: ".", glob: "", zipFile: "binUbuntu18.zip"
            stash includes: "binUbuntu18.zip", name: "Ubuntu18SDK"
            options.pluginUbuntuSha = sha1 "binUbuntu18.zip"
        }
        if (options.sendToUMS) {
            dir("jobs_launcher") {
                sendToMINIO(options, "Ubuntu18", "../RadeonProRenderSDK/RadeonProRender/binUbuntu18", "binUbuntu18.zip", false)                            
            }
        }
    }
}

def executeBuild(String osName, Map options)
{
    if (options.sendToUMS){
        universeClientProd.stage("Build-" + osName , "begin")
        universeClientDev.stage("Build-" + osName , "begin")
    }

    try {
        dir("RadeonProRenderSDK")
        {
            withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_PLUGIN_REPO) {
                checkOutBranchOrScm(options["projectBranch"], "git@github.com:amdadvtech/RadeonProRenderSDKInternal.git", false, options["prBranchName"], options["prRepoName"])
            }
        }

        if (options.sendToUMS) {
            timeout(time: "5", unit: "MINUTES") {
                dir("jobs_launcher") {
                    withNotifications(title: osName, printMessage: true, options: options, configuration: NotificationConfiguration.DOWNLOAD_JOBS_LAUNCHER) {
                        checkOutBranchOrScm(options["jobsLauncherBranch"], "git@github.com:luxteam/jobs_launcher.git")
                    }
                }
            }
        }

        outputEnvironmentInfo(osName)

        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.BUILD_RPR_SDK) {
            switch(osName) {
                case "Windows":
                    executeBuildWindows(options);
                    break;
                case "OSX":
                    executeBuildOSX(options);
                    break;
                default:
                    executeBuildLinux(options);
            }
        }
    }
    catch (e) {
        throw e
    }
    finally {
        if (options.sendToUMS) {
            dir("jobs_launcher") {
                switch(osName) {
                    case 'Windows':
                        sendToMINIO(options, osName, "..", "*.log")
                        break;
                    default:
                        sendToMINIO(options, osName, "..", "*.log")
                }
            }
        }
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
    if (options.sendToUMS){
        universeClientProd.stage("Build-" + osName, "end")
        universeClientDev.stage("Build-" + osName, "end")
    }
}

def executePreBuild(Map options)
{
    if (env.CHANGE_URL) {
        println "Branch was detected as Pull Request"
        GithubNotificator githubNotificator = new GithubNotificator(this, pullRequest)
        options.githubNotificator = githubNotificator
        githubNotificator.initPreBuild("${BUILD_URL}")
    }

    withNotifications(title: "Version increment", options: options, configuration: NotificationConfiguration.DOWNLOAD_RPR_SDK_REPO) {
        checkOutBranchOrScm(options["projectBranch"], "git@github.com:amdadvtech/RadeonProRenderSDKInternal.git")
    }

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim().replace('\n', '')
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"
    println "Commit shortSHA: ${options.commitShortSHA}"

    if (options.projectBranch){
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }

    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '30']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '30']]]);
    }


    def tests = []
    options.groupsUMS = []

    withNotifications(title: "Version increment", options: options, configuration: NotificationConfiguration.CONFIGURE_TESTS) {
        dir('jobs_test_core')
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_core.git')
            dir ('jobs_launcher') {
                options['jobsLauncherBranch'] = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            }
            options['testsBranch'] = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            println "[INFO] Test branch hash: ${options['testsBranch']}"

            if(options.testsPackage != "none")
            {
                // json means custom test suite. Split doesn't supported
                def tempTests = readJSON file: "jobs/${options.testsPackage}"
                tempTests["groups"].each() {
                    // TODO: fix: duck tape - error with line ending
                    tests << it.key
                }
                options.tests = tests
                options.testsPackage = "none"
                options.groupsUMS = tests
            }
            else {
                options.tests.split(" ").each()
                {
                    tests << "${it}"
                }
                options.tests = tests
                options.groupsUMS = tests
            }

            options.testsList = ['']
            options.tests = tests.join(" ")

            if (options.sendToUMS) {
                try {
                    universeClientProd.createBuild(options.universePlatforms, options.groupsUMS, options.updateRefs, options)
                    universeClientDev.createBuild(options.universePlatforms, options.groupsUMS, options.updateRefs, options)
                
                    if (universeClientProd.build != null || universeClientDev != null){
                        options.buildIdProd = universeClientProd.build["id"]
                        options.jobIdProd = universeClientProd.build["job_id"]
                        options.isUrl = universeClientProd.is_url

                        options.buildIdDev = universeClientDev.build["id"]
                        options.jobIdDev = universeClientDev.build["job_id"]
                        options.isUrl = universeClientDev.is_url
                    } else {
                        println("Failed to create build: ${universeClientProd.build} & ${universeClientDev.build}. Set sendToUms to false.")
                        options.sendToUMS = false
                    }
                } catch (e) {
                    println("Failed to create build in UMS. Set sendToUms to false.")
                    println(e.toString())
                    options.sendToUMS = false
                }    
            }
        }

        if (env.CHANGE_URL) {
            options.githubNotificator.initPR(options, "${BUILD_URL}")
        }
    }
}


def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        cleanWS()

        if(options['executeTests'] && testResultList)
        {
            withNotifications(title: "Building test report", options: options, startUrl: "${BUILD_URL}", configuration: NotificationConfiguration.BUILDING_UNIT_TESTS_REPORT) {
                String reportFiles = ""
                dir("SummaryReport") {
                    testResultList.each() {
                        String stashName = it.replace("testResult", "unitTestFailures")
                        try {
                            unstash "${stashName}"
                            reportFiles += ", ${stashName}_failures/report.html".replace("unitTestFailures-", "")
                        }
                        catch(e) {
                            echo "[ERROR] Can't unstash ${stashName}"
                            println(e.toString());
                            println(e.getMessage());
                        }
                    }
                }
                utils.publishReport(this, "${BUILD_URL}", "SummaryReport", "${reportFiles}", "Failures Report", "${STAGE_NAME}_failures")
            }

            withNotifications(title: "Building test report", options: options, configuration: NotificationConfiguration.DOWNLOAD_TESTS_REPO) {
                checkOutBranchOrScm(options["testsBranch"], "git@github.com:luxteam/jobs_test_core.git")
            }

            List lostStashes = []

            dir("summaryTestResults")
            {
                unstashCrashInfo(options['nodeRetry'])
                testResultList.each()
                {
                    dir("$it".replace("testResult-", ""))
                    {
                        try
                        {
                            unstash "$it"
                        }catch(e)
                        {
                            echo "Can't unstash ${it}"
                            lostStashes.add("'$it'".replace("testResult-", ""))
                            println(e.toString());
                            println(e.getMessage());
                        }

                    }
                }
            }

            try {
                dir("core_tests_configuration") {
                    bat(returnStatus: false, script: "%CIS_TOOLS%\\receiveFilesCoreConf.bat ${options.PRJ_ROOT}/${options.PRJ_NAME}/CoreAssets/ .")
                }
            } catch (e) {
                println("[ERROR] Can't download json files with core tests configuration")
            }

            try {
                dir("jobs_launcher") {
                    bat """
                    count_lost_tests.bat \"${lostStashes}\" .. ..\\summaryTestResults \"${options.splitTestsExecution}\" \"${options.testsPackage}\" \"${options.tests.toString().replace(" ", "")}\" \"\" \"{}\"
                    """
                }
            } catch (e) {
                println("[ERROR] Can't generate number of lost tests")
            }

            try {
                GithubNotificator.updateStatus("Deploy", "Building test report", "pending", options, NotificationConfiguration.BUILDING_REPORT, "${BUILD_URL}")
                def buildNumber = ""
                if (options.collectTrackedMetrics) {
                    buildNumber = env.BUILD_NUMBER
                    try {
                        dir("summaryTestResults/tracked_metrics") {
                            receiveFiles("${options.PRJ_ROOT}/${options.PRJ_NAME}/TrackedMetrics/${env.JOB_NAME}/", ".")
                        }
                    } catch (e) {
                        println("[WARNING] Failed to download history of tracked metrics.")
                        println(e.toString())
                        println(e.getMessage())
                    }
                }
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}", "BUILD_NAME=${options.baseBuildName}"])
                {
                    dir("jobs_launcher")
                    {
                        if(options.projectBranch != "") {
                            options.branchName = options.projectBranch
                        } else {
                            options.branchName = env.BRANCH_NAME
                        }
                        if(options.incrementVersion) {
                            options.branchName = "master"
                        }

                        options.commitMessage = options.commitMessage.replace("'", "")
                        options.commitMessage = options.commitMessage.replace('"', '')

                        def retryInfo = JsonOutput.toJson(options.nodeRetry)
                        dir("..\\summaryTestResults") {
                            JSON jsonResponse = JSONSerializer.toJSON(retryInfo, new JsonConfig());
                            writeJSON file: 'retry_info.json', json: jsonResponse, pretty: 4
                        }                    

                        bat """
                        build_reports.bat ..\\summaryTestResults Core ${options.commitSHA} ${options.branchName} \"${escapeCharsByUnicode(options.commitMessage)}\" \"\" \"${buildNumber}\"
                        """

                        bat "get_status.bat ..\\summaryTestResults"
                    }
                }
                if (options.collectTrackedMetrics) {
                    try {
                        dir("summaryTestResults/tracked_metrics") {
                            sendFiles(".", "${options.PRJ_ROOT}/${options.PRJ_NAME}/TrackedMetrics/${env.JOB_NAME}")
                        }
                    } catch (e) {
                        println("[WARNING] Failed to update history of tracked metrics.")
                        println(e.toString())
                        println(e.getMessage())
                    }
                }  
            } catch(e) {
                String errorMessage = utils.getReportFailReason(e.getMessage())
                GithubNotificator.updateStatus("Deploy", "Building test report", "failure", options, errorMessage, "${BUILD_URL}")
                if (utils.isReportFailCritical(e.getMessage())) {
                    options.problemMessageManager.saveSpecificFailReason(errorMessage, "Deploy")
                    println("[ERROR] Failed to build test report.")
                    println(e.toString())
                    println(e.getMessage())
                    if (!options.testDataSaved) {
                        try {
                            // Save test data for access it manually anyway
                            utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", \
                                "Test Report", "Summary Report, Performance Report, Compare Report")
                            options.testDataSaved = true 
                        } catch(e1) {
                            println("[WARNING] Failed to publish test data.")
                            println(e.toString())
                            println(e.getMessage())
                        }
                    }
                    throw e
                } else {
                    currentBuild.result = "FAILURE"
                    options.problemMessageManager.saveGlobalFailReason(errorMessage)
                }
            }

            try
            {
                dir("jobs_launcher") {
                    archiveArtifacts "launcher.engine.log"
                }
            }
            catch(e)
            {
                println("[ERROR] during archiving launcher.engine.log")
                println(e.toString())
                println(e.getMessage())
            }

            Map summaryTestResults = [:]
            try
            {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                summaryTestResults['passed'] = summaryReport.passed
                summaryTestResults['failed'] = summaryReport.failed
                summaryTestResults['error'] = summaryReport.error
                if (summaryReport.error > 0) {
                    println("[INFO] Some tests marked as error. Build result = FAILURE.")
                    currentBuild.result = "FAILURE"

                    options.problemMessageManager.saveGlobalFailReason(NotificationConfiguration.SOME_TESTS_ERRORED)
                }
                else if (summaryReport.failed > 0) {
                    println("[INFO] Some tests marked as failed. Build result = UNSTABLE.")
                    currentBuild.result = "UNSTABLE"

                    options.problemMessageManager.saveUnstableReason(NotificationConfiguration.SOME_TESTS_FAILED)
                }
            }
            catch(e)
            {
                println(e.toString())
                println(e.getMessage())
                println("[ERROR] CAN'T GET TESTS STATUS")
                options.problemMessageManager.saveUnstableReason(NotificationConfiguration.CAN_NOT_GET_TESTS_STATUS)
                currentBuild.result = "UNSTABLE"
            }

            try
            {
                options.testsStatus = readFile("summaryTestResults/slack_status.json")
            }
            catch(e)
            {
                println(e.toString())
                println(e.getMessage())
                options.testsStatus = ""
            }

            withNotifications(title: "Building test report", options: options, configuration: NotificationConfiguration.PUBLISH_REPORT) {
                utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", \
                    "Test Report", "Summary Report, Performance Report, Compare Report")

                if (summaryTestResults) {
                    // add in description of status check information about tests statuses
                    // Example: Report was published successfully (passed: 69, failed: 11, error: 0)
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", options, "${NotificationConfiguration.REPORT_PUBLISHED} Results: passed - ${summaryTestResults.passed}, failed - ${summaryTestResults.failed}, error - ${summaryTestResults.error}.", "${BUILD_URL}/Test_20Report")
                } else {
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", options, NotificationConfiguration.REPORT_PUBLISHED, "${BUILD_URL}/Test_20Report")
                }
            }

            if (options.sendToUMS) {
                try {
                    String status = currentBuild.result ?: 'SUCCESSFUL'
                    universeClientProd.changeStatus(status)
                    universeClientDev.changeStatus(status)
                }
                catch (e){
                    println(e.getMessage())
                }
            }
        }
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally
    {}
}


def call(String projectBranch = "",
         String testsBranch = "master",
         String unitTestsBranch = "master",
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,AMD_RadeonVII,AMD_RX5700XT,NVIDIA_GF1080TI,NVIDIA_RTX2080TI,NVIDIA_RTX2070S',
         String renderPlatforms = 'Windows:AMD_RX5700XT,NVIDIA_RTX2070S',
         String updateRefs = 'No',
         Boolean enableNotifications = false,
         String renderDevice = "gpu",
         String testsPackage = "Full-Internal.json",
         String tests = "",
         String width = "0",
         String height = "0",
         String iterations = "0",
         Boolean sendToUMS = false,
         String tester_tag = 'Core',
         String mergeablePR = "",
         String parallelExecutionTypeString = "TakeOneNodePerGPU",
         Boolean collectTrackedMetrics = true)
{
    ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)
    Map options = [:]
    options["stage"] = "Init"
    options["problemMessageManager"] = problemMessageManager
    
    def nodeRetry = []
    Map errorsInSuccession = [:]

    try {
        withNotifications(options: options, configuration: NotificationConfiguration.INITIALIZATION) {

            sendToUMS = updateRefs.contains('Update') || sendToUMS

            try {
                withCredentials([string(credentialsId: 'prodUniverseURL', variable: 'PROD_UMS_URL'),
                    string(credentialsId: 'devUniverseURL', variable: 'DEV_UMS_URL'),
                    string(credentialsId: 'imageServiceURL', variable: 'IS_URL')])
                {
                    UniverseURLProd = "${PROD_UMS_URL}"
                    UniverseURLDev = "${DEV_UMS_URL}"
                    ImageServiceURL = "${IS_URL}"
                    universeClientProd = new UniverseClient(this, UniverseURLProd, env, ImageServiceURL, ProducteName)
                    universeClientDev = new UniverseClient(this, UniverseURLDev, env, ImageServiceURL, ProducteName)

                    options.universeClientProd = universeClientProd
                    options.universeClientDev = universeClientDev
                }
                universeClientProd.tokenSetup()
                universeClientDev.tokenSetup()
            } catch (e) {
                println("[ERROR] Failed to setup token for UMS. Set sendToUms to false.")
                sendToUMS = false
                println(e.toString());
                println(e.getMessage());
            }

            gpusCount = 0
            renderPlatforms.split(';').each()
            { platform ->
                List tokens = platform.tokenize(':')
                if (tokens.size() > 1)
                {
                    gpuNames = tokens.get(1)
                    gpuNames.split(',').each()
                    {
                        gpusCount += 1
                    }
                }
            }

            def universePlatforms = convertPlatforms(renderPlatforms);

            def parallelExecutionType = TestsExecutionType.valueOf(parallelExecutionTypeString)
 
            println "Platforms: ${platforms}"
            println "Render platforms: ${renderPlatforms}"
            println "Tests: ${tests}"
            println "Tests package: ${testsPackage}"
            println "Tests execution type: ${parallelExecutionType}"
            println "Send to UMS: ${sendToUMS} "
            println "UMS platforms: ${universePlatforms}"

            String prRepoName = ""
            String prBranchName = ""
            if (mergeablePR) {
                String[] prInfo = mergeablePR.split(";")
                prRepoName = prInfo[0]
                prBranchName = prInfo[1]
            }

            options << [projectBranch:projectBranch,
                        testsBranch:testsBranch,
                        unitTestsBranch:unitTestsBranch,
                        updateRefs:updateRefs,
                        enableNotifications:enableNotifications,
                        PRJ_NAME:"RadeonProRenderCore",
                        PRJ_ROOT:"rpr-core",
                        BUILDER_TAG:'BuilderS',
                        TESTER_TAG:tester_tag,
                        slackChannel:"${SLACK_CORE_CHANNEL}",
                        renderDevice:renderDevice,
                        testsPackage:testsPackage,
                        tests:tests.replace(',', ' '),
                        executeBuild:true,
                        executeTests:true,
                        reportName:'Test_20Report',
                        TEST_TIMEOUT:150,
                        width:width,
                        gpusCount:gpusCount,
                        height:height,
                        iterations:iterations,
                        sendToUMS:sendToUMS,
                        universePlatforms: universePlatforms,
                        nodeRetry: nodeRetry,
                        problemMessageManager: problemMessageManager,
                        platforms:platforms,
                        renderPlatforms:renderPlatforms,
                        prRepoName:prRepoName,
                        prBranchName:prBranchName,
                        parallelExecutionType:parallelExecutionType,
                        collectTrackedMetrics:collectTrackedMetrics
                        ]
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy, options)
    }
    catch(e) 
    {
        currentBuild.result = "FAILURE"
        if (sendToUMS){
            universeClientProd.changeStatus(currentBuild.result)
            universeClientDev.changeStatus(currentBuild.result)
        }
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally
    {
        problemMessageManager.publishMessages()
    }
}
