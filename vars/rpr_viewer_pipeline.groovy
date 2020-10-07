import UniverseClient
import groovy.transform.Field
import groovy.json.JsonOutput;
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import TestsExecutionType

@Field UniverseClient universeClient = new UniverseClient(this, "https://umsapi.cis.luxoft.com", env, "https://imgs.cis.luxoft.com", "AMD%20Radeon™%20ProRender%20Viewer")
@Field ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)


def getViewerTool(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':

            if (!fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip")) {

                clearBinariesWin()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "appWindows"
                
                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    copy RprViewer_Windows.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip"
                """

            } else {
                println "[INFO] The plugin ${options.pluginWinSha}.zip exists in the storage."
                bat """
                    copy "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip" RprViewer_Windows.zip
                """
            }

            unzip zipFile: "RprViewer_Windows.zip", dir: "RprViewer", quiet: true

            break;

        case 'OSX':
            println "OSX isn't supported"
            break;

        default:
            
            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip")) {

                clearBinariesUnix()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "appUbuntu18"
                
                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                    cp RprViewer_Ubuntu18.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip"
                """ 

            } else {

                println "[INFO] The plugin ${options.pluginUbuntuSha}.zip exists in the storage."
                sh """
                    cp "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip" RprViewer_Ubuntu18.zip
                """
            }

            unzip zipFile: "RprViewer_Ubuntu18.zip", dir: "RprViewer", quiet: true

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

def executeTestCommand(String osName, String asicName, Map options)
{
    def test_timeout = options.timeouts["${options.tests}"]
    String testsNames = options.tests
    String testsPackageName = options.testsPackage
    if (options.testsPackage != "none" && !options.isPackageSplitted) {
        if (options.parsedTests.contains(".json")) {
            // if tests package isn't splitted and it's execution of this package - replace test group for non-splitted package by empty string
            testsNames = ""
        } else {
            // if tests package isn't splitted and it isn't execution of this package - replace tests package by empty string
            testsPackageName = ""
        }
    }

    println "Set timeout to ${test_timeout}"

    timeout(time: test_timeout, unit: 'MINUTES') { 

        build_id = "none"
        job_id = "none"
        if (options.sendToUMS && universeClient.build != null){
            build_id = universeClient.build["id"]
            job_id = universeClient.build["job_id"]
        }
        withCredentials([usernamePassword(credentialsId: 'image_service', usernameVariable: 'IS_USER', passwordVariable: 'IS_PASSWORD'),
            usernamePassword(credentialsId: 'universeMonitoringSystem', usernameVariable: 'UMS_USER', passwordVariable: 'UMS_PASSWORD')])
        {
            withEnv(["UMS_USE=${options.sendToUMS}", "UMS_BUILD_ID=${build_id}", "UMS_JOB_ID=${job_id}",
                "UMS_URL=${universeClient.url}", "UMS_ENV_LABEL=${osName}-${asicName}", "IS_URL=${universeClient.is_url}",
                "UMS_LOGIN=${UMS_USER}", "UMS_PASSWORD=${UMS_PASSWORD}", "IS_LOGIN=${IS_USER}", "IS_PASSWORD=${IS_PASSWORD}"])
            {
                switch(osName)
                {
                case 'Windows':
                    String driverPostfix = asicName.endsWith('_Beta') ? " Beta Driver" : ""

                    dir('scripts')
                    {
                        bat """
                        set CIS_RENDER_DEVICE=%CIS_RENDER_DEVICE%${driverPostfix}
                        run.bat \"${testsPackageName}\" \"${testsNames}\" ${options.testCaseRetries} ${options.updateRefs} 1>> \"../${options.stageName}_${options.currentTry}.log\"  2>&1
                        """
                    }
                    break;

                case 'OSX':
                    echo "OSX is not supported"
                    break;

                default:
                    dir('scripts')
                    {
                        withEnv(["LD_LIBRARY_PATH=../RprViewer/engines/hybrid:\$LD_LIBRARY_PATH"]) {
                            sh """
                            chmod +x ../RprViewer/RadeonProViewer
                            chmod +x run.sh
                            ./run.sh \"${testsPackageName}\" \"${testsNames}\" ${options.testCaseRetries} ${options.updateRefs} 1>> \"../${options.stageName}_${options.currentTry}.log\"  2>&1
                            """
                        }
                    }
                }
            }
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    if (options.sendToUMS){
        universeClient.stage("Tests-${osName}-${asicName}", "begin")
    }
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {
        try {
            timeout(time: "10", unit: 'MINUTES') {
                GithubNotificator.updateStatus("Test", options['stageName'], "pending", env, options, "Downloading tests repository.", "${BUILD_URL}")
                cleanWS(osName)
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_rprviewer.git')
                getViewerTool(osName, options)
            }
        } catch (e) {
            if (utils.isTimeoutExceeded(e)) {
                throw new ExpectedExceptionWrapper("Failed to download tests repository due to timeout.", e)
            } else {
                throw new ExpectedExceptionWrapper("Failed to download tests repository.", e)
            }
        }

        try {
            GithubNotificator.updateStatus("Test", options['stageName'], "pending", env, options, "Downloading test scenes.", "${BUILD_URL}")
            downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/Assets/", 'RprViewer')
        } catch (e) {
            throw new ExpectedExceptionWrapper("Failed to download test scenes.", e)
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, "", options.currentTry)

        try {
            if(options['updateRefs'].contains('Update')) {
                executeTestCommand(osName, asicName, options)
                executeGenTestRefCommand(osName, options, options['updateRefs'].contains('clean'))
                sendFiles('./Work/GeneratedBaselines/', REF_PATH_PROFILE)
                // delete generated baselines when they're sent 
                switch(osName) {
                    case 'Windows':
                        bat "if exist Work\\GeneratedBaselines rmdir /Q /S Work\\GeneratedBaselines"
                        break;
                    default:
                        sh "rm -rf ./Work/GeneratedBaselines"        
                }
            } else {
                try {
                    GithubNotificator.updateStatus("Test", options['stageName'], "pending", env, options, "Downloading reference images.", "${BUILD_URL}")
                    String baseline_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_viewer_autotests_baselines" : "/mnt/c/TestResources/rpr_viewer_autotests_baselines"
                    println "[INFO] Downloading reference images for ${options.tests}"
                    options.tests.split(" ").each() {
                        if (it.contains(".json")) {
                            receiveFiles("${REF_PATH_PROFILE}/", baseline_dir)
                        } else {
                            receiveFiles("${REF_PATH_PROFILE}/${it}", baseline_dir)
                        }
                    }
                } 
                catch (e)
                {
                    println("Baseline doesn't exist.")
                }
                GithubNotificator.updateStatus("Test", options['stageName'], "pending", env, options, "Executing tests.", "${BUILD_URL}")
                executeTestCommand(osName, asicName, options)
            }
            options.executeTestsFinished = true
        } catch (e) {
            throw new ExpectedExceptionWrapper("An error occurred while running tests. Please contact support.", e)
        }

    } catch (e) {
        if (options.currentTry < options.nodeReallocateTries) {
            stashResults = false
        } 
        println(e.toString())
        println(e.getMessage())
        if (e instanceof ExpectedExceptionWrapper) {
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", env, options, e.getMessage(), "${BUILD_URL}")
            throw e
        } else {
            String errorMessage = "The reason is not automatically identified. Please contact support."
            GithubNotificator.updateStatus("Test", options['stageName'], "failure", env, options, errorMessage, "${BUILD_URL}")
            throw new ExpectedExceptionWrapper(errorMessage, e)
        }
    }
    finally {
        try {
            archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
            if (stashResults) {
                dir('Work')
                {
                    if (fileExists("Results/RprViewer/session_report.json")) {

                        def sessionReport = null
                        sessionReport = readJSON file: 'Results/RprViewer/session_report.json'

                        if (options.sendToUMS)
                        {
                            universeClient.stage("Tests-${osName}-${asicName}", "end")
                        }

                        if (sessionReport.summary.error > 0) {
                            GithubNotificator.updateStatus("Test", options['stageName'], "failure", env, options, "Some tests were marked as error. Check the report for details.", "${BUILD_URL}")
                        } else if (sessionReport.summary.failed > 0) {
                            GithubNotificator.updateStatus("Test", options['stageName'], "success", env, options, "Some tests were marked as failed. Check the report for details.", "${BUILD_URL}")
                        } else {
                            GithubNotificator.updateStatus("Test", options['stageName'], "success", env, options, "Tests completed successfully.", "${BUILD_URL}")
                        }

                        echo "Stashing test results to : ${options.testResultsName}"
                        stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

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
                    GithubNotificator.updateStatus("Test", options['stageName'], "failure", env, options, e.getMessage(), "${BUILD_URL}")
                    throw e
                } else {
                    String errorMessage = "An error occurred while saving test results. Please contact support."
                    GithubNotificator.updateStatus("Test", options['stageName'], "failure", env, options, , "${BUILD_URL}")
                    throw new ExpectedExceptionWrapper(errorMessage, e)
                }
            }
        }
    }
}

def executeBuildWindows(Map options)
{
    GithubNotificator.updateStatus("Build", "Windows", "pending", env, options, "Building RPRViewer package.", "${BUILD_URL}/artifact/Build-Windows.log")
    try {
        bat"""
            cmake . -B build -G "Visual Studio 15 2017" -A x64 >> ${STAGE_NAME}.log 2>&1
            cmake --build build --target RadeonProViewer --config Release >> ${STAGE_NAME}.log 2>&1
        """
    } catch (e) {
        String errorMessage = "Failed during RPRViewer building."
        GithubNotificator.updateStatus("Build", "Windows", "failure", env, options, errorMessage)
        throw e
    }

    try {
        bat"""
            mkdir ${options.DEPLOY_FOLDER}
            xcopy config.json ${options.DEPLOY_FOLDER}
            xcopy README.md ${options.DEPLOY_FOLDER}
            xcopy UIConfig.json ${options.DEPLOY_FOLDER}
            xcopy sky.hdr ${options.DEPLOY_FOLDER}
            xcopy build\\Viewer\\Release\\RadeonProViewer.exe ${options.DEPLOY_FOLDER}\\RadeonProViewer.exe*

            xcopy shaders ${options.DEPLOY_FOLDER}\\shaders /y/i/s

            mkdir ${options.DEPLOY_FOLDER}\\rml\\lib
            xcopy rml\\lib\\RadeonML-DirectML.dll ${options.DEPLOY_FOLDER}\\rml\\lib\\RadeonML-DirectML.dll*
            xcopy rif\\models ${options.DEPLOY_FOLDER}\\rif\\models /s/i/y
            xcopy rif\\lib ${options.DEPLOY_FOLDER}\\rif\\lib /s/i/y
            del /q ${options.DEPLOY_FOLDER}\\rif\\lib\\*.lib
        """
    } catch (e) {
        String errorMessage = "Failed during artifacts copying building."
        GithubNotificator.updateStatus("Build", "Windows", "failure", env, options, errorMessage)
        throw e
    }

    //temp fix
    bat"""
        xcopy build\\viewer\\engines ${options.DEPLOY_FOLDER}\\engines /s/i/y
    """

    def controlFiles = ['config.json', 'UIConfig.json', 'sky.hdr', 'RadeonProViewer.exe', 'rml/lib/RadeonML-DirectML.dll']
        controlFiles.each() {
        if (!fileExists("${options.DEPLOY_FOLDER}/${it}")) {
            error "Not found ${it}"
        }
    }

    zip archive: true, dir: "${options.DEPLOY_FOLDER}", glob: '', zipFile: "RprViewer_Windows.zip"
    stash includes: "RprViewer_Windows.zip", name: "appWindows"
    options.pluginWinSha = sha1 "RprViewer_Windows.zip"

    GithubNotificator.updateStatus("Build", "Windows", "success", env, options, "RPRViewer package was successfully built and published.", "${BUILD_URL}/artifact/RprViewer_Windows.zip")
}


def executeBuildLinux(Map options)
{
    GithubNotificator.updateStatus("Build", "Ubuntu18", "pending", env, options, "Building RPRViewer package.", "${BUILD_URL}/artifact/Build-Windows.log")
    try {
        sh """
            mkdir build
            cd build
            cmake .. >> ../${STAGE_NAME}.log 2>&1
            make >> ../${STAGE_NAME}.log 2>&1
        """
    } catch (e) {
        String errorMessage = "Failed during RPRViewer building."
        GithubNotificator.updateStatus("Build", "Ubuntu18", "failure", env, options, errorMessage)
        throw e
    }

    try {
        sh """
            mkdir ${options.DEPLOY_FOLDER}
            cp config.json ${options.DEPLOY_FOLDER}
            cp README.md ${options.DEPLOY_FOLDER}
            cp UIConfig.json ${options.DEPLOY_FOLDER}
            cp sky.hdr ${options.DEPLOY_FOLDER}
            cp build/viewer/RadeonProViewer ${options.DEPLOY_FOLDER}/RadeonProViewer

            cp -rf shaders ${options.DEPLOY_FOLDER}/shaders

            mkdir ${options.DEPLOY_FOLDER}/rif
            cp -rf rif/models ${options.DEPLOY_FOLDER}/rif/models
            cp -rf rif/lib ${options.DEPLOY_FOLDER}/rif/lib

            cp -rf build/viewer/engines ${options.DEPLOY_FOLDER}/engines
        """
    } catch (e) {
        String errorMessage = "Failed during artifacts copying building."
        GithubNotificator.updateStatus("Build", "Ubuntu18", "failure", env, options, errorMessage)
        throw e
    }

    zip archive: true, dir: "${options.DEPLOY_FOLDER}", glob: '', zipFile: "RprViewer_Ubuntu18.zip"
    stash includes: "RprViewer_Ubuntu18.zip", name: "appUbuntu18"
    options.pluginUbuntuSha = sha1 "RprViewer_Ubuntu18.zip"

    GithubNotificator.updateStatus("Build", "Ubuntu18", "success", env, options, "RPRViewer package was successfully built and published.", "${BUILD_URL}/artifact/RprViewer_Ubuntu18.zip")
}

def executeBuild(String osName, Map options)
{
    if (options.sendToUMS){
        universeClient.stage("Build-" + osName , "begin")
    }

    try {
        try {
            GithubNotificator.updateStatus("Build", osName, "pending", env, options, "Downloading RPRViewer repository.")
            checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        } catch (e) {
            String errorMessage = "Failed to download RPRViewer repository."
            GithubNotificator.updateStatus("Build", osName, "failure", env, options, errorMessage)
            problemMessageManager.saveSpecificFailReason(errorMessage, "Build", osName)
            throw e
        }

        outputEnvironmentInfo(osName)

        try {
            switch(osName)
            {
            case 'Windows':
                executeBuildWindows(options);
                break;
            case 'OSX':
                println "OSX isn't supported."
                break;
            default:
                executeBuildLinux(options);
            }
        } catch (e) {
            String errorMessage = "Failed to build RPRViewer package."
            GithubNotificator.updateStatus("Build", osName, "failure", env, options, errorMessage)
            problemMessageManager.saveSpecificFailReason(errorMessage, "Build", osName)
            throw e
        }
    }
    catch (e) {
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
    if (options.sendToUMS){
        universeClient.stage("Build-" + osName, "end")
    }
}

def executePreBuild(Map options)
{
    if (env.CHANGE_URL) {
        echo "[INFO] Branch was detected as Pull Request"
        options.testsPackage = "PR.json"
        GithubNotificator githubNotificator = new GithubNotificator(this, pullRequest)
        options.githubNotificator = githubNotificator
        githubNotificator.initPreBuild("${BUILD_URL}")
    } else if(env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.testsPackage = "master.json"
    } else if(env.BRANCH_NAME) {
        options.testsPackage = "smoke.json"
    }

    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'], true)
    } catch (e) {
        String errorMessage = "Failed to download RPRViewer repository."
        GithubNotificator.updateStatus("PreBuild", "Version increment", "error", env, options, errorMessage)
        problemMessageManager.saveSpecificFailReason(errorMessage, "PreBuild")
        throw e
    }

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim().replace('\n', '')
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"

    if (options.projectBranch){
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }

    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    if (env.BRANCH_NAME && (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop")) {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    } else if (env.BRANCH_NAME && env.BRANCH_NAME != "master" && env.BRANCH_NAME != "develop") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else if (env.JOB_NAME == "RadeonProViewer-WeeklyFull") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    }

    def tests = []
    options.timeouts = [:]
    options.groupsUMS = []

    try {
        dir('jobs_test_rprviewer')
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_rprviewer.git')

            options['testsBranch'] = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            println "[INFO] Test branch hash: ${options['testsBranch']}"

            def packageInfo

            if(options.testsPackage != "none") 
            {
                packageInfo = readJSON file: "jobs/${options.testsPackage}"
                options.isPackageSplitted = packageInfo["split"]
                // if it's build of manual job and package can be splitted - use list of tests which was specified in params (user can change list of tests before run build)
                if (options.forceBuild && options.isPackageSplitted && options.tests) {
                    options.testsPackage = "none"
                }
            }

            if(options.testsPackage != "none")
            {
                if (options.isPackageSplitted) {
                    println("[INFO] Tests package '${options.testsPackage}' can be splitted")
                } else {
                    // save tests which user wants to run with non-splitted tests package
                    if (options.tests) {
                        tests = options.tests.split(" ") as List
                        options.groupsUMS = tests.clone()
                    }
                    println("[INFO] Tests package '${options.testsPackage}' can't be splitted")
                }

                // modify name of tests package if tests package is non-splitted (it will be use for run package few time with different engines)
                String modifiedPackageName = "${options.testsPackage}~"
                packageInfo["groups"].each() {
                    if (options.isPackageSplitted) {
                        tests << it.key
                        options.groupsUMS << it.key
                    } else {
                        if (!tests.contains(it.key)) {
                            options.groupsUMS << it.key
                        } else {
                            // add duplicated group name in name of package group name for exclude it
                            modifiedPackageName = "${modifiedPackageName},${it.key}"
                        }
                    }
                }
                tests.each() {
                    def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }
                modifiedPackageName = modifiedPackageName.replace('~,', '~')

                if (options.isPackageSplitted) {
                    options.testsPackage = "none"
                } else {
                    options.testsPackage = modifiedPackageName
                    if (options.engines.count(",") > 0) {
                        options.engines.split(",").each { engine ->
                            tests << "${modifiedPackageName}-${engine}"
                        }
                    } else {
                        tests << modifiedPackageName
                    }
                    options.timeouts[options.testsPackage] = options.NON_SPLITTED_PACKAGE_TIMEOUT + options.ADDITIONAL_XML_TIMEOUT
                }
            } else {
                tests = options.tests.split(" ") as List
                tests.each() {
                    options.groupsUMS << it
                    def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }
            }
            options.tests = tests
        }
    } catch (e) {
        String errorMessage = "Failed to configurate tests."
        GithubNotificator.updateStatus("PreBuild", "Version increment", "error", env, options, errorMessage)
        problemMessageManager.saveSpecificFailReason(errorMessage, "PreBuild")
        throw e
    }

    if (env.CHANGE_URL) {
        options.githubNotificator.initPR(options, "${BUILD_URL}")
    }

    options.testsList = options.tests

    println "timeouts: ${options.timeouts}"

    if (options.sendToUMS)
    {
        try
        {
            // Universe : auth because now we in node
            // If use httpRequest in master slave will catch 408 error
            universeClient.tokenSetup()

            // create build ([OS-1:GPU-1, ... OS-N:GPU-N], ['Suite1', 'Suite2', ..., 'SuiteN'])
            universeClient.createBuild(options.universePlatforms, options.groupsUMS)
        }
        catch (e)
        {
            println(e.toString())
        }
    }

    GithubNotificator.updateStatus("PreBuild", "Version increment", "success", env, options, "PreBuild stage was successfully finished.")
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    try
    {
        if(options['executeTests'] && testResultList)
        {
            try {
                GithubNotificator.updateStatus("Deploy", "Building test report", "pending", env, options, "Preparing tests results.", "${BUILD_URL}")
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_rprviewer.git')
            } catch (e) {
                String errorMessage = "Failed to download tests repository."
                GithubNotificator.updateStatus("Deploy", "Building test report", "failure", env, options, errorMessage, "${BUILD_URL}")
                problemMessageManager.saveSpecificFailReason(errorMessage, "Deploy")
                throw e
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
                            echo "[ERROR] Failed to unstash ${it}"
                            lostStashes.add("'$it'".replace("testResult-", ""))
                            println(e.toString());
                            println(e.getMessage());
                        }

                    }
                }
            }

            try {
                dir("jobs_launcher") {
                    bat """
                    count_lost_tests.bat \"${lostStashes}\" .. ..\\summaryTestResults \"${options.splitTestsExecution}\" \"${options.testsPackage}\" \"${options.tests}\"
                    """
                }
            } catch (e) {
                println("[ERROR] Can't generate number of lost tests")
            }
            
            String branchName = env.BRANCH_NAME ?: options.projectBranch

            try {
                GithubNotificator.updateStatus("Deploy", "Building test report", "pending", env, options, "Building test report.", "${BUILD_URL}")
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"])
                {
                    dir("jobs_launcher") {
                        def retryInfo = JsonOutput.toJson(options.nodeRetry)
                        dir("..\\summaryTestResults") {
                            JSON jsonResponse = JSONSerializer.toJSON(retryInfo, new JsonConfig());
                            writeJSON file: 'retry_info.json', json: jsonResponse, pretty: 4
                        }
                        bat """
                        build_reports.bat ..\\summaryTestResults "RprViewer" ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
                        """
                    }
                }
            } catch(e) {
                String errorMessage = utils.getReportFailReason(e.getMessage())
                GithubNotificator.updateStatus("Deploy", "Building test report", "failure", env, options, errorMessage, "${BUILD_URL}")
                if (utils.isReportFailCritical(e.getMessage())) {
                    problemMessageManager.saveSpecificFailReason(errorMessage, "Deploy")
                    println("[ERROR] Failed to build test report.")
                    println(e.toString())
                    println(e.getMessage())
                    throw e
                } else {
                    currentBuild.result = "FAILURE"
                    problemMessageManager.saveGlobalFailReason(errorMessage)
                }
            }

            try
            {
                dir("jobs_launcher") {
                    bat "get_status.bat ..\\summaryTestResults"
                }
            }
            catch(e)
            {
                println("[ERROR] during slack status generation")
                println(e.toString())
                println(e.getMessage())
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

                    problemMessageManager.saveGlobalFailReason("Some tests marked as error")
                }
                else if (summaryReport.failed > 0) {
                    println("[INFO] Some tests marked as failed. Build result = UNSTABLE.")
                    currentBuild.result = "UNSTABLE"

                    problemMessageManager.saveUnstableReason("Some tests marked as failed")
                }
            }
            catch(e)
            {
                println(e.toString())
                println(e.getMessage())
                println("[ERROR] CAN'T GET TESTS STATUS")
                problemMessageManager.saveUnstableReason("Can't get tests status")
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

            try {
                GithubNotificator.updateStatus("Deploy", "Building test report", "pending", env, options, "Publishing test report.", "${BUILD_URL}")
                utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", \
                    "Test Report", "Summary Report, Performance Report, Compare Report")
                if (summaryTestResults) {
                    // add in description of status check information about tests statuses
                    // Example: Report was published successfully (passed: 69, failed: 11, error: 0)
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", env, options, "Report was published successfully. Results: passed - ${summaryTestResults.passed}, failed - ${summaryTestResults.failed}, error - ${summaryTestResults.error}.", "${BUILD_URL}/Test_20Report")
                } else {
                    GithubNotificator.updateStatus("Deploy", "Building test report", "success", env, options, "Report was published successfully.", "${BUILD_URL}/Test_20Report")
                }
            } catch (e) {
                String errorMessage = "Failed to publish test report."
                GithubNotificator.updateStatus("Deploy", "Building test report", "failure", env, options, errorMessage, "${BUILD_URL}")
                problemMessageManager.saveSpecificFailReason(errorMessage, "Deploy")
                throw e
            }

            if (options.sendToUMS) {
                try {
                    String status = currentBuild.result ?: 'SUCCESSFUL'
                    universeClient.changeStatus(status)
                }
                catch (e){
                    println(e.getMessage())
                }
            }
        }
    }
    catch(e)
    {
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}

def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_RadeonVII,AMD_RadeonVII_Beta,NVIDIA_RTX2080TI;Ubuntu18:AMD_RadeonVII',
         String updateRefs = 'No',
         Boolean enableNotifications = true,
         String testsPackage = "",
         String tests = "",
         Boolean splitTestsExecution = true,
         Boolean sendToUMS = true,
         String tester_tag = 'RprViewer',
         String parallelExecutionTypeString = "TakeAllNodes",
         Integer testCaseRetries = 2)
{
    def nodeRetry = []
    Map options = [:]

    try 
    {
        try 
        {
            String PRJ_ROOT='rpr-core'
            String PRJ_NAME='RadeonProViewer'
            String projectRepo='git@github.com:Radeon-Pro/RadeonProViewer.git'

            def universePlatforms = convertPlatforms(platforms);

            def parallelExecutionType = TestsExecutionType.valueOf(parallelExecutionTypeString)

            println "Platforms: ${platforms}"
            println "Tests: ${tests}"
            println "Tests package: ${testsPackage}"
            println "Tests execution type: ${parallelExecutionType}"
            println "UMS platforms: ${universePlatforms}"

            options << [projectBranch:projectBranch,
                        testsBranch:testsBranch,
                        updateRefs:updateRefs,
                        enableNotifications:enableNotifications,
                        PRJ_NAME:PRJ_NAME,
                        PRJ_ROOT:PRJ_ROOT,
                        projectRepo:projectRepo,
                        BUILDER_TAG:'BuilderViewer',
                        TESTER_TAG:tester_tag,
                        executeBuild:true,
                        executeTests:true,
                        splitTestsExecution:splitTestsExecution,
                        DEPLOY_FOLDER:"RprViewer",
                        testsPackage:testsPackage,
                        TEST_TIMEOUT:45,
                        ADDITIONAL_XML_TIMEOUT:15,
                        NON_SPLITTED_PACKAGE_TIMEOUT:45,
                        DEPLOY_TIMEOUT:45,
                        tests:tests,
                        nodeRetry: nodeRetry,
                        sendToUMS:sendToUMS,
                        universePlatforms: universePlatforms,
                        problemMessageManager: problemMessageManager,
                        platforms:platforms,
                        parallelExecutionType:parallelExecutionType,
                        testCaseRetries:testCaseRetries
                        ]
        } 
        catch(e)
        {
            problemMessageManager.saveGeneralFailReason("Failed initialization.", "Init")

            throw e
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy, options)
    }
    catch(e)
    {
        currentBuild.result = "FAILURE"
        if (sendToUMS){
            universeClient.changeStatus(currentBuild.result)
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
