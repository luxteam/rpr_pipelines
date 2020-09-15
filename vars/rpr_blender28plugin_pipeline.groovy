import groovy.transform.Field
import groovy.json.JsonOutput
import UniverseClient
import utils
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig

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
        // OSX & Ubuntu18
        default:
            sh """
            ./make_results_baseline.sh
            """
        }
    }
}

def buildRenderCache(String osName, String toolVersion, String log_name)
{
    dir("scripts") {
        switch(osName)
        {
            case 'Windows':
                bat "build_rpr_cache.bat ${toolVersion} >> ..\\${log_name}.cb.log  2>&1"
                break;
            default:
                sh "./build_rpr_cache.sh ${toolVersion} >> ../${log_name}.cb.log 2>&1"        
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
                switch(osName)
                {
                case 'Windows':
                    dir('scripts')
                    {
                        bat """
                        run.bat ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} ${options.engine}  ${options.toolVersion}>> ..\\${options.stageName}.log  2>&1
                        """
                    }
                    break;
                // OSX & Ubuntu18
                default:
                    dir("scripts")
                    {
                        sh """
                        ./run.sh ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" ${options.resX} ${options.resY} ${options.SPU} ${options.iter} ${options.theshold} ${options.engine} ${options.toolVersion}>> ../${options.stageName}.log 2>&1
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
                        buildRenderCache(osName, options.toolVersion, options.stageName)
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

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        if (options.engine == 'FULL2'){
            REF_PATH_PROFILE="${REF_PATH_PROFILE}-NorthStar"
        }

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, options.stageName)

        try {
            if (options['updateRefs']) {
                executeTestCommand(osName, asicName, options)
                executeGenTestRefCommand(osName, options)
                sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
            } else {
                // TODO: receivebaseline for json suite
                try {
                    GithubNotificator.updateStatus("Test", options['stageName'], "pending", env, options, "Downloading reference images.", "${BUILD_URL}")
                    println "[INFO] Downloading reference images for ${options.tests}"
                    receiveFiles("${REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
                    options.tests.split(" ").each() {
                        receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/')
                    }
                } catch (e) {
                    println("[WARNING] Baseline doesn't exist.")
                }
                GithubNotificator.updateStatus("Test", options['stageName'], "pending", env, options, "Executing tests.", "${BUILD_URL}")
                executeTestCommand(osName, asicName, options)
            }
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
                        if (sessionReport.summary.total == sessionReport.summary.error + sessionReport.summary.skipped) {
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
                checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
            } catch (e) {
                String errorMessage = "Failed to download the plugin repository."
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
            githubNotificator.initPR(options, "${BUILD_URL}")
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

            if(options.testsPackage != "none")
            {
                // json means custom test suite. Split doesn't supported
                if(options.testsPackage.endsWith('.json'))
                {
                    def testsByJson = readJSON file: "jobs/${options.testsPackage}"
                    testsByJson.each() {
                        options.groupsUMS << "${it.key}"
                    }
                    options.splitTestsExecution = false
                    options.timeouts = ["regression.json": options.REGRESSION_TIMEOUT + options.ADDITIONAL_XML_TIMEOUT]
                }
                else {
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
            } else {
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
    } catch (e) {
        String errorMessage = "Failed to configurate tests."
        GithubNotificator.updateStatus("PreBuild", "Version increment", "error", env, options, errorMessage)
        problemMessageManager.saveSpecificFailReason(errorMessage, "PreBuild")
        throw e
    }

    if(options.splitTestsExecution) {
        options.testsList = options.tests
    }
    else {
        options.testsList = ['']
        options.tests = tests.join(" ")
    }

    println "timeouts: ${options.timeouts}"

    if (options.sendToUMS)
    {
        try
        {
            // Universe : auth because now we in node
            // If use httpRequest in master slave will catch 408 error
            universeClient.tokenSetup()

            // create build ([OS-1:GPU-1, ... OS-N:GPU-N], ['Suite1', 'Suite2', ..., 'SuiteN'])
            universeClient.createBuild(options.universePlatforms, options.groupsUMS, options.updateRefs)
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
                        if (options['isPreBuilt'])
                        {
                            bat """
                            build_reports.bat ..\\summaryTestResults ${escapeCharsByUnicode("Blender ")}${options.toolVersion} "PreBuilt" "PreBuilt" "PreBuilt"
                            """
                        }
                        else
                        {
                            bat """
                            build_reports.bat ..\\summaryTestResults ${escapeCharsByUnicode("Blender ")}${options.toolVersion} ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
                            """
                        }
                    }
                }
            } catch(e) {
                String errorMessage = "Failed to build test report."
                GithubNotificator.updateStatus("Deploy", "Building test report", "failure", env, options, errorMessage, "${BUILD_URL}")
                problemMessageManager.saveSpecificFailReason(errorMessage, "Deploy")
                println("[ERROR] Failed to build test report.")
                println(e.toString())
                println(e.getMessage())
                throw e
            }

            try
            {
                dir("jobs_launcher") {
                    bat "get_status.bat ..\\summaryTestResults"
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
    Boolean updateRefs = false,
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
    String engine = "1.0",
    String tester_tag = "Blender2.8",
    String toolVersion = "2.83")
{
    resX = (resX == 'Default') ? '0' : resX
    resY = (resY == 'Default') ? '0' : resY
    SPU = (SPU == 'Default') ? '25' : SPU
    iter = (iter == 'Default') ? '50' : iter
    theshold = (theshold == 'Default') ? '0.05' : theshold
    engine = (engine == '2.0 (Northstar)') ? 'FULL2' : 'FULL'
    def nodeRetry = []
    Map options = [:]

    try
    {
        try
        {
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

            println "Platforms: ${platforms}"
            println "Tests: ${tests}"
            println "Tests package: ${testsPackage}"
            println "Split tests execution: ${splitTestsExecution}"
            println "UMS platforms: ${universePlatforms}"

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
                        REGRESSION_TIMEOUT:120,
                        DEPLOY_TIMEOUT:150,
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
                        engine: engine,
                        nodeRetry: nodeRetry,
                        problemMessageManager: problemMessageManager,
                        platforms:platforms
                        ]
        }
        catch(e)
        {
            problemMessageManager.saveSpecificFailReason("Failed initialization.", "Init")

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
