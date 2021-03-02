import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig


def executeGenTestRefCommand(String asicName, String osName, Map options) {
    dir('BaikalNext/RprTest') {
        if (options.testsQuality) {
            switch(osName) {
                case 'Windows':
                    bat """
                        ..\\bin\\RprTest -quality ${options.RENDER_QUALITY} -genref 1 --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ..\\..\\${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                    """
                    break
                case 'OSX':
                    sh """
                        export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                        ../bin/RprTest -quality ${options.RENDER_QUALITY} -genref 1 --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ../../${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                    """
                    break
                default:
                    sh """
                        export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                        ../bin/RprTest -quality ${options.RENDER_QUALITY} -genref 1 --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ../../${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                    """
            }

        } else {
            
            options.enableRTX = ""
            if (!asicName.contains("RTX")) {
                println "[INFO] Enable rrn for ${asicName}"
                options.enableRTX = "-enable-rrn"
            }

            switch(osName) {
                case 'Windows':
                    bat """
                        ..\\bin\\RprTest ${options.enableRTX} -genref 1 --gtest_output=xml:../../${STAGE_NAME}.gtest.xml >> ..\\..\\${STAGE_NAME}.log 2>&1
                    """
                    break
                case 'OSX':
                    sh """
                        export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                        ../bin/RprTest ${options.enableRTX} -genref 1 --gtest_output=xml:../../${STAGE_NAME}.gtest.xml >> ../../${STAGE_NAME}.log 2>&1
                    """
                    break
                default:
                    sh """
                        export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                        ../bin/RprTest ${options.enableRTX} -genref 1 --gtest_output=xml:../../${STAGE_NAME}.gtest.xml >> ../../${STAGE_NAME}.log 2>&1
                    """
            }
        }
    }
}

def executeTestCommand(String asicName, String osName, Map options) {
    dir('BaikalNext/RprTest') {
        if (options.testsQuality) {
            switch(osName) {
                case 'Windows':
                    bat """
                        ..\\bin\\RprTest -quality ${options.RENDER_QUALITY} --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ..\\..\\${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                    """
                    break
                case 'OSX':
                    sh """
                        export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                        ../bin/RprTest -quality ${options.RENDER_QUALITY} --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ../../${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                    """
                    break
                default:
                    sh """
                        export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                        ../bin/RprTest -quality ${options.RENDER_QUALITY} --gtest_output=xml:../../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml >> ../../${STAGE_NAME}.${options.RENDER_QUALITY}.log 2>&1
                    """
            }

        } else {
            
            options.enableRTX = ""
            if (!asicName.contains("RTX")) {
                println "[INFO] Enable rrn for ${asicName}"
                options.enableRTX = "-enable-rrn"
            }

            switch(osName) {
                case 'Windows':
                    bat """
                        ..\\bin\\RprTest ${options.enableRTX} --gtest_output=xml:../../${STAGE_NAME}.gtest.xml >> ..\\..\\${STAGE_NAME}.log 2>&1
                    """
                    break
                case 'OSX':
                    sh """
                        export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                        ../bin/RprTest ${options.enableRTX} --gtest_output=xml:../../${STAGE_NAME}.gtest.xml >> ../../${STAGE_NAME}.log 2>&1
                    """
                    break
                default:
                    sh """
                        export LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH
                        ../bin/RprTest ${options.enableRTX} --gtest_output=xml:../../${STAGE_NAME}.gtest.xml >> ../../${STAGE_NAME}.log 2>&1
                    """
            }
        }
    }
}


def executeTestsCustomQuality(String osName, String asicName, Map options) {
    validateDriver(osName, asicName, ["Ubuntu-NVIDIA": "455.46.04", "Windows-NVIDIA": "457.67"], options)
       
    cleanWS(osName)
    String error_message = ""
    String REF_PATH_PROFILE
    String JOB_PATH_PROFILE

    if (options.testsQuality) {
        REF_PATH_PROFILE="${options.REF_PATH}/${options.RENDER_QUALITY}/${asicName}-${osName}"
        JOB_PATH_PROFILE="${options.JOB_PATH}/${options.RENDER_QUALITY}/${asicName}-${osName}"
        outputEnvironmentInfo(osName, "${STAGE_NAME}.${options.RENDER_QUALITY}")
    } else {
        REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"
        outputEnvironmentInfo(osName, "${STAGE_NAME}")
    }
    
    try {
        unstash "app${osName}"
        switch(osName) {
            case 'Windows':
                unzip dir: '.', glob: '', zipFile: 'BaikalNext_Build-Windows.zip'
                break
            default:
                sh "tar -xJf BaikalNext_Build*"
        }

        if (options['updateRefs']) {
            println "Updating Reference Images"
            executeGenTestRefCommand(asicName, osName, options)
            sendFiles('./BaikalNext/RprTest/ReferenceImages/*.*', "${REF_PATH_PROFILE}")
        } else {
            println "Execute Tests"
            receiveFiles("${REF_PATH_PROFILE}/*", './BaikalNext/RprTest/ReferenceImages/')
            executeTestCommand(asicName, osName, options)
        }
    } catch (e) {
        println(e.getMessage())
        error_message = e.getMessage()
        options.successfulTests["unit"] = false

        if (options.testsQuality) {
            println("Exception during [${options.RENDER_QUALITY}] quality tests execution")
            try {
                dir('HTML_Report') {
                    checkOutBranchOrScm('master', 'git@github.com:luxteam/HTMLReportsShared')
                    python3("-m pip install --user -r requirements.txt")
                    python3("hybrid_report.py --xml_path ../${STAGE_NAME}.${options.RENDER_QUALITY}.gtest.xml --images_basedir ../BaikalNext/RprTest --report_path ../${asicName}-${osName}-${options.RENDER_QUALITY}_failures")
                }

                stash includes: "${asicName}-${osName}-${options.RENDER_QUALITY}_failures/**/*", name: "testResult-${asicName}-${osName}-${options.RENDER_QUALITY}", allowEmpty: true

                utils.publishReport(this, "${BUILD_URL}", "${asicName}-${osName}-${options.RENDER_QUALITY}_failures", "report.html", "${STAGE_NAME}_${options.RENDER_QUALITY}_failures", "${STAGE_NAME}_${options.RENDER_QUALITY}_failures")

            } catch (err) {
                println("Error during HTML report publish")
                println(err.getMessage())
            }
        } else {
            println("Exception during tests execution")
            try {
                dir('HTML_Report') {
                    checkOutBranchOrScm('master', 'git@github.com:luxteam/HTMLReportsShared')
                    python3("-m pip install -r requirements.txt")
                    python3("hybrid_report.py --xml_path ../${STAGE_NAME}.gtest.xml --images_basedir ../BaikalNext/RprTest --report_path ../${asicName}-${osName}-Failures")
                }

                stash includes: "${asicName}-${osName}-Failures/**/*", name: "testResult-${asicName}-${osName}", allowEmpty: true

                utils.publishReport(this, "${BUILD_URL}", "${asicName}-${osName}-Failures", "report.html", "${STAGE_NAME}_Failures", "${STAGE_NAME}_Failures")

            } catch (err) {
                println("[ERROR] Failed to publish HTML report.")
                println(err.getMessage())
            }
        }
    } finally {
        archiveArtifacts "*.log"
        archiveArtifacts "*.gtest.xml"
        junit "*.gtest.xml"

        if (options['RENDER_QUALITY']) {
            options['processedQualities'] << options['RENDER_QUALITY']
        }

        if (options.testsQuality && env.CHANGE_ID) {
            String context = "[TEST] ${osName}-${asicName}-${options.RENDER_QUALITY}"
            String description = error_message ? "Testing finished with error message: ${error_message}" : "Testing finished"
            String status = error_message ? "failure" : "success"
            String url = error_message ? "${env.BUILD_URL}/${STAGE_NAME}_${options.RENDER_QUALITY}_failures" : "${env.BUILD_URL}/artifact/${STAGE_NAME}.${options.RENDER_QUALITY}.log"
            pullRequest.createStatus(status, context, description, url)
            options['commitContexts'].remove(context)

        } else if (env.CHANGE_ID) {
            String context = "[TEST] ${osName}-${asicName}"
            String description = error_message ? "Testing finished with error message: ${error_message}" : "Testing finished"
            String status = error_message ? "failure" : "success"
            String url = error_message ? "${env.BUILD_URL}/${STAGE_NAME}_Failures" : "${env.BUILD_URL}/artifact/${STAGE_NAME}.log"
            pullRequest.createStatus(status, context, description, url)
            options['commitContexts'].remove(context)
        }

    }
}


def createPerfDirs() {
    if (isUnix()) {
        sh """
            mkdir -p Scenarios
            mkdir -p Telemetry
            mkdir -p Metrics
            mkdir -p Reports
            mkdir -p References
        """
    } else {
        bat """
            if not exist Scenarios mkdir Scenarios
            if not exist Telemetry mkdir Telemetry
            if not exist Metrics mkdir Metrics
            if not exist Reports mkdir Reports
            if not exist References mkdir References
        """
    }
}


def executeGenPerfTestRefCommand(String asicName, String osName, Map options) {
    dir('BaikalNext/RprPerfTest') {
        createPerfDirs()
        switch(osName) {
            case 'Windows':
                python3("ScenarioPlayer.py -s ${options.scenarios} -E ..\\bin >> ..\\..\\${STAGE_NAME}.perf.log 2>&1", "39")
                break
            case 'OSX':
                withEnv(["LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH"]) {
                    python3("ScenarioPlayer.py -s ${options.scenarios} -E ../bin >> ../../${STAGE_NAME}.perf.log 2>&1", "3.9")
                }
                break
            default:
                withEnv(["LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH"]) {
                    python3("ScenarioPlayer.py -s ${options.scenarios} -E ../bin >> ../../${STAGE_NAME}.perf.log 2>&1", "3.9")
                }
        }
    }
}


def executePerfTestCommand(String asicName, String osName, Map options) {
    dir('BaikalNext/RprPerfTest') {
        createPerfDirs()
        switch(osName) {
            case 'Windows':
                python3("ScenarioPlayer.py -s ${options.scenarios} -E ..\\bin -P >> ..\\..\\${STAGE_NAME}.perf.log 2>&1", "39")
                break
            case 'OSX':
                withEnv(["LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH"]) {
                    python3("ScenarioPlayer.py -s ${options.scenarios} -E ../bin -P >> ../../${STAGE_NAME}.perf.log 2>&1", "3.9")
                }
                break
            default:
                withEnv(["LD_LIBRARY_PATH=../bin:\$LD_LIBRARY_PATH"]) {
                    python3("ScenarioPlayer.py -s ${options.scenarios} -E ../bin -P >> ../../${STAGE_NAME}.perf.log 2>&1", "3.9")
                }
        }
    }
}


def executePerfTests(String osName, String asicName, Map options) {
    String error_message = ""
    String REF_PATH_PROFILE
    String JOB_PATH_PROFILE

    REF_PATH_PROFILE="${options.REF_PATH}/perf/${asicName}-${osName}"
    JOB_PATH_PROFILE="${options.JOB_PATH}/perf/${asicName}-${osName}"
    outputEnvironmentInfo(osName, "${STAGE_NAME}.perf")
    
    try {
        String assetsDir = isUnix() ? "${CIS_TOOLS}/../TestResources/rpr_hybrid_perftests" : "C:\\TestResources\\rpr_hybrid_perftests"
        dir(assetsDir) {
            checkOutBranchOrScm(options["assetsBranch"], options["assetsRepo"], true, null, null, false, true, "radeonprorender-gitlab", true)
        }

        dir("BaikalNext") {
            dir("bin") {
                unstash "perf${osName}"
            }
            unstash "perfTestsConf"
            dir("RprPerfTest/Scenarios") {
                if (options.scenarios == "all") {
                    List scenariosList = []
                    def files = findFiles(glob: "*.json")
                    for (file in files) {
                        scenariosList << file.name
                    }
                    options.scenarios = scenariosList.join(" ")
                }
                for (scenarioName in options.scenarios.split()) {
                    def scenarioContent = readJSON file: scenarioName
                    // check that it's scene path not a case which is implemented programmatically
                    if (scenarioContent["scene_name"].contains("/")) {
                        String[] scenePathParts = scenarioContent["scene_name"].split("/")
                        scenarioContent["scene_name"] = assetsDir.replace("\\", "/") + "/" + scenePathParts[-2] + "/" + scenePathParts[-1]
                        JSON serializedJson = JSONSerializer.toJSON(scenarioContent, new JsonConfig());
                        writeJSON file: scenarioName, json: serializedJson, pretty: 4
                    }
                }
            }
        }

        if (options["updateRefsPerf"]) {
            println "Updating references for performance tests"
            executeGenPerfTestRefCommand(asicName, osName, options)
            sendFiles('./BaikalNext/RprPerfTest/Telemetry/*.*', "${REF_PATH_PROFILE}")
        } else {
            println "Execute Tests"
            receiveFiles("${REF_PATH_PROFILE}/*", './BaikalNext/RprPerfTest/References/')
            executePerfTestCommand(asicName, osName, options)
        }
    } catch (e) {
        println(e.getMessage())
        error_message = e.getMessage()
        options.successfulTests["perf"] = false
        currentBuild.result = "UNSTABLE"
    } finally {
        archiveArtifacts "*.log"

        dir("BaikalNext/RprPerfTest/Reports") {
            stash includes: "*.json", name: "testPerfResult-${asicName}-${osName}", allowEmpty: true

            // check results
            if (!options.updateRefsPerf) {
                List reportsList = []
                def reports = findFiles(glob: "*.json")
                Boolean cliffDetected, unexpectedAcceleration
                loop: for (report in reports) {
                    def reportContent = readJSON file: report.name
                    for (metric in reportContent) {
                        if (metric.value["Cliff_detected"]) {
                            cliffDetected = true
                        } else if (metric.value["Unexpected_acceleration"]) {
                            unexpectedAcceleration = true
                        }
                        if (cliffDetected && unexpectedAcceleration) {
                            break loop
                        }
                    }
                }
                if (cliffDetected || unexpectedAcceleration) {
                    currentBuild.result = "UNSTABLE"
                    options.successfulTests["perf"] = false
                }
                if (cliffDetected) {
                    error_message += " Testing finished with 'cliff detected'."
                }
                if (unexpectedAcceleration) {
                    error_message += " Testing finished with 'unexpected acceleration'."
                }

                error_message = error_message.trim()
            }
        }

        if (env.BRANCH_NAME) {
            String context = "[TEST-PERF] ${osName}-${asicName}"
            String description = error_message ? "Testing finished with error message: ${error_message}" : "Testing finished"
            String status = error_message ? "failure" : "success"
            String url = "${env.BUILD_URL}/artifact/${STAGE_NAME}.perf.log"
            pullRequest.createStatus(status, context, description, url)
            options['commitContexts'].remove(context)
        }
    }
}


def executeTests(String osName, String asicName, Map options) {
    Boolean someStageFail = false 
    if (options.testsQuality) {
        options['testsQuality'].split(",").each() {
            try {
                // create list with processed qualities for do not run them twice in case of retries
                if (!options['processedQualities']) {
                    options['processedQualities'] = []
                }
                options['RENDER_QUALITY'] = "${it}"
                if (!options['processedQualities'].contains(options['RENDER_QUALITY'])) {
                    executeTestsCustomQuality(osName, asicName, options)
                }
            } catch (e) {
                // suppress exception for start next quality test
                someStageFail = true
                println(e.toString())
                println(e.getMessage())
            }
        }
    } else {
        try {
            executeTestsCustomQuality(osName, asicName, options)
        } catch (e) {
            someStageFail = true
            println(e.toString())
            println(e.getMessage())
        }

        if (options.scenarios) {
            try {
                executePerfTests(osName, asicName, options)
            } catch (e) {
                somStageFail = true
                println(e.toString())
                println(e.getMessage())
            }
        }
    }

    if (someStageFail) {
        // send error signal for mark stage as failed
        error "Error during tests execution"
    }
}


def executeBuildWindows(Map options) {
    String build_type = options['cmakeKeys'].contains("-DCMAKE_BUILD_TYPE=Debug") ? "Debug" : "Release"
    bat """
        mkdir Build
        cd Build
        cmake ${options['cmakeKeys']} -G "Visual Studio 15 2017 Win64" .. >> ..\\${STAGE_NAME}.log 2>&1
        cmake --build . --target PACKAGE --config ${build_type} >> ..\\${STAGE_NAME}.log 2>&1
        rename BaikalNext.zip BaikalNext_${STAGE_NAME}.zip
    """
    dir("Build/bin/${build_type}") {
        stash includes: "RprPerfTest.exe", name: "perfWindows", allowEmpty: true
    }
}


def executeBuildOSX(Map options) {
    sh """
        mkdir Build
        cd Build
        cmake ${options['cmakeKeys']} .. >> ../${STAGE_NAME}.log 2>&1
        make -j 4 >> ../${STAGE_NAME}.log 2>&1
        make package >> ../${STAGE_NAME}.log 2>&1
        mv BaikalNext.tar.xz BaikalNext_${STAGE_NAME}.tar.xz
    """
    dir("Build/bin") {
        stash includes: "RprPerfTest", name: "perfOSX", allowEmpty: true
    }
}


def executeBuildLinux(Map options) {
    sh """
        mkdir Build
        cd Build
        cmake ${options['cmakeKeys']} .. >> ../${STAGE_NAME}.log 2>&1
        make -j 8 >> ../${STAGE_NAME}.log 2>&1
        make package >> ../${STAGE_NAME}.log 2>&1
        mv BaikalNext.tar.xz BaikalNext_${STAGE_NAME}.tar.xz
    """
    dir("Build/bin") {
        stash includes: "RprPerfTest", name: "perfUbuntu18", allowEmpty: true
    }
}


def executeBuild(String osName, Map options) {
    String error_message = ""
    String context = "[BUILD] ${osName}"
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        outputEnvironmentInfo(osName)

        if (env.CHANGE_ID) {
            pullRequest.createStatus("pending", context, "Checkout has been finished. Trying to build...", "${env.JOB_URL}")
        }

        switch(osName) {
            case 'Windows':
                executeBuildWindows(options)
                break
            case 'OSX':
                executeBuildOSX(options)
                break
            default:
                executeBuildLinux(options)
        }

        dir('Build') {
            stash includes: "BaikalNext_${STAGE_NAME}*", name: "app${osName}"
        }
    } catch (e) {
        println(e.getMessage())
        error_message = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts "*.log"
        archiveArtifacts "Build/BaikalNext_${STAGE_NAME}*"
        if (env.CHANGE_ID) {
            String status = error_message ? "failure" : "success"
            pullRequest.createStatus("${status}", context, "Build finished as '${status}'", "${env.BUILD_URL}/artifact/${STAGE_NAME}.log")
            options['commitContexts'].remove(context)
        }
    }
}

def executePreBuild(Map options) {
   
    checkOutBranchOrScm(options.projectBranch, options.projectRepo, true)

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true)
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${commitMessage}"
    println "Commit SHA: ${options.commitSHA}"
    
    if ((commitMessage.contains("[CIS:GENREFALL]") || commitMessage.contains("[CIS:GENREF]")) && env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.updateRefs = true
        println("[CIS:GENREF] or [CIS:GENREFALL] have been found in comment")
    }

    if ((commitMessage.contains("[CIS:GENREFALL]") || commitMessage.contains("[CIS:GENREFPERF]")) && env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.updateRefsPerf = true
        println("[CIS:GENREFPERF] or [CIS:GENREFALL] have been found in comment")
    }

    if (env.CHANGE_URL) {
        println("Build was detected as Pull Request")
    }

    stash includes: "RprPerfTest/", name: "perfTestsConf", allowEmpty: true

    options.commitMessage = []
    commitMessage = commitMessage.split('\r\n')
    commitMessage[2..commitMessage.size()-1].collect(options.commitMessage) { it.trim() }
    options.commitMessage = options.commitMessage.join('\n')

    println "Commit list message: ${options.commitMessage}"
    
    // set pending status for all
    if (env.CHANGE_ID) {
        def commitContexts = []
        options['platforms'].split(';').each() { platform ->
            List tokens = platform.tokenize(':')
            String osName = tokens.get(0)
            // Statuses for builds
            String context = "[BUILD] ${osName}"
            commitContexts << context
            pullRequest.createStatus("pending", context, "Scheduled", "${env.JOB_URL}")
            if (tokens.size() > 1) {
                gpuNames = tokens.get(1)
                gpuNames.split(',').each() { gpuName ->
                    if (options.testsQuality) {
                        options.testsQuality.split(",").each() { testQuality ->
                            // Statuses for tests
                            context = "[TEST] ${osName}-${gpuName}-${testQuality}"
                            commitContexts << context
                            pullRequest.createStatus("pending", context, "Scheduled", "${env.JOB_URL}")
                        }
                    } else {
                        // Statuses for tests
                        context = "[TEST] ${osName}-${gpuName}"
                        commitContexts << context
                        pullRequest.createStatus("pending", context, "Scheduled", "${env.JOB_URL}")
                    }
                    if (options.scenarios) {
                        // Statuses for performance tests
                        context = "[TEST-PERF] ${osName}-${gpuName}"
                        commitContexts << context
                        pullRequest.createStatus("pending", context, "Scheduled", "${env.JOB_URL}")
                    }
                }
            }
        }
        options['commitContexts'] = commitContexts
    }
}

def executeDeploy(Map options, List platformList, List testResultList) {
    cleanWS()
    if (options['executeTests'] && testResultList) {
        if (options.testsQuality) {
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
                                println(e.toString())
                                println(e.getMessage())
                            }
                        }
                    }
                }
                publishHTML([allowMissing: true,
                             alwaysLinkToLastBuild: false,
                             keepAll: true,
                             reportDir: "SummaryReport",
                             reportFiles: "${reportFiles.replaceAll('^, ', '')}",
                             reportName: "HTML Failures"])
            } catch(e) {
                println(e.toString())
            }
        } else {
            try {
                String reportFiles = ""
                dir("SummaryReport") {
                    testResultList.each() {
                        try {
                            unstash "${it}"
                            reportFiles += ", ${it}-Failures/report.html".replace("testResult-", "")
                        }
                        catch(e) {
                            println("[ERROR] Can't unstash ${it}")
                            println(e.toString())
                            println(e.getMessage())
                        }
                    }
                }
                publishHTML([allowMissing: true,
                             alwaysLinkToLastBuild: false,
                             keepAll: true,
                             reportDir: "SummaryReport",
                             reportFiles: "${reportFiles.replaceAll('^, ', '')}",
                             reportName: "HTML Failures"])
            } catch(e) {
                println(e.toString())
            }

            if (options.scenarios && !options.updateRefsPerf) {
                checkOutBranchOrScm("master", "git@github.com:luxteam/HTMLReportsShared")

                dir("performanceReports") {
                    testResultList.each() {
                        try {
                            dir("${it}".replace("testResult-", "")) {
                                unstash "${it.replace('testResult-', 'testPerfResult-')}"
                            }
                        }
                        catch(e) {
                            echo "[ERROR] Can't unstash ${it.replace('testResult-', 'testPerfResult-')}"
                            println(e.toString());
                            println(e.getMessage());
                        }
                    }
                }

                python3("-m pip install --user -r requirements.txt")
                python3("hybrid_perf_report.py --json_files_path \"performanceReports\"")

                utils.publishReport(this, "${BUILD_URL}", "PerformanceReport", "performace_report.html", "Performance Tests Report", "Performance Tests Report")
            }
        }
    }

    // set error statuses for PR, except if current build has been superseded by new execution
    if (env.CHANGE_ID && !currentBuild.nextBuild) {
        // if jobs was aborted or crushed remove pending status for unfinished stages
        options['commitContexts'].each() {
            pullRequest.createStatus("error", it, "Build has been terminated unexpectedly", "${env.BUILD_URL}")
        }
        String status = currentBuild.result ?: "success"
        status = status.toLowerCase()
        String commentMessage = ""
        if (!options.successfulTests["unit"]) {
            commentMessage = "\n Unit tests failures - ${env.BUILD_URL}/HTML_20Failures/"
        }
        if (options.successfulTests["perf"]) {
            commentMessage += "\n Perf tests report (success) - ${env.BUILD_URL}/Performance_20Tests_20Report/"
        } else {
            commentMessage += "\n Perf tests report (problems detected) - ${env.BUILD_URL}/Performance_20Tests_20Report/"
        }
        
        
        def comment = pullRequest.comment("Jenkins build for ${pullRequest.head} finished as ${status} ${commentMessage}")
    }
}

def call(String projectBranch = "",
         String assetsBranch = "master",
         String platforms = "Windows:NVIDIA_RTX2070S;Ubuntu18:NVIDIA_RTX2070",
         String testsQuality = "none",
         String scenarios = "all",
         Boolean updateRefs = false,
         Boolean updateRefsPerf = false,
         Boolean enableNotifications = true,
         String cmakeKeys = "-DCMAKE_BUILD_TYPE=Release -DBAIKAL_ENABLE_RPR=ON -DBAIKAL_NEXT_EMBED_KERNELS=ON") {

    if (testsQuality == "none") {
        println "[INFO] Convert none quality to empty string"
        testsQuality = ""
    }

    if ((env.BRANCH_NAME && env.BRANCH_NAME == "1.xx") || (env.CHANGE_TARGET && env.CHANGE_TARGET == "1.xx") || (projectBranch == "1.xx")) {
        platforms = "Windows:AMD_RXVEGA,AMD_WX9100,NVIDIA_GF1080TI;Ubuntu18:AMD_RadeonVII;CentOS7"
        testsQuality = "low,medium,high"
        scenarios = ""
    }

    println "Test quality: ${testsQuality}"
    println "[INFO] Performance tests which will be executed: ${scenarios}"

    def assetsRepo
    withCredentials([string(credentialsId: 'gitlabURL', variable: 'GITLAB_URL')]){
        assetsRepo = "${GITLAB_URL}/autotest_assets/rpr_hybrid_perftests"
    }

    Map successfulTests = ["unit": true, "perf": true]

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                           [platforms:platforms,
                            projectBranch:projectBranch,
                            assetsBranch:assetsBranch,
                            assetsRepo:assetsRepo,
                            scenarios:scenarios,
                            updateRefs:updateRefs,
                            updateRefsPerf:updateRefsPerf,
                            testsQuality:testsQuality,
                            enableNotifications:enableNotifications,
                            PRJ_NAME:"RadeonProRender-Hybrid",
                            PRJ_ROOT:"rpr-core",
                            projectRepo:"git@github.com:Radeon-Pro/RPRHybrid.git",
                            TESTER_TAG:'HybridTester',
                            executeBuild:true,
                            executeTests:true,
                            slackChannel:"${SLACK_BAIKAL_CHANNEL}",
                            slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
                            slackTocken:"${SLACK_BAIKAL_TOCKEN}",
                            TEST_TIMEOUT:60,
                            cmakeKeys:cmakeKeys,
                            retriesForTestStage:1,
                            successfulTests:successfulTests])
}
