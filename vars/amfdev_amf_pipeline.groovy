import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import static groovy.io.FileType.FILES
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig

def getAmfTool(String osName, String build_name, Map options)
{
    switch(osName)
    {
        case 'Windows':

            if (!fileExists("${CIS_TOOLS}\\..\\PluginsBinaries\\${options[build_name + 'sha']}.zip")) {

                clearBinariesWin()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "AMF_Windows_${build_name}"
                
                bat """
                    IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                    copy binWindows.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${options[build_name + 'sha']}.zip"
                """

            } else {
                println "[INFO] The plugin ${options[build_name + 'sha']}.zip exists in the storage."
                bat """
                    copy "${CIS_TOOLS}\\..\\PluginsBinaries\\${options[build_name + 'sha']}.zip" binWindows.zip
                """
            }

            unzip zipFile: "binWindows.zip", dir: "AMF", quiet: true

            break;

        case 'MacOS':

            if (!fileExists("${CIS_TOOLS}/../PluginsBinaries/${options[build_name + 'sha']}.zip")) {

                clearBinariesUnix()

                println "[INFO] The plugin does not exist in the storage. Unstashing and copying..."
                unstash "AMF_MacOS_${build_name}"
                
                sh """
                    mkdir -p "${CIS_TOOLS}/../PluginsBinaries"
                    cp binMacOS.zip "${CIS_TOOLS}/../PluginsBinaries/${options[build_name + 'sha']}.zip"
                """ 

            } else {
                println "[INFO] The plugin ${options[build_name + 'sha']}.zip exists in the storage."
                sh """
                    cp "${CIS_TOOLS}/../PluginsBinaries/${options[build_name + 'sha']}.zip" binMacOS.zip
                """
            }

            unzip zipFile: "binMacOS.zip", dir: "AMF", quiet: true

            break;
            
            break;

        default:
            
            // TODO implement artifacts getting for Linux

            break;
    }
}


def executeTestCommand(String osName, String build_name, Map options)
{
    switch(osName) {
        case 'Windows':
            dir('AMF')
            {
                bat """
                    autotests.exe --gtest_output=json:../${STAGE_NAME}.${build_name}.json --gtest_filter=\"${options.testsFilter}\" >> ../${STAGE_NAME}.${build_name}.log 2>&1
                """
            }
            break;
        case 'MacOS':
            dir('AMF')
            {
                sh """
                    chmod u+x autotests
                    ./autotests --gtest_output=json:../${STAGE_NAME}.${build_name}.json --gtest_filter=\"${options.testsFilter}\" >> ../${STAGE_NAME}.${build_name}.log 2>&1
                """
            }
            break;
        default:
            
            // TODO implement tests for Linux

            break;
    }
}


def renameLog(String osName, String build_name) 
{
    switch(osName) {
        case 'Windows':
            bat """
                move AMF\\out.log .
                rename out.log ${STAGE_NAME}.${build_name}.out.log
            """
            break;
        case 'MacOS':
            sh """
                mv AMF/out.log ${STAGE_NAME}.${build_name}.out.log
            """
            break;
        default:
            
            // TODO implement tests for Linux

            break;
    }
}


def executeTests(String osName, String asicName, Map options) {
    try {
        switch(osName) {
            case 'Windows':
                executeTestsWindows(osName, asicName, options)
                break;
            case 'MacOS':
                executeTestsMacOS(osName, asicName, options)
                break;
            default:
                
                // TODO implement tests for Linux

                break;
        }
    } catch (e) {
        println(e.toString());
        println(e.getMessage());
        throw e
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        stash includes: "${STAGE_NAME}.*.json, *.log", name: "${options.testResultsName}", allowEmpty: true
    }
}


def executeTestsWindows(String osName, String asicName, Map options) {
    cleanWS(osName)
    options.buildConfiguration.each() { win_build_conf ->
        options.winVisualStudioVersion.each() { win_vs_ver ->
            options.winLibraryType.each() { win_lib_type ->

                println "Current build configuration: ${win_build_conf}."
                println "Current VS version: ${win_vs_ver}."
                println "Current library type: ${win_lib_type}."

                String win_build_name = generateBuildNameWindows(win_build_conf, win_vs_ver, win_lib_type)

                try {
                    if (!options[win_build_name + 'sha']) {
                        println("[ERROR] Can't find info for saved stash of this configuration. It'll be skipped")
                        return
                    }

                    timeout(time: "5", unit: 'MINUTES') {
                        try {
                            getAmfTool(osName, win_build_name, options)
                        } catch(e) {
                            println("[ERROR] Failed to prepare tests on ${env.NODE_NAME}")
                            println(e.toString())
                            throw e
                        }
                    }

                    executeTestCommand(osName, win_build_name, options)

                } catch (e) {
                    println(e.toString())
                    println(e.getMessage())
                    options.failureMessage = "Failed during testing ${win_build_name} on ${asicName}-${osName}"
                    options.failureError = e.getMessage()
                    currentBuild.result = "FAILURE"
                } finally {
                    try {
                        renameLog(osName, win_build_name)
                    } catch (e) {
                        println("[ERROR] Failed to copy logs")
                        println(e.toString())
                    }
                    try {
                        String outputJsonName = "${STAGE_NAME}.${win_build_name}.json"
                        def outputJson = readJSON file: outputJsonName
                        outputJson["platform"] = env.STAGE_NAME.replace("Test-", "")
                        outputJson["configuration"] = win_build_name
                        outputJson["hostname"] = env.NODE_NAME
                        JSON serializedJson = JSONSerializer.toJSON(outputJson, new JsonConfig());
                        writeJSON file: outputJsonName, json: serializedJson, pretty: 4
                    } catch (e) {
                        println("[ERROR] Failed to save additional information")
                        println(e.toString())
                    }
                    bat """
                        if exist AMF rmdir /Q /S AMF
                        if exist binWindows.zip del binWindows.zip
                    """
                }
            }
        }
    }
}


def executeTestsMacOS(String osName, String asicName, Map options) {
    cleanWS(osName)
    options.buildConfiguration.each() { macos_build_conf ->
        options.macosTool.each() { macos_tool ->
            options.macosLibraryType.each() { macos_lib_type ->

                println "Current build configuration: ${macos_build_conf}."
                println "Current tool: ${macos_tool}."
                println "Current library type: ${macos_lib_type}."

                String macos_build_name = generateBuildNameMacOS(macos_build_conf, macos_tool, macos_lib_type)

                try {
                    if (!options[macos_build_name + 'sha']) {
                        println("[ERROR] Can't find info for saved stash of this configuration. It'll be skipped")
                        return
                    }

                    timeout(time: "5", unit: 'MINUTES') {
                        try {
                            getAmfTool(osName, macos_build_name, options)
                        } catch(e) {
                            println("[ERROR] Failed to prepare tests on ${env.NODE_NAME}")
                            println(e.toString())
                            throw e
                        }
                    }

                    executeTestCommand(osName, macos_build_name, options)

                } catch (e) {
                    println(e.toString())
                    println(e.getMessage())
                    options.failureMessage = "Failed during testing ${macos_build_name} on ${asicName}-${osName}"
                    options.failureError = e.getMessage()
                    currentBuild.result = "FAILURE"
                } finally {
                    try {
                        renameLog(osName, macos_build_name)
                    } catch (e) {
                        println("[ERROR] Failed to copy logs")
                        println(e.toString())
                    }
                    try {
                        String outputJsonName = "${STAGE_NAME}.${macos_build_name}.json"
                        def outputJson = readJSON file: outputJsonName
                        outputJson["platform"] = env.STAGE_NAME.replace("Test-", "")
                        outputJson["configuration"] = macos_build_name
                        outputJson["hostname"] = env.NODE_NAME
                        JSON serializedJson = JSONSerializer.toJSON(outputJson, new JsonConfig());
                        writeJSON file: outputJsonName, json: serializedJson, pretty: 4
                    } catch (e) {
                        println("[ERROR] Failed to save additional information")
                        println(e.toString())
                    }
                    sh """
                        rm -rf AMF
                        rm -rf binMacOS.zip
                    """
                }
            }
        }
    }
    
}


def generateBuildNameWindows(String win_build_conf, String win_vs_ver, String win_lib_type) {
    return "${win_build_conf}_vs${win_vs_ver}_${win_lib_type}"
}


def executeBuildWindows(Map options) {
    options.buildConfiguration.each() { win_build_conf ->
        options.winVisualStudioVersion.each() { win_vs_ver ->
            options.winLibraryType.each() { win_lib_type ->

                println "Current build configuration: ${win_build_conf}."
                println "Current VS version: ${win_vs_ver}."
                println "Current library type: ${win_lib_type}."

                win_build_name = generateBuildNameWindows(win_build_conf, win_vs_ver, win_lib_type)

                switch(win_vs_ver) {
                    case '2017':
                        options.visualStudio = "Visual Studio 15 2017"
                        options.msBuildPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe"
                        break;
                    case '2019':
                        options.visualStudio = "Visual Studio 16 2019"
                        options.msBuildPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Professional\\MSBuild\\Current\\Bin\\MSBuild.exe"
                }

                dir("amf\\public\\proj\\OpenAMF_Autotests") {

                    try {

                        if (!fileExists("generate-${win_lib_type}-vs${win_vs_ver}.bat")) {
                            println("[INFO] This configuration isn't supported now. It'll be skipped")
                            return
                        }

                        bat """
                            generate-${win_lib_type}-vs${win_vs_ver}.bat >> ..\\..\\..\\..\\${STAGE_NAME}.${win_build_name}.log 2>&1
                        """
  
                        String sourceCodeLocation = "vs${win_vs_ver}"

                        dir (sourceCodeLocation) {
                            bat """
                                set msbuild="${options.msBuildPath}"
                                %msbuild% autotests.sln /target:build /maxcpucount /property:Configuration=${win_build_conf};Platform=x64 >> ..\\..\\..\\..\\..\\${STAGE_NAME}.${win_build_name}.log 2>&1
                            """
                        }

                        bat """
                            mkdir binWindows
                            xcopy /s/y/i ${sourceCodeLocation}\\${win_build_conf.capitalize()}\\autotests.exe binWindows
                        """

                        if (win_lib_type == 'shared') {
                            bat """
                                xcopy /s/y/i ${sourceCodeLocation}\\openAmf\\${win_build_conf}\\openAmfLoader.lib binWindows
                            """
                        } else if (win_lib_type == 'static') {
                            bat """
                                xcopy /s/y/i ${sourceCodeLocation}\\openAmf\\${win_build_conf}\\openAmf.lib binWindows
                            """
                        }
                        
                        zip archive: true, dir: "binWindows", glob: '', zipFile: "Windows_${win_build_name}.zip"

                        bat """
                            rename Windows_${win_build_name}.zip binWindows.zip
                        """
                        stash includes: "binWindows.zip", name: "AMF_Windows_${win_build_name}"
                        options[win_build_name + 'sha'] = sha1 "binWindows.zip"
                        println "[INFO] Saved sha: ${options[win_build_name + 'sha']}"

                    } catch (FlowInterruptedException error) {
                        println "[INFO] Job was aborted during build stage"
                        throw error
                    } catch (e) {
                        println(e.toString());
                        println(e.getMessage());
                        currentBuild.result = "FAILED"
                        println "[ERROR] Failed to build AMF on Windows"
                    } finally {
                        bat """
                            if exist binWindows rmdir /Q /S binWindows
                            if exist binWindows.zip del binWindows.zip
                        """
                    }
                }
            }
        }
    }
}


def generateBuildNameMacOS(String macos_build_conf, String macos_tool, String macos_lib_type) {
    if (macos_tool == "xcode") {
        return "${macos_tool}_${macos_lib_type}"
    } else {
        return "${macos_build_conf}_${macos_tool}_${macos_lib_type}"
    }
}


def executeBuildMacOS(Map options) {
    options.buildConfiguration.each() { macos_build_conf ->
        options.macosTool.each() { macos_tool ->
            options.macosLibraryType.each() { macos_lib_type ->

                println "Current build configuration: ${macos_build_conf}."
                println "Current tool: ${macos_tool}."
                println "Current library type: ${macos_lib_type}."

                macos_build_name = generateBuildNameMacOS(macos_build_conf, macos_tool, macos_lib_type)

                dir("amf/public/proj/OpenAMF_Autotests") {

                    try {

                        if (macos_tool == "cmake") {
                            if (!fileExists("generate-mac-${macos_lib_type}.sh")) {
                                println("[INFO] This configuration isn't supported now. It'll be skipped")
                                return
                            }
                            sh """
                                chmod u+x generate-mac-${macos_lib_type}.sh
                                ./generate-mac-${macos_lib_type}.sh ${macos_build_conf.capitalize()} >> ../../../../${STAGE_NAME}.${macos_build_name}.log 2>&1
                            """
                        } else if (macos_tool == "xcode") {
                            // skip double building if release and debug were chosen
                            if (!fileExists("generate-xcode-${macos_lib_type}.sh") || (macos_build_conf == 'debug' && buildConfiguration.size() == 2)) {
                                println("[INFO] This configuration isn't supported now. It'll be skipped")
                                return
                            }
                            sh """
                                chmod u+x generate-xcode-${macos_lib_type}.sh
                                ./generate-xcode-${macos_lib_type}.sh >> ../../../../${STAGE_NAME}.${macos_build_name}.log 2>&1
                            """
                        }

                        sh """
                            chmod u+x build-mac.sh
                            ./build-mac.sh >> ../../../../${STAGE_NAME}.${macos_build_name}.log 2>&1
                        """

                        sh """
                            mkdir binMacOS
                            cp build/autotests binMacOS/autotests
                        """

                        if (macos_lib_type == 'shared') {
                            sh """
                                cp build/openAmf/libopenAmf.dylib binMacOS/libopenAmf.dylib
                            """
                        } else if (macos_lib_type == 'static') {
                            sh """
                                cp build/openAmf/libopenAmf.a binMacOS/libopenAmf.a
                            """
                        }
                        
                        zip archive: true, dir: "binMacOS", glob: '', zipFile: "MacOS_${macos_build_name}.zip"

                        sh """
                            mv MacOS_${macos_build_name}.zip binMacOS.zip
                        """
                        stash includes: "binMacOS.zip", name: "AMF_MacOS_${macos_build_name}"
                        options[macos_build_name + 'sha'] = sha1 "binMacOS.zip"
                        println "[INFO] Saved sha: ${options[macos_build_name + 'sha']}"

                    } catch (FlowInterruptedException error) {
                        println "[INFO] Job was aborted during build stage"
                        throw error
                    } catch (e) {
                        println(e.toString());
                        println(e.getMessage());
                        currentBuild.result = "FAILED"
                        println "[ERROR] Failed to build AMF on MacOS"
                    } finally {
                        sh """
                            rm -rf binMacOS
                            rm -f binMacOS.zip
                        """
                    }
                }
            }
        }
    }
}


def generateBuildNameLinux(String linux_build_conf, String linux_lib_type) {
    return "${linux_build_conf}_${linux_lib_type}"
}


def executeBuildLinux(String osName, Map options) {
    options.buildConfiguration.each() { linux_build_conf ->
        options.linuxLibraryType.each() { linux_lib_type ->

            println "Current build configuration: ${linux_build_conf}."
            println "Current library type: ${linux_lib_type}."

            linux_build_name = generateBuildNameLinux(linux_build_conf, linux_lib_type)

            dir("amf/public/proj/OpenAMF_Autotests") {

                try {

                    if (!fileExists("generate-${linux_lib_type}-linux.sh")) {
                        println("[INFO] This configuration isn't supported now. It'll be skipped")
                        return
                    }

                    sh """
                        chmod u+x generate-${linux_lib_type}-linux.sh
                        ./generate-${linux_lib_type}-linux.sh ${linux_build_conf.capitalize()} >> ../../../../../${STAGE_NAME}.${linux_build_name}.log 2>&1
                    """

                    sh """
                        chmod u+x build-linux.sh
                        ./build-linux.sh >> ../../../../${STAGE_NAME}.${linux_build_name}.log 2>&1
                    """

                    //TODO implement copying of autotests executable file
                    sh """
                        mkdir binLinux
                    """

                    if (linux_lib_type == 'shared') {
                        sh """
                            cp linux/openAmf/libopenAmfLoader.a binLinux/libopenAmfLoader.a
                        """
                    } else if (linux_lib_type == 'static') {
                        sh """
                            cp linux/openAmf/libopenAmf.a binLinux/libopenAmf.a
                        """
                    }
                    
                    zip archive: true, dir: "binLinux", glob: '', zipFile: "Linux_${linux_build_name}.zip"

                    sh """
                        mv Linux_${linux_build_name}.zip binLinux.zip
                    """
                    stash includes: "binLinux.zip", name: "AMF_Linux_${linux_build_name}"
                    options[linux_build_name + 'sha'] = sha1 "binLinux.zip"
                    println "[INFO] Saved sha: ${options[linux_build_name + 'sha']}"

                } catch (FlowInterruptedException error) {
                    println "[INFO] Job was aborted during build stage"
                    throw error
                } catch (e) {
                    println(e.toString());
                    println(e.getMessage());
                    currentBuild.result = "FAILED"
                    println "[ERROR] Failed to build AMF on ${osName}"
                } finally {
                    sh """
                        rm -rf binLinux
                        rm -f binLinux.zip
                    """
                }
            }
        }
    }
}

def executeBuild(String osName, Map options) {
    try {

        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        
        switch(osName)
        {
            case 'Windows':
                executeBuildWindows(options);
                break;
            case 'MacOS':
                withEnv(["PATH=$WORKSPACE:$PATH"]) {
                    executeBuildMacOS(options);
                }
                break;
            default:
                withEnv(["PATH=$PWD:$PATH"]) {
                    executeBuildLinux(osName, options);
                }
        }
    } catch (e) {
        options.failureMessage = "[ERROR] Failed to build plugin on ${osName}"
        options.failureError = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}

def executePreBuild(Map options) {

    // manual job
    if (options.forceBuild) {
        options.executeBuild = true
        options.executeTests = true
    // auto job
    } else {
        options.executeBuild = true
        options.executeTests = true
        if (env.CHANGE_URL)
        {
            println "[INFO] Branch was detected as Pull Request"
        }
        else if("${env.BRANCH_NAME}" == "master")
        {
           println "[INFO] master branch was detected"
        } else {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
        }
    }

    if(!env.CHANGE_URL){

        currentBuild.description = ""
        ['projectBranch'].each
        {
            if(options[it] != 'master' && options[it] != "")
            {
                currentBuild.description += "<b>${it}:</b> ${options[it]}<br/>"
            }
        }

        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'], true)

        options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
        options.commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true).split('\r\n')[2].trim()
        options.commitSHA = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

        println "The last commit was written by ${options.commitAuthor}."
        println "Commit message: ${options.commitMessage}"
        println "Commit SHA: ${options.commitSHA}"

        currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
        currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
        currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"
        
        if (options.incrementVersion) {
            // TODO implement incrementing of version 
        }
    }

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25']]]);
    } else if (env.CHANGE_URL ) {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    }
}


def executeDeploy(Map options, List platformList, List testResultList) {
    cleanWS()

    dir("testResults") {
        testResultList.each() {

            try {
                unstash "$it"
            } catch(e) {
                echo "[ERROR] Failed to unstash ${it}"
                println(e.toString());
                println(e.getMessage());
            }

        }
    }

    dir("amf-report") {
        String branchName = env.BRANCH_NAME ?: options.projectBranch
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

        dir("amf/public/proj/OpenAMF_Autotests/Reports") {
            bat """
            set PATH=c:\\python35\\;c:\\python35\\scripts\\;%PATH%
            pip install --user -r requirements.txt >> ${STAGE_NAME}.requirements.log 2>&1
            python MakeReport.py --commit_hash "${options.commitSHA}" --branch_name "${branchName}" --test_results ..\\..\\..\\..\\..\\..\\testResults\\
            """
        }
    }

    utils.publishReport(this, "${BUILD_URL}", "testResults", "mainPage.html", "Test Report", "Summary Report")
}


def call(String projectBranch = "",
    String projectRepo = "git@github.com:amfdev/AMF.git",
    String platforms = 'Windows:AMD_WX7100,AMD_WX9100,AMD_RXVEGA,AMD_RadeonVII,AMD_RX5700XT,NVIDIA_RTX2080TI;MacOS:AMD_RXVEGA',
    String buildConfiguration = "release,debug",
    String winVisualStudioVersion = "2017,2019",
    String winLibraryType = "shared,static",
    String macosTool = "cmake,xcode",
    String macosLibraryType = "shared,static",
    String linuxLibraryType = "shared,static",
    Boolean incrementVersion = true,
    Boolean forceBuild = false,
    String testsFilter = "*") {
    try {
        String PRJ_NAME="AMF"
        String PRJ_ROOT="gpuopen"

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

        buildConfiguration = buildConfiguration.split(',')
        winVisualStudioVersion = winVisualStudioVersion.split(',')
        winLibraryType = winLibraryType.split(',')
        macosTool = macosTool.split(',')
        macosLibraryType = macosLibraryType.split(',')
        linuxLibraryType = linuxLibraryType.split(',')

        println "Win build configuration: ${buildConfiguration}"
        println "Win visual studio version: ${winVisualStudioVersion}"
        println "Win library type: ${winLibraryType}"

        println "MacOS visual studio version: ${macosTool}"
        println "MacOS library type: ${macosLibraryType}"

        println "Linux library type: ${linuxLibraryType}"

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                projectRepo:projectRepo,
                                incrementVersion:incrementVersion,
                                forceBuild:forceBuild,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                buildConfiguration:buildConfiguration,
                                winVisualStudioVersion:winVisualStudioVersion,
                                winLibraryType:winLibraryType,
                                macosTool:macosTool,
                                macosLibraryType:macosLibraryType,
                                linuxLibraryType:linuxLibraryType,
                                gpusCount:gpusCount,
                                TEST_TIMEOUT:90,
                                DEPLOY_TIMEOUT:150,
                                BUILDER_TAG:"BuilderAMF",
                                testsFilter:testsFilter
                                ])
    } catch(e) {
        currentBuild.result = "FAILED"
        failureMessage = "INIT FAILED"
        failureError = e.getMessage()
        println(e.toString());
        println(e.getMessage());

        throw e
    }
}
