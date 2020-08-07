import groovy.transform.Field
import UniverseClient
import groovy.json.JsonOutput;

@Field UniverseClient universeClient = new UniverseClient(this, "https://umsapi.cis.luxoft.com", env, "https://imgs.cis.luxoft.com", "AMD%20Radeon™%20ProRender%20for%203ds%20Max")


def getMaxPluginInstaller(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':

            if (options['isPreBuilt']) {

                println "[INFO] PluginWinSha: ${options['pluginWinSha']}"

                if (options['pluginWinSha']) {
                    if (fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options['pluginWinSha']}.msi")) {
                        println "[INFO] The plugin ${options['pluginWinSha']}.msi exists in the storage."
                    } else {
                        clearBinariesWin()

                        println "[INFO] The plugin does not exist in the storage. Downloading and copying..."
                        downloadPlugin(osName, "Max", options)

                        bat """
                            IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                            move RadeonProRender*.msi "${CIS_TOOLS}\\..\\PluginsBinaries\\${options['pluginWinSha']}.msi"
                        """
                    }
                } else {
                    clearBinariesWin()

                    println "[INFO] The plugin does not exist in the storage. PluginSha is unknown. Downloading and copying..."
                    downloadPlugin(osName, "Max", options)

                    bat """
                        IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                        move RadeonProRender*.msi "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.msi"
                    """
                }

            } else {
                if (fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options['productCode']}.msi")) {
                    println "[INFO] The plugin ${options['productCode']}.msi exists in the storage."
                } else {
                    clearBinariesWin()

                    println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                    unstash "appWindows"

                    bat """
                        IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                        move RadeonProRender*.msi "${CIS_TOOLS}\\..\\PluginsBinaries\\${options['productCode']}.msi"
                    """
                }
            }

            break;

        default:
            echo "[WARNING] ${osName} is not supported"
    }

}


def executeGenTestRefCommand(String osName, Map options)
{
    try
    {
        //for update existing manifest file
        receiveFiles("${options.REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
    }
    catch(e)
    {
        println("baseline_manifest.json not found")
    }

    dir('scripts')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                    make_results_baseline.bat
                """
                break;
            default:
                echo "[WARNING] ${osName} is not supported"
        }
    }
}


def executeTestCommand(String osName, String asicName, Map options)
{
    def test_timeout
    if (options.testsPackage.endsWith('.json')) 
    {
        test_timeout = options.timeouts["${options.testsPackage}"]
    } 
    else
    {
        test_timeout = options.timeouts["${options.tests}"]
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
                dir('scripts')
                {
                    bat"""
                    run.bat ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" ${options.toolVersion} ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} >> ../${options.stageName}.log  2>&1
                    """
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

        timeout(time: "5", unit: 'MINUTES') {
            try {
                cleanWS(osName)

                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_max.git')

                println "[INFO] Preparing successfully finished."

            } catch(e) {
                println("[ERROR] Failed to prepare test group")
                println(e.toString())
                throw e
            }
        }

        downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/MaxAssets/", 'MaxAssets')

        try {
            Boolean newPluginInstalled = false
            timeout(time: "15", unit: 'MINUTES') {
                getMaxPluginInstaller(osName, options)
                newPluginInstalled = installMSIPlugin(osName, 'Max', options)
                println "[INFO] Install function return ${newPluginInstalled}"
            }
        } catch(e) {
            println(e.toString())
            println("[ERROR] Failed to install plugin on ${env.NODE_NAME}.")
            throw e
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, options.stageName)

        if(options['updateRefs'])
        {
            executeTestCommand(osName, asicName, options)
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else
        {
            try {
                println "[INFO] Downloading reference images for ${options.tests}"
                receiveFiles("${REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/')
                }
            } catch (e) {
                println("[WARNING] Baseline doesn't exist.")
            }
            executeTestCommand(osName, asicName, options)
        }
    } catch (e) {
        if (options.currentTry < options.nodeReallocateTries) {
            stashResults = false
        }
        println(e.toString())
        println(e.getMessage())
        options.failureMessage = "Failed during testing: ${asicName}-${osName}"
        options.failureError = e.getMessage()
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        if (stashResults) {
            dir('Work')
            {
                if (fileExists("Results/Max/session_report.json")) {
                    
                    def sessionReport = null
                    sessionReport = readJSON file: 'Results/Max/session_report.json'

                    // if none launched tests - mark build failed
                    if (sessionReport.summary.total == 0)
                    {
                        options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                        currentBuild.result = "FAILED"
                    }

                    if (options.sendToUMS)
                    {
                        universeClient.stage("Tests-${osName}-${asicName}", "end")
                    }

                    echo "Stashing test results to : ${options.testResultsName}"
                    stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

                    // deinstalling broken addon & reallocate node if there are still attempts
                    if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped) {
                        if (sessionReport.summary.total != sessionReport.summary.skipped){
                            collectCrashInfo(osName, options)
                            installMSIPlugin(osName, "Max", options, false, true)
                            if (options.currentTry < options.nodeReallocateTries) {
                                throw new Exception("All tests crashed")
                            }
                        }
                    }
                }
            }
        } else {
            println "[INFO] Task ${options.tests} will be retried."
        }
    }
}

def executeBuildWindows(Map options)
{
    dir("RadeonProRenderMaxPlugin/Package")
    {
        bat """
            build_windows_installer.cmd >> ../../${STAGE_NAME}.log  2>&1
        """

        if(options.branch_postfix)
        {
            bat """
                rename RadeonProRender*msi *.(${options.branch_postfix}).msi
            """
        }

        archiveArtifacts "RadeonProRender3dsMax*.msi"
        String BUILD_NAME = options.branch_postfix ? "RadeonProRender3dsMax_${options.pluginVersion}.(${options.branch_postfix}).msi" : "RadeonProRender3dsMax_${options.pluginVersion}.msi"
        rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""

        bat """
            rename  RadeonProRender*.msi RadeonProRenderMax.msi
        """

        bat """
            echo import msilib >> getMsiProductCode.py
            echo db = msilib.OpenDatabase(r'RadeonProRenderMax.msi', msilib.MSIDBOPEN_READONLY) >> getMsiProductCode.py
            echo view = db.OpenView("SELECT Value FROM Property WHERE Property='ProductCode'") >> getMsiProductCode.py
            echo view.Execute(None) >> getMsiProductCode.py
            echo print(view.Fetch().GetString(1)) >> getMsiProductCode.py
        """

        options.productCode = python3("getMsiProductCode.py").split('\r\n')[2].trim()[1..-2]

        println "[INFO] Built MSI product code: ${options.productCode}"

        stash includes: 'RadeonProRenderMax.msi', name: 'appWindows'
    }
}


def executeBuild(String osName, Map options)
{
    cleanWS(osName)
    if (options.sendToUMS){
        universeClient.stage("Build-" + osName , "begin")
    }
    try {
        dir('RadeonProRenderMaxPlugin')
        {
            checkOutBranchOrScm(options.projectBranch, options.projectRepo)
        }

        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows':
            executeBuildWindows(options);
            break;
        default:
            echo "[WARNING] ${osName} is not supported"
        }

        //stash includes: 'Bin/**/*', name: "app${osName}"
    }
    catch (e) {
        currentBuild.result = "FAILED"
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
    // manual job with prebuilt plugin
    if (options.isPreBuilt) {
        println "[INFO] Build was detected as prebuilt. Build stage will be skipped"
        currentBuild.description = "<b>Project branch:</b> Prebuilt plugin<br/>"
        options.executeBuild = false
        options.executeTests = true
    // manual job
    } else if (options.forceBuild) {
        println "[INFO] Manual job launch detected"
        options['executeBuild'] = true
        options['executeTests'] = true
    // auto job
    } else {
        if (env.CHANGE_URL) {
            println "[INFO] Branch was detected as Pull Request"
            options['executeBuild'] = true
            options['executeTests'] = true
            options['testsPackage'] = "regression.json"
        } else if (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop") {
           println "[INFO] ${env.BRANCH_NAME} branch was detected"
           options['executeBuild'] = true
           options['executeTests'] = true
           options['testsPackage'] = "regression.json"
        } else {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
            options['testsPackage'] = "regression.json"
        }
    }

    // branch postfix
    options["branch_postfix"] = ""
    if(env.BRANCH_NAME && env.BRANCH_NAME == "master")
    {
        options["branch_postfix"] = "release"
    }
    else if(env.BRANCH_NAME && env.BRANCH_NAME != "master" && env.BRANCH_NAME != "develop")
    {
        options["branch_postfix"] = env.BRANCH_NAME.replace('/', '-')
    }
    else if(options.projectBranch && options.projectBranch != "master" && options.projectBranch != "develop")
    {
        options["branch_postfix"] = options.projectBranch.replace('/', '-')
    }

    if (!options['isPreBuilt']) {
        dir('RadeonProRenderMaxPlugin')
        {
            checkOutBranchOrScm(options.projectBranch, options.projectRepo, true)

            options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
            options.commitMessage = bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim().replace('\n', '')
            options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            options.commitShortSHA = options.commitSHA[0..6]

            println "The last commit was written by ${options.commitAuthor}."
            println "Commit message: ${options.commitMessage}"
            println "Commit SHA: ${options.commitSHA}"
            println "Commit shortSHA: ${options.commitShortSHA}"

            if (options.projectBranch){
                currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
            } else {
                currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
            }

            options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderMaxPlugin\\version.h", '#define VERSION_STR')

            if (options['incrementVersion']) {
                if(env.BRANCH_NAME == "develop" && options.commitAuthor != "radeonprorender") {

                    println "[INFO] Incrementing version of change made by ${options.commitAuthor}."
                    println "[INFO] Current build version: ${options.pluginVersion}"

                    def new_version = version_inc(options.pluginVersion, 3)
                    println "[INFO] New build version: ${new_version}"
                    version_write("${env.WORKSPACE}\\RadeonProRenderMaxPlugin\\version.h", '#define VERSION_STR', new_version)

                    options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderMaxPlugin\\version.h", '#define VERSION_STR')
                    println "[INFO] Updated build version: ${options.pluginVersion}"

                    bat """
                      git add version.h
                      git commit -m "buildmaster: version update to ${options.pluginVersion}"
                      git push origin HEAD:develop
                    """

                    //get commit's sha which have to be build
                    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
                    options.projectBranch = options.commitSHA
                    println "[INFO] Project branch hash: ${options.projectBranch}"
                }
                else
                {
                    if(options.commitMessage.contains("CIS:BUILD"))
                    {
                        options['executeBuild'] = true
                    }

                    if(options.commitMessage.contains("CIS:TESTS"))
                    {
                        options['executeBuild'] = true
                        options['executeTests'] = true
                    }
                }
            }

            currentBuild.description += "<b>Version:</b> ${options.pluginVersion}<br/>"
            currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
            currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
            currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"
        }
    }

    if (env.BRANCH_NAME && (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop")) {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25']]]);
    } else if (env.BRANCH_NAME && env.BRANCH_NAME != "master" && env.BRANCH_NAME != "develop") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else if (env.JOB_NAME == "RadeonProRenderMaxPlugin-WeeklyFull") {
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

    dir('jobs_test_max')
    {
        checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_max.git')

        if(options.testsPackage != "none")
        {
            if(options.testsPackage.endsWith('.json'))
            {
                def testsByJson = readJSON file: "jobs/${options.testsPackage}"
                testsByJson.each() {
                    options.groupsUMS << "${it.key}"
                }
                options.splitTestsExecution = false
                options.timeouts = ["regression.json": options.REGRESSION_TIMEOUT + options.ADDITIONAL_XML_TIMEOUT]
            }
            else 
            {
                String tempTests = readFile("jobs/${options.testsPackage}")
                tempTests.split("\n").each {
                    // TODO: fix: duck tape - error with line ending
                    def test_group = "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
                    tests << test_group
                    def xml_timeout = utils.getTimeoutFromXML(this, "${test_group}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${test_group}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }
                options.tests = tests
                options.testsPackage = "none"
                options.groupsUMS = tests
            }
        }
        else
        {
            options.tests.split(" ").each()
            {
                tests << "${it}"
                def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
            }
            options.tests = tests
            options.groupsUMS = tests
        }
    }

    println "timeouts: ${options.timeouts}"

    if(options.splitTestsExecution) {
        options.testsList = options.tests
    }
    else {
        options.testsList = ['']
        options.tests = tests.join(" ")
    }

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
            println(e)
        }
    }

}

def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_max.git')

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
                String executionType
                if (options.testsPackage.endsWith('.json')) {
                    executionType = 'regression'
                } else if (options.splitTestsExecution) {
                    executionType = 'split_execution'
                } else {
                    executionType = 'default'
                }

                dir("jobs_launcher") {
                    bat """
                    count_lost_tests.bat \"${lostStashes}\" .. ..\\summaryTestResults ${executionType} \"${options.tests}\"
                    """
                }
            } catch (e) {
                println("[ERROR] Can't generate number of lost tests")
            }

            String branchName = env.BRANCH_NAME ?: options.projectBranch
            try
            {
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"])
                {
                    dir("jobs_launcher") {
                        def retryInfo = JsonOutput.toJson(options.nodeRetry)
                        if (options['isPreBuilt'])
                        {
                            bat """
                            build_reports.bat ..\\summaryTestResults ${escapeCharsByUnicode("3ds Max")} "PreBuilt" "PreBuilt" "PreBuilt" \"${escapeCharsByUnicode(retryInfo.toString())}\"
                            """
                        }
                        else
                        {
                            bat """
                            build_reports.bat ..\\summaryTestResults ${escapeCharsByUnicode("3ds Max")} ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\" \"${escapeCharsByUnicode(retryInfo.toString())}\"
                            """
                        }
                    }
                }
            } catch(e) {
                println("ERROR during report building")
                println(e.toString())
                println(e.getMessage())
            }

            try
            {
                dir("jobs_launcher") {
                    bat "get_status.bat ..\\summaryTestResults"
                }
            }
            catch(e)
            {
                println("ERROR during slack status generation")
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
                println("ERROR during archiving launcher.engine.log")
                println(e.toString())
                println(e.getMessage())
            }

            try
            {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                if (summaryReport.error > 0) {
                    println("Some tests crashed")
                    currentBuild.result="FAILED"
                }
                else if (summaryReport.failed > 0) {
                    println("Some tests failed")
                    currentBuild.result="UNSTABLE"
                } else {
                    currentBuild.result="SUCCESS"
                }
            }
            catch(e)
            {
                println("CAN'T GET TESTS STATUS")
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

            publishHTML([allowMissing: false,
                         alwaysLinkToLastBuild: false,
                         keepAll: true,
                         reportDir: 'summaryTestResults',
                         reportFiles: 'summary_report.html, performance_report.html, compare_report.html',
                         reportName: 'Test Report',
                         reportTitles: 'Summary Report, Performance Report, Compare Report'])

            if (options.sendToUMS) {
                try {
                    String status = currentBuild.result ?: 'SUCCESSFUL'
                    universeClient.changeStatus(status)
                } catch (e) {
                    println(e.getMessage())
                }
            }
        }
    }
    catch (e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        throw e
    }
    finally
    {}
}

def appendPlatform(String filteredPlatforms, String platform) {
    if (filteredPlatforms)
    {
        filteredPlatforms +=  ";" + platform
    } 
    else 
    {
        filteredPlatforms += platform
    }
    return filteredPlatforms
}

def call(String projectRepo = "git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderMaxPlugin.git",
        String projectBranch = "",
        String testsBranch = "master",
        String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI',
        Boolean updateRefs = false,
        Boolean enableNotifications = true,
        Boolean incrementVersion = true,
        String renderDevice = "2",
        String testsPackage = "",
        String tests = "",
        String toolVersion = "2021",
        Boolean forceBuild = false,
        Boolean splitTestsExecution = true,
        Boolean sendToUMS = true,
        String resX = '0',
        String resY = '0',
        String SPU = '25',
        String iter = '50',
        String theshold = '0.05',
        String customBuildLinkWindows = "",
        String tester_tag = 'Max')
{
    resX = (resX == 'Default') ? '0' : resX
    resY = (resY == 'Default') ? '0' : resY
    SPU = (SPU == 'Default') ? '60' : SPU
    iter = (iter == 'Default') ? '120' : iter
    theshold = (theshold == 'Default') ? '0.01' : theshold
    def nodeRetry = []
    try
    {
        Boolean isPreBuilt = customBuildLinkWindows.length() > 0

        if (isPreBuilt)
        {
            //remove platforms for which pre built plugin is not specified
            String filteredPlatforms = ""

            platforms.split(';').each()
            { platform ->
                List tokens = platform.tokenize(':')
                String platformName = tokens.get(0)

                switch(platformName)
                {
                case 'Windows':
                    if (customBuildLinkWindows)
                    {
                        filteredPlatforms = appendPlatform(filteredPlatforms, platform)
                    }
                    break;
                }
            }

            platforms = filteredPlatforms
        }

        // if (tests == "" && testsPackage == "none") { currentBuild.setKeepLog(true) }

        String PRJ_NAME="RadeonProRenderMaxPlugin"
        String PRJ_ROOT="rpr-plugins"
        gpusCount = 0
        platforms.split(';').each()
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

        def universePlatforms = convertPlatforms(platforms);

        println "Platforms: ${platforms}"
        println "Tests: ${tests}"
        println "Tests package: ${testsPackage}"
        println "Split tests execution: ${splitTestsExecution}"
        println "UMS platforms: ${universePlatforms}"

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectRepo:projectRepo,
                                projectBranch:projectBranch,
                                testsBranch:testsBranch,
                                updateRefs:updateRefs,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                incrementVersion:incrementVersion,
                                renderDevice:renderDevice,
                                testsPackage:testsPackage,
                                tests:tests,
                                toolVersion:toolVersion,
                                executeBuild:false,
                                executeTests:isPreBuilt,
                                isPreBuilt:isPreBuilt,
                                forceBuild:forceBuild,
                                reportName:'Test_20Report',
                                splitTestsExecution:splitTestsExecution,
                                sendToUMS: sendToUMS,
                                gpusCount:gpusCount,
                                TEST_TIMEOUT:180,
                                ADDITIONAL_XML_TIMEOUT:15,
                                REGRESSION_TIMEOUT:60,
                                TESTER_TAG:tester_tag,
                                universePlatforms: universePlatforms,
                                resX: resX,
                                resY: resY,
                                SPU: SPU,
                                iter: iter,
                                theshold: theshold,
                                customBuildLinkWindows: customBuildLinkWindows,
                                nodeRetry: nodeRetry
                                ])
        }
        catch (e) {
            currentBuild.result = "FAILED"
            if (sendToUMS){
                universeClient.changeStatus(currentBuild.result)
            }
            println(e.toString());
            println(e.getMessage());

            throw e
        }
}
