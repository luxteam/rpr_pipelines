import groovy.transform.Field
import groovy.json.JsonOutput
import UniverseClient
import utils
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import TestsExecutionType

@Field UniverseClient universeClient = new UniverseClient(this, "https://umsapi.cis.luxoft.com", env, "https://imgs.cis.luxoft.com", "AMD%20Radeon™%20ProRender%20for%20Blender")
@Field ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)


def getBlenderAddonInstaller(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':

            if (options['isPreBuilt']) {

                println "[INFO] PluginWinSha: ${options['pluginWinSha']}"

                if (options['pluginWinSha']) {
                    if (fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options['pluginWinSha']}.zip")) {
                        println "[INFO] The plugin ${options['pluginWinSha']}.zip exists in the storage."
                    } else {
                        clearBinariesWin()

                        println "[INFO] The plugin does not exist in the storage. Downloading and copying..."
                        downloadPlugin(osName, "Blender", options)

                        bat """
                            IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                            move RadeonProRender*.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options['pluginWinSha']}.zip"
                        """
                    }
                } else {
                    clearBinariesWin()

                    println "[INFO] The plugin does not exist in the storage. PluginSha is unknown. Downloading and copying..."
                    downloadPlugin(osName, "Blender", options)

                    bat """
                        IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                        move RadeonProRender*.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.zip"
                    """
                }

            } else {
                if (fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.commitSHA}_${osName}.zip")) {
                    println "[INFO] The plugin ${options.commitSHA}_${osName}.zip exists in the storage."
                } else {
                    clearBinariesWin()

                    println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                    unstash "appWindows"

                    bat """
                        IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                        move RadeonProRender*.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.commitSHA}_${osName}.zip"
                    """
                }
            }

            break;

        case "OSX":

            if (options['isPreBuilt']) {

                println "[INFO] PluginOSXSha: ${options['pluginOSXSha']}"

                if (options['pluginOSXSha']) {
                    if (fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip")) {
                        println "[INFO] The plugin ${options['pluginOSXSha']}.zip exists in the storage."
                    } else {
                        clearBinariesUnix()

                        println "[INFO] The plugin does not exist in the storage. Downloading and copying..."
                        downloadPlugin(osName, "Blender", options)

                        sh """
                            mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                            mv RadeonProRenderBlender*.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip"
                        """
                    }
                } else {
                    clearBinariesUnix()

                    println "[INFO] The plugin does not exist in the storage. PluginSha is unknown. Downloading and copying..."
                    downloadPlugin(osName, "Blender", options)

                    sh """
                        mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                        mv RadeonProRenderBlender*.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.zip"
                    """
                }

            } else {
                if (fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.commitSHA}_${osName}.zip")) {
                    println "[INFO] The plugin ${options.commitSHA}_${osName}.zip exists in the storage."
                } else {
                    clearBinariesUnix()

                    println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                    unstash "appOSX"
                   
                    sh """
                        mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                        mv RadeonProRenderBlender*.zip "${CIS_TOOLS}/../PluginsBinaries/${options.commitSHA}_${osName}.zip"
                    """
                }
            }

            break;

        default:

            if (options['isPreBuilt']) {

                println "[INFO] PluginOSXSha: ${options['pluginUbuntuSha']}"

                if (options['pluginUbuntuSha']) {
                    if (fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip")) {
                        println "[INFO] The plugin ${options['pluginUbuntuSha']}.zip exists in the storage."
                    } else {
                        clearBinariesUnix()

                        println "[INFO] The plugin does not exist in the storage. Downloading and copying..."
                        downloadPlugin(osName, "Blender", options)

                        sh """
                            mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                            mv RadeonProRenderBlender*.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip"
                        """
                    }
                } else {
                    clearBinariesUnix()

                    println "[INFO] The plugin does not exist in the storage. PluginSha is unknown. Downloading and copying..."
                    downloadPlugin(osName, "Blender", options)

                    sh """
                        mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                        mv RadeonProRenderBlender*.zip "${CIS_TOOLS}/../PluginsBinaries/${options.pluginUbuntuSha}.zip"
                    """
                }

            } else {
                if (fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.commitSHA}_${osName}.zip")) {
                    println "[INFO] The plugin ${options.commitSHA}_${osName}.zip exists in the storage."
                } else {
                    clearBinariesUnix()

                    println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                    unstash "app${osName}"
                   
                    sh """
                        mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                        mv RadeonProRenderBlender*.zip "${CIS_TOOLS}/../PluginsBinaries/${options.commitSHA}_${osName}.zip"
                    """
                }
            }
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
        // OSX & Ubuntu18
        default:
            sh """
            ./make_results_baseline.sh ${delete}
            """
        }
    }
}

def buildRenderCache(String osName, String toolVersion, String log_name, Integer currentTry)
{
    dir("scripts") {
        switch(osName)
        {
            case 'Windows':
                bat "build_rpr_cache.bat ${toolVersion} >> \"..\\${log_name}_${currentTry}.cb.log\"  2>&1"
                break;
            default:
                sh "./build_rpr_cache.sh ${toolVersion} >> \"../${log_name}_${currentTry}.cb.log\" 2>&1"        
        }
    }
}

def executeTestCommand(String osName, String asicName, Map options)
{
    def test_timeout = options.timeouts["${options.parsedTests}"]
    String testsNames = options.parsedTests
    String testsPackageName = options.testsPackage
    if (options.testsPackage != "none" && !options.isPackageSplitted) {
        if (options.parsedTests.contains(".json")) {
            // if tests package isn't splitted and it's execution of this package - replace test group for non-splitted package by empty string
            testsNames = ""
        } else {
            // if tests package isn't splitted and it isn't execution of this package - replace tests package by empty string
            testsPackageName = "none"
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
                    dir('scripts')
                    {
                        bat """
                        run.bat ${options.renderDevice} \"${testsPackageName}\" \"${testsNames}\" ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} ${options.engine}  ${options.toolVersion} ${options.testCaseRetries} ${options.updateRefs} 1>> \"..\\${options.stageName}_${options.currentTry}.log\"  2>&1
                        """
                    }
                    break;
                // OSX & Ubuntu18
                default:
                    dir("scripts")
                    {
                        sh """
                        ./run.sh ${options.renderDevice} \"${testsPackageName}\" \"${testsNames}\" ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} ${options.engine} ${options.toolVersion} ${options.testCaseRetries} ${options.updateRefs} 1>> \"../${options.stageName}_${options.currentTry}.log\" 2>&1
                        """
                    }
                }
            }
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    // TODO: improve envs, now working on Windows testers only
    if (options.sendToUMS){
        universeClient.stage("Tests-${osName}-${asicName}", "begin")
    }

    // get engine from test group name if there are more than one engines
    if (options.engines.count(",") > 0) {
        options.engine = options.tests.split("-")[-1]
        List parsedTestNames = []
        options.tests.split().each { test ->
            List testNameParts = test.split("-") as List
            parsedTestNames.add(testNameParts.subList(0, testNameParts.size() - 1).join("-"))
        }
        options.parsedTests = parsedTestNames.join(" ")
    } else {
        options.engine = options.engines
        options.parsedTests = options.tests
    }

    // used for mark stash results or not. It needed for not stashing failed tasks which will be retried.
    Boolean stashResults = true

    try {
        try {
            timeout(time: "5", unit: 'MINUTES') {
                GithubNotificator.updateStatus("Test", options['stageName'], "pending", env, options, "Downloading tests repository.", "${BUILD_URL}")
                cleanWS(osName)
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_blender.git')
                println "[INFO] Preparing on ${env.NODE_NAME} successfully finished."
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
            downloadAssets("${options.PRJ_ROOT}/${options.PRJ_NAME}/Blender2.8Assets/", 'Blender2.8Assets')
        } catch (e) {
            throw new ExpectedExceptionWrapper("Failed to download test scenes.", e)
        }

        try {
            Boolean newPluginInstalled = false
            try {
                timeout(time: "12", unit: 'MINUTES') {
                    GithubNotificator.updateStatus("Test", options['stageName'], "pending", env, options, "Installing the plugin.", "${BUILD_URL}")
                    getBlenderAddonInstaller(osName, options)
                    newPluginInstalled = installBlenderAddon(osName, options.toolVersion, options)
                    println "[INFO] Install function on ${env.NODE_NAME} return ${newPluginInstalled}"
                }
            } catch (e) {
                if (utils.isTimeoutExceeded(e)) {
                    throw new ExpectedExceptionWrapper("Failed to install the plugin due to timeout.", e)
                } else {
                    throw new ExpectedExceptionWrapper("Failed to install the plugin.", e)
                }
            }
        

            try {
                if (newPluginInstalled) {
                    timeout(time: "3", unit: 'MINUTES') {
                        GithubNotificator.updateStatus("Test", options['stageName'], "pending", env, options, "Building cache.", "${BUILD_URL}")
                        buildRenderCache(osName, options.toolVersion, options.stageName, options.currentTry)
                        if(!fileExists("./Work/Results/Blender28/cache_building.jpg")){
                            println "[ERROR] Failed to build cache on ${env.NODE_NAME}. No output image found."
                            throw new ExpectedExceptionWrapper("No output image after cache building.", new Exception("No output image after cache building."))
                        }
                    }
                }
            } catch (e) {
                if (utils.isTimeoutExceeded(e)) {
                    throw new ExpectedExceptionWrapper("Failed to build cache due to timeout.", e)
                } else {
                    throw new ExpectedExceptionWrapper("Failed to build cache.", e)
                }
            }
            
        } catch(e) {
            println("[ERROR] Failed to install plugin on ${env.NODE_NAME}")
            println(e.toString())
            // deinstalling broken addon
            installBlenderAddon(osName, options.toolVersion, options, false, true)
            throw e
        }

        String enginePostfix = ""
        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        switch(options.engine) {
            case 'FULL2':
                enginePostfix = "NorthStar"
                break
            case 'LOW':
                enginePostfix = "HybridLow"
                break
            case 'MEDIUM':
                enginePostfix = "HybridMedium"
                break
            case 'HIGH':
                enginePostfix = "HybridHigh"
                break
        }
        REF_PATH_PROFILE = enginePostfix ? "${REF_PATH_PROFILE}-${enginePostfix}" : REF_PATH_PROFILE

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, options.stageName, options.currentTry)

        try {
            if (options['updateRefs'].contains('Update')) {
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
                // TODO: receivebaseline for json suite
                try {
                    String baseline_dir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_blender_autotests_baselines" : "/mnt/c/TestResources/rpr_blender_autotests_baselines"
                    baseline_dir = enginePostfix ? "${baseline_dir}-${enginePostfix}" : baseline_dir
                    GithubNotificator.updateStatus("Test", options['stageName'], "pending", env, options, "Downloading reference images.", "${BUILD_URL}")
                    println "[INFO] Downloading reference images for ${options.parsedTests}"
                    options.parsedTests.split(" ").each() {
                        if (it.contains(".json")) {
                            receiveFiles("${REF_PATH_PROFILE}/", baseline_dir)
                        } else {
                            receiveFiles("${REF_PATH_PROFILE}/${it}", baseline_dir)
                        }
                    }
                } catch (e) {
                    println("[WARNING] Problem when copying baselines. " + e.getMessage())
                }
                GithubNotificator.updateStatus("Test", options['stageName'], "pending", env, options, "Executing tests.", "${BUILD_URL}")
                executeTestCommand(osName, asicName, options)
            }
            options.executeTestsFinished = true
        } catch (e) {
            throw new ExpectedExceptionWrapper("An error occurred while executing tests. Please contact support.", e)
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
    } finally {
        try {
            archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
            if (stashResults) {
                dir('Work')
                {
                    if (fileExists("Results/Blender28/session_report.json")) {

                        def sessionReport = null
                        sessionReport = readJSON file: 'Results/Blender28/session_report.json'

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

                        // deinstalling broken addon
                        if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped || sessionReport.summary.total == 0) {
                            if (sessionReport.summary.total != sessionReport.summary.skipped){
                                collectCrashInfo(osName, options, options.currentTry)
                                installBlenderAddon(osName, options.toolVersion, options, false, true)
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
            } else {
                println "[INFO] Task ${options.tests} on ${options.nodeLabels} labels will be retried."
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
    dir('RadeonProRenderBlenderAddon\\BlenderPkg')
    {
        GithubNotificator.updateStatus("Build", "Windows", "pending", env, options, "Building the plugin.", "${BUILD_URL}/artifact/Build-Windows.log")
        bat """
            build_win.cmd >> ../../${STAGE_NAME}.log  2>&1
        """

        dir('.build')
        {
            bat """
                rename rprblender*.zip RadeonProRenderForBlender_${options.pluginVersion}_Windows.zip
            """

            if(options.branch_postfix)
            {
                bat """
                    rename RadeonProRender*zip *.(${options.branch_postfix}).zip
                """
            }


            archiveArtifacts "RadeonProRender*.zip"
            String BUILD_NAME = options.branch_postfix ? "RadeonProRenderForBlender_${options.pluginVersion}_Windows.(${options.branch_postfix}).zip" : "RadeonProRenderForBlender_${options.pluginVersion}_Windows.zip"
            String pluginUrl = "${BUILD_URL}/artifact/${BUILD_NAME}"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${pluginUrl}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""

            bat """
                rename RadeonProRender*.zip RadeonProRenderBlender_Windows.zip
            """

            stash includes: "RadeonProRenderBlender_Windows.zip", name: "appWindows"

            GithubNotificator.updateStatus("Build", "Windows", "success", env, options, "The plugin was successfully built and published.", pluginUrl)
        }
    }
}

def executeBuildOSX(Map options)
{
    dir('RadeonProRenderBlenderAddon/BlenderPkg')
    {
        GithubNotificator.updateStatus("Build", "OSX", "pending", env, options, "Building the plugin.", "${BUILD_URL}/artifact/Build-OSX.log")
        sh """
            ./build_osx.sh >> ../../${STAGE_NAME}.log  2>&1
        """

        dir('.build')
        {
            sh """
                mv rprblender*.zip RadeonProRenderForBlender_${options.pluginVersion}_OSX.zip
            """

            if(options.branch_postfix)
            {
                sh """
                    for i in RadeonProRender*; do name="\${i%.*}"; mv "\$i" "\${name}.(${options.branch_postfix})\${i#\$name}"; done
                """
            }

            archiveArtifacts "RadeonProRender*.zip"
            String BUILD_NAME = options.branch_postfix ? "RadeonProRenderForBlender_${options.pluginVersion}_OSX.(${options.branch_postfix}).zip" : "RadeonProRenderForBlender_${options.pluginVersion}_OSX.zip"
            String pluginUrl = "${BUILD_URL}/artifact/${BUILD_NAME}"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${pluginUrl}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""

            sh """
                mv RadeonProRender*zip RadeonProRenderBlender_OSX.zip
            """

            stash includes: "RadeonProRenderBlender_OSX.zip", name: "appOSX"

            GithubNotificator.updateStatus("Build", "OSX", "success", env, options, "The plugin was successfully built and published.", pluginUrl)
        }
    }
}

def executeBuildLinux(String osName, Map options)
{
    dir('RadeonProRenderBlenderAddon/BlenderPkg')
    {
        GithubNotificator.updateStatus("Build", osName, "pending", env, options, "Building the plugin.", "${BUILD_URL}/artifact/Build-Ubuntu18.log")
        sh """
            ./build_linux.sh >> ../../${STAGE_NAME}.log  2>&1
        """

        dir('.build')
        {

            sh """
                mv rprblender*.zip RadeonProRenderForBlender_${options.pluginVersion}_${osName}.zip
            """

            if(options.branch_postfix)
            {
                sh """
                    for i in RadeonProRender*; do name="\${i%.*}"; mv "\$i" "\${name}.(${options.branch_postfix})\${i#\$name}"; done
                """
            }

            archiveArtifacts "RadeonProRender*.zip"
            String BUILD_NAME = options.branch_postfix ? "RadeonProRenderForBlender_${options.pluginVersion}_${osName}.(${options.branch_postfix}).zip" : "RadeonProRenderForBlender_${options.pluginVersion}_${osName}.zip"
            String pluginUrl = "${BUILD_URL}/artifact/${BUILD_NAME}"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${pluginUrl}">[BUILD: ${BUILD_ID}] ${BUILD_NAME}</a></h3>"""

            sh """
                mv RadeonProRender*zip RadeonProRenderBlender_${osName}.zip
            """

            stash includes: "RadeonProRenderBlender_${osName}.zip", name: "app${osName}"

            GithubNotificator.updateStatus("Build", osName, "success", env, options, "The plugin was successfully built and published.", pluginUrl)
        }

    }
}

def executeBuild(String osName, Map options)
{
    if (options.sendToUMS){
        universeClient.stage("Build-" + osName , "begin")
    }
    try {
        dir('RadeonProRenderBlenderAddon')
        {
            try {
                GithubNotificator.updateStatus("Build", osName, "pending", env, options, "Downloading the plugin repository.")
                checkOutBranchOrScm(options['projectBranch'], options['projectRepo'], false, options['prBranchName'], options['prRepoName'])
            } catch (e) {
                String errorMessage
                if (e.getMessage() && e.getMessage().contains("Branch not suitable for integration")) {
                    errorMessage = "Failed to merge branches."
                } else {
                    errorMessage = "Failed to download plugin repository."
                }
                GithubNotificator.updateStatus("Build", osName, "failure", env, options, errorMessage)
                problemMessageManager.saveSpecificFailReason(errorMessage, "Build", osName)
                throw e
            }
        }

        outputEnvironmentInfo(osName)

        try {
            switch(osName)
            {
                case 'Windows':
                    executeBuildWindows(options);
                    break;
                case 'OSX':
                    if(!fileExists("python3"))
                    {
                        sh "ln -s /usr/local/bin/python3.7 python3"
                    }
                    withEnv(["PATH=$WORKSPACE:$PATH"])
                    {
                        executeBuildOSX(options);
                    }
                    break;
                default:
                    if(!fileExists("python3"))
                    {
                        sh "ln -s /usr/bin/python3.7 python3"
                    }
                    withEnv(["PATH=$PWD:$PATH"])
                    {
                        executeBuildLinux(osName, options);
                    }
            }
        } catch (e) {
            String errorMessage = "Failed to build the plugin."
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
            options.executeBuild = true
            options.executeTests = true
            options.testsPackage = "regression.json"
            GithubNotificator githubNotificator = new GithubNotificator(this, pullRequest)
            options.githubNotificator = githubNotificator
            githubNotificator.initPreBuild("${BUILD_URL}")
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
        dir('RadeonProRenderBlenderAddon')
        {
            try {
                checkOutBranchOrScm(options.projectBranch, options.projectRepo, true)
            } catch (e) {
                String errorMessage = "Failed to download plugin repository."
                GithubNotificator.updateStatus("PreBuild", "Version increment", "error", env, options, errorMessage)
                problemMessageManager.saveSpecificFailReason(errorMessage, "PreBuild")
                throw e
            }

            options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
            options.commitMessage = bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim().replace('\n', '')
            options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
            options.commitShortSHA = options.commitSHA[0..6]

            println(bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim());
            println "The last commit was written by ${options.commitAuthor}."
            println "Commit message: ${options.commitMessage}"
            println "Commit SHA: ${options.commitSHA}"
            println "Commit shortSHA: ${options.commitShortSHA}"

            if (options.projectBranch){
                currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
            } else {
                currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
            }

            try {
                options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', ', ').replace(', ', '.')

                if (options['incrementVersion']) {
                    if(env.BRANCH_NAME == "develop" && options.commitAuthor != "radeonprorender") {

                        options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', ', ')
                        println "[INFO] Incrementing version of change made by ${options.commitAuthor}."
                        println "[INFO] Current build version: ${options.pluginVersion}"

                        def new_version = version_inc(options.pluginVersion, 3, ', ')
                        println "[INFO] New build version: ${new_version}"
                        version_write("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', new_version, ', ')

                        options.pluginVersion = version_read("${env.WORKSPACE}\\RadeonProRenderBlenderAddon\\src\\rprblender\\__init__.py", '"version": (', ', ', "true").replace(', ', '.')
                        println "[INFO] Updated build version: ${options.pluginVersion}"

                        bat """
                            git add src/rprblender/__init__.py
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
            catch(e) {
                String errorMessage = "Failed to increment plugin version."
                GithubNotificator.updateStatus("PreBuild", "Version increment", "error", env, options, errorMessage)
                problemMessageManager.saveSpecificFailReason(errorMessage, "PreBuild")
                throw e
            }
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
    } else if (env.JOB_NAME == "RadeonProRenderBlender2.8Plugin-WeeklyFull") {
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
        dir('jobs_test_blender')
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_blender.git')

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
                def tempTests = []

                if (options.isPackageSplitted) {
                    println("[INFO] Tests package '${options.testsPackage}' can be splitted")
                } else {
                    // save tests which user wants to run with non-splitted tests package
                    if (options.tests) {
                        tempTests = options.tests.split(" ") as List
                    }
                    println("[INFO] Tests package '${options.testsPackage}' can't be splitted")
                }

                // modify name of tests package if tests package is non-splitted (it will be use for run package few time with different engines)
                String modifiedPackageName = "${options.testsPackage}~"
                packageInfo["groups"].each() {
                    if (options.isPackageSplitted) {
                        tempTests << it.key
                    } else {
                        if (tempTests.contains(it.key)) {
                            // add duplicated group name in name of package group name for exclude it
                            modifiedPackageName = "${modifiedPackageName},${it.key}"
                        }
                    }
                }
                tempTests.each()
                {
                    // if there are more than one engines - generate set of tests for each engine
                    if (options.engines.count(",") > 0) {
                        options.engines.split(",").each { engine ->
                            tests << "${it}-${engine}"
                        }
                    } else {
                        tests << "${it}"
                    }
                    def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }
                options.groupsUMS = tests

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
            } 
            else 
            {
                options.tests.split(" ").each()
                {
                    // if there are more than one engines - generate set of tests for each engine
                    if (options.engines.count(",") > 0) {
                        options.engines.split(",").each { engine ->
                            tests << "${it}-${engine}"
                        }
                    } else {
                        tests << "${it}"
                    }
                    def xml_timeout = utils.getTimeoutFromXML(this, "${it}", "simpleRender.py", options.ADDITIONAL_XML_TIMEOUT)
                    options.timeouts["${it}"] = (xml_timeout > 0) ? xml_timeout : options.TEST_TIMEOUT
                }
                options.groupsUMS = tests
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
    cleanWS()
    try {
        if(options['executeTests'] && testResultList)
        {
            try {
                GithubNotificator.updateStatus("Deploy", "Building test report", "pending", env, options, "Preparing tests results.", "${BUILD_URL}")
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_blender.git')
            } catch (e) {
                String errorMessage = "Failed to download tests repository."
                GithubNotificator.updateStatus("Deploy", "Building test report", "failure", env, options, errorMessage, "${BUILD_URL}")
                problemMessageManager.saveSpecificFailReason(errorMessage, "Deploy")
                throw e
            }

            Map lostStashes = [:]
            options.engines.split(",").each { engine ->
                lostStashes[engine] = []
            }

            dir("summaryTestResults")
            {
                testResultList.each()
                {
                    String engine
                    String testName
                    if (options.engines.count(",") > 0) {
                        options.engines.split(",").each { currentEngine ->
                            dir(currentEngine) {
                                unstashCrashInfo(options['nodeRetry'], currentEngine)
                            }
                        }
                        List testNameParts = it.split("-") as List
                        engine = testNameParts[-1]
                        testName = testNameParts.subList(0, testNameParts.size() - 1).join("-")
                    } else {
                        testName = it
                        engine = options.engines
                        dir(engine) {
                            unstashCrashInfo(options['nodeRetry'])
                        }
                    }
                    dir(engine)
                    {
                        dir(testName.replace("testResult-", ""))
                        {
                            try
                            {
                                unstash "$it"
                            }catch(e)
                            {
                                echo "[ERROR] Failed to unstash ${it}"
                                lostStashes[engine].add("'${testName}'".replace("testResult-", ""))
                                println(e.toString());
                                println(e.getMessage());
                            }

                        }
                    }
                }
            }

            try {
                dir("jobs_launcher") {
                    // delete engine name from names of test groups
                    def tests = []
                    if (options.engines.count(",") > 0) {
                        options.tests.each { group ->
                            List testNameParts = group.split("-") as List
                            String parsedTestName = testNameParts.subList(0, testNameParts.size() - 1).join("-")
                            if (!tests.contains(parsedTestName)) {
                                tests.add(parsedTestName)
                            }
                        }
                    } else {
                        tests = options.tests
                    }
                    options.engines.split(",").each {
                        bat """
                        count_lost_tests.bat \"${lostStashes[it]}\" .. ..\\summaryTestResults\\${it} \"${options.splitTestsExecution}\" \"${options.testsPackage}\" \"${tests}\"
                        """
                    }
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
                        String[] engines = options.engines.split(",")
                        String[] enginesNames = options.enginesNames.split(",")
                        for (int i = 0; i < engines.length; i++) {
                            String engine = engines[i]
                            String engineName = enginesNames[i]
                            List retryInfoList
                            if (options.engines.count(",") > 0) {
                                retryInfoList = utils.deepcopyCollection(this, options.nodeRetry)
                                retryInfoList.each{ gpu ->
                                    gpu['Tries'].each{ group ->
                                        group.each{ groupKey, retries ->
                                            if (groupKey.endsWith(engine)) {
                                                List testNameParts = groupKey.split("-") as List
                                                String parsedName = testNameParts.subList(0, testNameParts.size() - 1).join("-")
                                                group[parsedName] = retries
                                            }
                                            group.remove(groupKey)
                                        }
                                    }
                                    gpu['Tries'] = gpu['Tries'].findAll{ it.size() != 0 }
                                }
                            } else {
                                retryInfoList = options.nodeRetry
                            }
                            def retryInfo = JsonOutput.toJson(retryInfoList)
                            dir("..\\summaryTestResults\\${engine}") {
                                JSON jsonResponse = JSONSerializer.toJSON(retryInfo, new JsonConfig());
                                writeJSON file: 'retry_info.json', json: jsonResponse, pretty: 4
                            }
                            if (options['isPreBuilt'])
                            {
                                bat """
                                build_reports.bat ..\\summaryTestResults\\${engine} ${escapeCharsByUnicode("Blender ")}${options.toolVersion} "PreBuilt" "PreBuilt" "PreBuilt" \"${escapeCharsByUnicode(engineName)}\"
                                """
                            }
                            else
                            {
                                bat """
                                build_reports.bat ..\\summaryTestResults\\${engine} ${escapeCharsByUnicode("Blender ")}${options.toolVersion} ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\" \"${escapeCharsByUnicode(engineName)}\"
                                """
                            }
                        }
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
                    bat "get_status.bat ..\\summaryTestResults True"
                }
            }
            catch(e)
            {
                println("[ERROR] Failed to generate slack status.")
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

                    problemMessageManager.saveGlobalFailReason("Some tests marked as error.")
                }
                else if (summaryReport.failed > 0) {
                    println("[INFO] Some tests marked as failed. Build result = UNSTABLE.")
                    currentBuild.result = "UNSTABLE"

                    problemMessageManager.saveUnstableReason("Some tests marked as failed.")
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

                List reports = []
                List reportsNames = []
                options.engines.split(",").each { engine ->
                    reports.add("${engine}/summary_report.html")
                }
                options.enginesNames.split(",").each { engine ->
                    reportsNames.add("Summary Report (${engine})")
                }
                utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", reports.join(", "), "Test Report", reportsNames.join(", "))

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

            println "BUILD RESULT: ${currentBuild.result}"
            println "BUILD CURRENT RESULT: ${currentBuild.currentResult}"
        }
    }
    catch(e)
    {
        println(e.toString());
        println(e.getMessage());
        throw e
    }
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


def call(String projectRepo = "git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderBlenderAddon.git",
    String projectBranch = "",
    String testsBranch = "master",
    String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;Ubuntu18:AMD_RadeonVII;OSX:AMD_RXVEGA',
    String updateRefs = 'No',
    Boolean enableNotifications = true,
    Boolean incrementVersion = true,
    String renderDevice = "gpu",
    String testsPackage = "",
    String tests = "",
    Boolean forceBuild = false,
    Boolean splitTestsExecution = true,
    Boolean sendToUMS = true,
    String resX = '0',
    String resY = '0',
    String SPU = '25',
    String iter = '50',
    String theshold = '0.05',
    String customBuildLinkWindows = "",
    String customBuildLinkLinux = "",
    String customBuildLinkOSX = "",
    String enginesNames = "Tahoe,Northstar",
    String tester_tag = "Blender2.8",
    String toolVersion = "2.90",
    String mergeablePR = "",
    String parallelExecutionTypeString = "TakeAllNodes",
    Integer testCaseRetries = 2)
{
    resX = (resX == 'Default') ? '0' : resX
    resY = (resY == 'Default') ? '0' : resY
    SPU = (SPU == 'Default') ? '25' : SPU
    iter = (iter == 'Default') ? '50' : iter
    theshold = (theshold == 'Default') ? '0.05' : theshold
    def nodeRetry = []
    Map options = [:]

    try
    {
        try
        {
            if (!enginesNames) {
                String errorMessage = "Engines parameter is required."
                problemMessageManager.saveSpecificFailReason(errorMessage, "Init")
                throw new Exception(errorMessage)
            }
            def formattedEngines = []
            enginesNames.split(',').each {
                 if (it.contains('Hybrid')) {
                    formattedEngines.add(it.split()[1].toUpperCase())
                } else {
                    formattedEngines.add((it == 'Northstar') ? 'FULL2' : 'FULL')
                }
            }
            formattedEngines = formattedEngines.join(',')

            Boolean isPreBuilt = customBuildLinkWindows || customBuildLinkOSX || customBuildLinkLinux

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
                    default:
                        if (customBuildLinkLinux)
                        {
                            filteredPlatforms = appendPlatform(filteredPlatforms, platform)
                        }
                    }
                }

                platforms = filteredPlatforms
            }

            String PRJ_NAME="RadeonProRenderBlender2.8Plugin"
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

            def parallelExecutionType = TestsExecutionType.valueOf(parallelExecutionTypeString)

            println "Platforms: ${platforms}"
            println "Tests: ${tests}"
            println "Tests package: ${testsPackage}"
            println "Split tests execution: ${splitTestsExecution}"
            println "Tests execution type: ${parallelExecutionType}"
            println "UMS platforms: ${universePlatforms}"

            String prRepoName = ""
            String prBranchName = ""
            if (mergeablePR) {
                String[] prInfo = mergeablePR.split(";")
                prRepoName = prInfo[0]
                prBranchName = prInfo[1]
            }

            Integer deployTimeout = 150 * enginesNames.split(',').length
            println "Calculated deploy timeout: ${deployTimeout}"

            options << [projectRepo:projectRepo,
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
                        isPreBuilt:isPreBuilt,
                        forceBuild:forceBuild,
                        reportName:'Test_20Report',
                        splitTestsExecution:splitTestsExecution,
                        sendToUMS: sendToUMS,
                        gpusCount:gpusCount,
                        TEST_TIMEOUT:180,
                        ADDITIONAL_XML_TIMEOUT:30,
                        NON_SPLITTED_PACKAGE_TIMEOUT:120,
                        DEPLOY_TIMEOUT:deployTimeout,
                        TESTER_TAG:tester_tag,
                        BUILDER_TAG:"BuildBlender2.8",
                        universePlatforms: universePlatforms,
                        resX: resX,
                        resY: resY,
                        SPU: SPU,
                        iter: iter,
                        theshold: theshold,
                        customBuildLinkWindows: customBuildLinkWindows,
                        customBuildLinkLinux: customBuildLinkLinux,
                        customBuildLinkOSX: customBuildLinkOSX,
                        engines: formattedEngines,
                        enginesNames:enginesNames,
                        nodeRetry: nodeRetry,
                        problemMessageManager: problemMessageManager,
                        platforms:platforms,
                        prRepoName:prRepoName,
                        prBranchName:prBranchName,
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
