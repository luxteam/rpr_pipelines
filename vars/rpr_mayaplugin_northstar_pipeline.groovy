import RBSProduction;
import groovy.json.JsonOutput;

def getMayaPluginInstaller(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':

            if (options['isPreBuilt']) {
                if (options.pluginWinSha) {
                    win_addon_name = options.pluginWinSha
                } else {
                    win_addon_name = "unknown"
                }
            } else {
                win_addon_name = options.productCode
            }

            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${win_addon_name}.msi")) {

                clearBinariesWin()

                if (options['isPreBuilt']) {
                    println "[INFO] The plugin does not exist in the storage. Downloading and copying..."
                    downloadPlugin(osName, "Maya", options)
                    win_addon_name = options.pluginWinSha
                } else {
                    println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                    unstash "appWindows"
                }

                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    move RadeonProRender*.msi "${CIS_TOOLS}\\..\\PluginsBinaries\\${win_addon_name}.msi"
                """

            } else {
                println "[INFO] The plugin ${win_addon_name}.msi exists in the storage."
            }

            break;

        case "OSX":

            if (!options.pluginOSXSha) {
                options.pluginOSXSha = "unknown"
            }

            if(!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.dmg"))
            {
                clearBinariesUnix()

                if (options['isPreBuilt']) {
                    println "[INFO] The plugin does not exist in the storage. Downloading and copying..."
                    downloadPlugin(osName, "Maya", options)
                    osx_addon_name = options.pluginOSXSha
                } else {
                    println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                    unstash "appOSX"
                }

                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                    mv RadeonProRenderMaya*.dmg "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.dmg"
                """

            } else {
                println "[INFO] The plugin ${options.pluginOSXSha}.dmg exists in the storage."
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
            // OSX 
            default:
                sh """
                ./make_results_baseline.sh
                """
                break;
        }
    }
}


def buildRenderCache(String osName, String toolVersion, String log_name)
{
    dir("scripts") {
        switch(osName) {
            case 'Windows':
                bat "build_rpr_cache.bat ${toolVersion} >> ..\\${log_name}.cb.log  2>&1"
                break;
            case 'OSX':
                sh "./build_rpr_cache.sh ${toolVersion} >> ../${log_name}.cb.log 2>&1"
                break;
            default:
                echo "[WARNING] ${osName} is not supported"
        }
    }
}

def executeTestCommand(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':
            dir('scripts')
            {
                bat """
                    run.bat ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} ${options.toolVersion} ${options.engine} >> ../${options.stageName}.log  2>&1
                """
            }
            break;
        case 'OSX':
            dir('scripts')
            {
                sh """
                    ./run.sh ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} ${options.toolVersion} ${options.engine} >> ../${options.stageName}.log 2>&1
                """
            }
            break;
        default:
            echo "[WARNING] ${osName} is not supported"
    }
}

def executeTests(String osName, String asicName, Map options)
{
    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true
    
    try {

        timeout(time: "5", unit: 'MINUTES') {
            try {

                cleanWS(osName)
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_maya.git')

                writeFile file: 'local_config.py', text: """original_render = 'tahoe'
                tool_name = 'maya'
                report_type = 'ct'"""

                // setTester in rbs
                if (options.sendToRBS) {
                    options.rbs_prod.setTester(options)
                }
            } catch(e) {
                println("[ERROR] Failed to prepare test group on ${env.NODE_NAME}")
                println(e.toString())
                throw e
            }
        }

        downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/MayaAssets/", 'MayaAssets')

        try {
            Boolean newPluginInstalled = false
            timeout(time: "15", unit: 'MINUTES') {
                getMayaPluginInstaller(osName, options)
                newPluginInstalled = installMSIPlugin(osName, 'Maya', options)
                println "[INFO] Install function on ${env.NODE_NAME} return ${newPluginInstalled}"
            }
            if (newPluginInstalled) {
                timeout(time: "5", unit: 'MINUTES') {
                    buildRenderCache(osName, options.toolVersion, options.stageName)
                    if(!fileExists("./Work/Results/Maya/cache_building.jpg")){
                        println "[ERROR] Failed to build cache on ${env.NODE_NAME}. No output image found."
                        throw new Exception("No output image during build cache")
                    }
                }
            }
        }
        catch(e) {
            println(e.toString())
            println("[ERROR] Failed to install plugin on ${env.NODE_NAME}.")
            // deinstalling broken addon
            installMSIPlugin(osName, "Maya", options, false, true)
            throw e
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"

        outputEnvironmentInfo(osName, options.stageName)

        if(options['updateRefs']) {
            executeTestCommand(osName, options)
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else {
            // RPR baseline
            try {
                println "[INFO] Downloading RPR-Tahoe-1.0 reference images for ${options.tests}"
                receiveFiles("${REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/first')
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/first')
                }
            } catch (e) {
                println("[WARNING] Baseline doesn't exist.")
            }
            // Northstar baseline
            REF_PATH_PROFILE="${REF_PATH_PROFILE}-NorthStar"
            try {
                println "[INFO] Downloading RPR-Tahoe-1.0 reference images for ${options.tests}"
                receiveFiles("${REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/second')
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/second')
                }
            } catch (e) {
                println("[WARNING] Baseline doesn't exist.")
            }

            executeTestCommand(osName, options)
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
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        if (stashResults) {
            dir('Work')
            {
                if (fileExists("Results/Maya/session_report.json")) {

                    def sessionReport = null
                    sessionReport = readJSON file: 'Results/Maya/session_report.json'

                    // if none launched tests - mark build failed
                    if (sessionReport.summary.total == 0)
                    {
                        options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                        currentBuild.result = "FAILED"
                    }

                    if (options.sendToRBS)
                    {
                        options.rbs_prod.sendSuiteResult(sessionReport, options)
                    }

                    echo "Stashing test results to : ${options.testResultsName}"
                    stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

                    // deinstalling broken addon & reallocate node if there are still attempts
                    if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped) {
                        if (sessionReport.summary.total != sessionReport.summary.skipped){
                            collectCrashInfo(osName, options)
                            installMSIPlugin(osName, "Maya", options, false, true)
                            if (options.currentTry < options.nodeReallocateTries) {
                                throw new Exception("All tests crashed")
                            } else {
                                println "Group skipped"
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
    dir('RadeonProRenderMayaPlugin\\MayaPkg')
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

        archiveArtifacts "RadeonProRender*.msi"
        String BUILD_NAME = options.branch_postfix ? "RadeonProRenderMaya_${options.pluginVersion}.(${options.branch_postfix}).msi" : "RadeonProRenderMaya_${options.pluginVersion}.msi"
        rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""

        bat """
            rename RadeonProRender*.msi RadeonProRenderMaya.msi
        """

        bat """
            echo import msilib >> getMsiProductCode.py
            echo db = msilib.OpenDatabase(r'RadeonProRenderMaya.msi', msilib.MSIDBOPEN_READONLY) >> getMsiProductCode.py
            echo view = db.OpenView("SELECT Value FROM Property WHERE Property='ProductCode'") >> getMsiProductCode.py
            echo view.Execute(None) >> getMsiProductCode.py
            echo print(view.Fetch().GetString(1)) >> getMsiProductCode.py
        """

        options.productCode = python3("getMsiProductCode.py").split('\r\n')[2].trim()[1..-2]

        println "[INFO] Built MSI product code: ${options.productCode}"

        stash includes: 'RadeonProRenderMaya.msi', name: 'appWindows'
    }
}

def executeBuildOSX(Map options)
{
    dir('RadeonProRenderMayaPlugin/MayaPkg')
    {
        sh """
            ./build_osx_installer.sh >> ../../${STAGE_NAME}.log 2>&1
        """

        dir('.installer_build')
        {
            if(options.branch_postfix)
            {
                sh"""
                    for i in RadeonProRender*; do name="\${i%.*}"; mv "\$i" "\${name}.(${options.branch_postfix})\${i#\$name}"; done
                """
            }

            archiveArtifacts "RadeonProRender*.dmg"
            String BUILD_NAME = options.branch_postfix ? "RadeonProRenderMaya_${options.pluginVersion}.(${options.branch_postfix}).dmg" : "RadeonProRenderMaya_${options.pluginVersion}.dmg"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/${BUILD_NAME}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""

            sh "cp RadeonProRender*.dmg RadeonProRenderMaya.dmg"
            stash includes: 'RadeonProRenderMaya.dmg', name: "appOSX"

            // TODO: detect ID of installed plugin
            options.productCode = "unknown"
            options.pluginOSXSha = sha1 'RadeonProRenderMaya.dmg'
        }
    }
}


def executeBuild(String osName, Map options)
{
    try {
        dir('RadeonProRenderMayaPlugin')
        {
            checkOutBranchOrScm(options.projectBranch, options.projectRepo)
        }

        options.branch_postfix = ""
        if(env.BRANCH_NAME && env.BRANCH_NAME == "master")
        {
            options.branch_postfix = "release"
        }
        else if(env.BRANCH_NAME && env.BRANCH_NAME != "master" && env.BRANCH_NAME != "develop")
        {
            options.branch_postfix = env.BRANCH_NAME.replace('/', '-')
        }
        else if(options.projectBranch && options.projectBranch != "master" && options.projectBranch != "develop")
        {
            options.branch_postfix = options.projectBranch.replace('/', '-')
        }

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
            echo "[WARNING] ${osName} is not supported"
        }
    } catch (e) {
        currentBuild.result = "FAILED"
        if (options.sendToRBS)
        {
            try {
                options.rbs_prod.setFailureStatus()
            } catch (err) {
                println(err)
            }
        }
        throw e
    }
    finally {
        archiveArtifacts "*.log"
    }
}

def executePreBuild(Map options)
{
    if (options['isPreBuilt'])
    {
        //plugin is pre built
        options['executeBuild'] = false
        options['executeTests'] = true
        return
    }

    // manual job
    if (options.forceBuild) {
        options.executeBuild = true
        options.executeTests = true
    // auto job
    } else {
        if (env.CHANGE_URL) {
            println "[INFO] Branch was detected as Pull Request"
            options.isPR = true
            options.executeBuild = true
            options.executeTests = true
            options.testsPackage = "regression.json"
        } else if (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop") {
           println "[INFO] ${env.BRANCH_NAME} branch was detected"
           options.executeBuild = true
           options.executeTests = true
           options.testsPackage = "regression.json"
        } else {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
            options.testsPackage = "smoke"
        }
    }
    
    dir('RadeonProRenderMayaPlugin')
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

        options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderMayaPlugin\\version.h", '#define PLUGIN_VERSION')

        if (options['incrementVersion']) {
            if(env.BRANCH_NAME == "develop" && options.commitAuthor != "radeonprorender") {

                println "[INFO] Incrementing version of change made by ${options.commitAuthor}."
                println "[INFO] Current build version: ${options.pluginVersion}"

                def new_version = version_inc(options.pluginVersion, 3)
                println "[INFO] New build version: ${new_version}"
                version_write("${env.WORKSPACE}\\RadeonProRenderMayaPlugin\\version.h", '#define PLUGIN_VERSION', new_version)
                
                options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderMayaPlugin\\version.h", '#define PLUGIN_VERSION')
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

    if (env.BRANCH_NAME && (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop")) {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25']]]);
    } else if (env.BRANCH_NAME && env.BRANCH_NAME != "master" && env.BRANCH_NAME != "develop") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else if (env.JOB_NAME == "RadeonProRenderMayaPlugin-WeeklyFull") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    }
    
    def tests = []
    options.groupsRBS = []

    if(options.testsPackage != "none")
    {
        dir('jobs_test_maya')
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_maya.git')
            // json means custom test suite. Split doesn't supported
            if(options.testsPackage.endsWith('.json'))
            {
                def testsByJson = readJSON file: "jobs/${options.testsPackage}"
                testsByJson.each() {
                    options.groupsRBS << "${it.key}"
                }
                options.splitTestsExecution = false
            }
            else {
                String tempTests = readFile("jobs/${options.testsPackage}")
                tempTests.split("\n").each {
                    // TODO: fix: duck tape - error with line ending
                    tests << "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
                }
                options.tests = tests
                options.testsPackage = "none"
                options.groupsRBS = tests
            }
        }
    }
    else {
        options.tests.split(" ").each() {
            tests << "${it}"
        }
        options.tests = tests
        options.groupsRBS = tests
    }

    if(options.splitTestsExecution) {
        options.testsList = options.tests
    }
    else {
        options.tests = tests.join(" ")
        options.testsList = ['']
    }

    if (options.sendToRBS)
    {
        try
        {
            options.rbs_prod.startBuild(options)
        }
        catch (e)
        {
            println(e.toString())
        }
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    cleanWS()
    try {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_maya.git')
            writeFile file: 'local_config.py', text: """original_render = 'tahoe'
                tool_name = 'maya'
                report_type = 'ct'"""

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
                            build_reports.bat ..\\summaryTestResults "Maya" "PreBuilt" "PreBuilt" "PreBuilt" \"${escapeCharsByUnicode(retryInfo.toString())}\"
                            """
                        }
                        else
                        {
                            bat """
                            build_reports.bat ..\\summaryTestResults "Maya" ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\" \"${escapeCharsByUnicode(retryInfo.toString())}\"
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

            if (options.sendToRBS) {
                try {
                    String status = currentBuild.result ?: 'SUCCESSFUL'
                    options.rbs_prod.finishBuild(options, status)
                }
                catch (e){
                    println(e.getMessage())
                }
            }
        }
    }
    catch (e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
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

def call(String projectRepo = "git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderMayaPlugin.git",
        String projectBranch = "",
        String testsBranch = "master",
        String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;OSX:AMD_RXVEGA',
        Boolean updateRefs = false,
        Boolean enableNotifications = false,
        Boolean incrementVersion = false,
        String renderDevice = "gpu",
        String testsPackage = "",
        String tests = "",
        String toolVersion = "2020",
        Boolean forceBuild = false,
        Boolean splitTestsExecution = true,
        Boolean sendToRBS = false,
        String resX = '0',
        String resY = '0',
        String SPU = '25',
        String iter = '50',
        String theshold = '0.05',
        String customBuildLinkWindows = "",
        String customBuildLinkOSX = "")
{
    resX = (resX == 'Default') ? '0' : resX
    resY = (resY == 'Default') ? '0' : resY
    SPU = (SPU == 'Default') ? '25' : SPU
    iter = (iter == 'Default') ? '50' : iter
    theshold = (theshold == 'Default') ? '0.05' : theshold
    def nodeRetry = []
    try
    {
        Boolean isPreBuilt = customBuildLinkWindows || customBuildLinkOSX

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
                case 'OSX':
                    if (customBuildLinkOSX)
                    {
                        filteredPlatforms = appendPlatform(filteredPlatforms, platform)
                    }
                    break;
                }
            }

            platforms = filteredPlatforms
        }

        // if (tests == "" && testsPackage == "none") { currentBuild.setKeepLog(true) }
        String PRJ_NAME="RadeonProRenderMayaPlugin"
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

        rbs_prod = new RBSProduction(this, "Maya", env.JOB_NAME, env)

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
                                sendToRBS:sendToRBS,
                                gpusCount:gpusCount,
                                TEST_TIMEOUT:120,
                                DEPLOY_TIMEOUT:120,
                                TESTER_TAG:'Maya',
                                rbs_prod: rbs_prod,
                                resX: resX,
                                resY: resY,
                                SPU: SPU,
                                iter: iter,
                                theshold: theshold,
                                customBuildLinkWindows: customBuildLinkWindows,
                                customBuildLinkOSX: customBuildLinkOSX,
                                engine: '2',
                                nodeRetry: nodeRetry
                                ])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
