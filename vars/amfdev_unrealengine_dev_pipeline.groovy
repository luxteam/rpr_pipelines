import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import hudson.AbortException

def executeTestsWindows(String osName, String asicName, Map options)
{
    try {
        cleanWS(osName)
        unstash "UEWindowsTests"
        unzip zipFile: "WindowsTests.zip", dir: "UETests", quiet: true
    } catch(e) {
        println("[ERROR] Failed to prepare tests on ${env.NODE_NAME}")
        println(e.toString())
        throw e
    }

    options.versions.each() { ue_version ->

        if (fileExists("UETests\\Prerequirements\\${ue_version}")) {
            println("[INFO] Prerequirements found")
            println("[INFO] Install prerequirements")
            try {
                powershell """
                    Start-Process -Wait -FilePath 'UE4PrereqSetup_x64.exe' -ArgumentList '/S' -PassThru
                """
            } catch (e) {
                println("[ERROR] Failed to install prerequirements")
                throw e
            }

        } else {
            println("[ERROR] Can't find prerequirements for this version of UE")
            return
        }

        options.buildConfigurations.each() { build_conf ->
            options.visualStudioVersions.each() { vs_ver ->
                options.graphicsAPI.each() { graphics_api ->

                    println "Current UnrealEngine version: ${ue_version}."
                    println "Current build configuration: ${build_conf}."
                    println "Current VS version: ${vs_ver}."
                    println "Current graphics API: ${graphics_api}."

                    win_build_name = generateBuildNameWindows(ue_version, build_conf, vs_ver, graphics_api)

                    if (!fileExists("UETests\\Tests\\${win_build_name}")) {
                        println("[ERROR] Can't find tests for this configuration")
                        return
                    }

                    dir("UETests\\Tests\\${win_build_name}") {
                        String testsFolder = options['pluginType'] == 'Stitch' ? 'StitchAmf' : "${options.testsName}${options.pluginType}Cpp"
                        String logsName = testsFolder
                        testsFolder = "${testsFolder}_${build_conf}"
                        if (graphics_api == "Vulkan") {
                            testsFolder = "${testsFolder}_Vulkan"
                        }
                        dir("${testsFolder}") {
                            try {
                                if (options['pluginType'] == 'Amf') {
                                    String[] testBats = bat(script: '@dir /b *.bat', returnStdout: true).trim().split('\n')
                                    for (testBat in testBats) {
                                        try {
                                            timeout(time: 15, unit: 'SECONDS') {
                                                bat """
                                                    ${testBat.trim()}
                                                """
                                            }
                                        } catch (FlowInterruptedException e) {
                                            //Tests are playing endlessly. Stop them
                                            e.getCauses().each(){
                                                String causeClassName = it.getClass().toString()
                                                println "Interruption cause: ${causeClassName}"
                                                if (causeClassName.contains("ExceededTimeout")) {
                                                    println("[INFO] Test ${testBat.trim()} was stopped")
                                                    try {
                                                        dir("${logsName}\\Saved\\Logs") {
                                                            bat """
                                                                rename ${logsName}.log ${STAGE_NAME}.${testBat.trim().replace('.bat', '')}.log
                                                            """
                                                        }
                                                    } catch (e1) {
                                                        println("[ERROR] Failed to access logs of ${win_build_name} configuration on ${asicName}-${osName}")
                                                    }
                                                } else {
                                                    throw e
                                                }
                                            }
                                        } 
                                    }
                                } else if (options['pluginType'] == 'Stitch') {
                                    try {
                                        timeout(time: 15, unit: 'SECONDS') {
                                            bat """
                                                StitchAmf.exe
                                            """
                                        }
                                    } catch (FlowInterruptedException e) {
                                        //Tests are playing endlessly. Stop them
                                        e.getCauses().each(){
                                            String causeClassName = it.getClass().toString()
                                            println "Interruption cause: ${causeClassName}"
                                            if (causeClassName.contains("ExceededTimeout")) {
                                                println("[INFO] Stitch tests was stopped")
                                                try {
                                                    dir("${logsName}\\Saved\\Logs") {
                                                        bat """
                                                            rename StitchAmf.log ${STAGE_NAME}.StitchAmf.log
                                                        """
                                                    }
                                                } catch (e1) {
                                                    println("[ERROR] Failed to access logs of ${win_build_name} configuration on ${asicName}-${osName}")
                                                }
                                            } else {
                                                throw e
                                            }
                                        }
                                    }
                                }
                            } catch (FlowInterruptedException e) {
                                throw e
                            } catch (e) {
                                println(e.toString());
                                println(e.getMessage());
                                currentBuild.result = "FAILURE"
                                println "[ERROR] Failed during testing ${win_build_name} configuration on ${asicName}-${osName}"
                            } finally {
                                archiveArtifacts artifacts: "${logsName}/Saved/Logs/*.*", allowEmptyArchive: true
                            }
                        }
                    }
                }
            }
        }
    }
}

def executeTests(String osName, String asicName, Map options) {
    switch(osName) {
        case 'Windows':
            executeTestsWindows(osName, asicName, options)
            break;
        case 'OSX':
            println("[WARNING] OSX is not supported")
            break;
        default:
            println("[WARNING] ${osName} is not supported")
            break;
    }
}

def getPreparedUE(String version, String pluginType, Boolean forceDownloadUE) {
    String targetFolderPath = "${CIS_TOOLS}\\..\\PreparedUE\\UE-${version}"
    String folderName = pluginType == "Standard" ? "UE-${version}" : "UE-${version}-${pluginType}"
    if (!fileExists(targetFolderPath) || forceDownloadUE) {
        println("[INFO] UnrealEngine will be downloaded and configured")
        bat """
            Build.bat Engine ${version} PrepareUE Development >> ..\\PrepareUE.${version}.log 2>&1
        """

        dir("Logs") {
            String logsFolder = bat(script: '@dir /b Build_*', returnStdout: true).trim()
            bat """
                rename ${logsFolder} PrepareUE.${version}
            """

            try {
                dir("PrepareUE.${version}") {
                    if (fileExists("results.csv")) {
                    String failures = bat(script: '@findstr "failed" results.csv', returnStdout: true).trim()
                        if (failures) {
                            println("[ERROR] Failed to prepare UE")
                            throw new Exception("Failed to prepare UE")
                        }
                    } else {
                        throw new Exception("Can't find result.csv file")
                    }
                }
            } catch (AbortException e) {
                // findstr returns exit code 1 if it didn't found any suitable line
            }

        }

        println("[INFO] Prepared UE is ready. Saving it for use in future builds...")
        bat """
            xcopy /s/y/i UE-${version} ${targetFolderPath} >> nul

            rename UE-${version} ${folderName}
        """
    } else {
        println("[INFO] Prepared UnrealEngine found. Copying it...")
        dir(folderName) {
            bat """
                xcopy /s/y/i ${targetFolderPath} . >> nul
            """
        }
    }
}


def generateBuildNameWindows(String ue_version, String build_conf, String vs_ver, String graphics_api) {
    return "${ue_version}_${build_conf}_vs${vs_ver}_${graphics_api}"
}


def executeBuildWindows(Map options)
{
    options.versions.each() { ue_version ->
        options.buildConfigurations.each() { build_conf ->
            options.visualStudioVersions.each() { vs_ver ->
                options.graphicsAPI.each() { graphics_api ->

                    println "Current UnrealEngine version: ${ue_version}."
                    println "Current build configuration: ${build_conf}."
                    println "Current VS version: ${vs_ver}."
                    println "Current graphics API: ${graphics_api}."

                    win_build_name = generateBuildNameWindows(ue_version, build_conf, vs_ver, graphics_api)

                    if (graphics_api == "DX11") {
                        graphics_api = " "
                    }
                    String pluginBranch = ""
                    if (options['pluginBranch']) {
                        if (options['pluginType'] == 'Amf') {
                            pluginBranch = "AmfBranch: ${options.pluginBranch}"
                        } else if (options['pluginType'] == 'Stitch') {
                            pluginBranch = "StitchBranch: ${options.pluginBranch}"
                        }
                    }

                    try {
                        dir("U\\integration")
                        {
                            getPreparedUE(ue_version, options['pluginType'], options['forceDownloadUE'])
                            bat """
                                Build.bat ${options.targets.join(' ')} ${ue_version} ${options.pluginType} ${build_conf} ${options.testsName} ${vs_ver} ${graphics_api} ${options.source} ${pluginBranch} Dirty >> ..\\${STAGE_NAME}.${win_build_name}.log 2>&1
                            """

                            dir("Logs") {
                                String logsFolder = bat(script: '@dir /b Build_*', returnStdout: true).trim()
                                bat """
                                    rename ${logsFolder} Build.${win_build_name}
                                """

                                dir("Build.${win_build_name}") {
                                    if (fileExists("results.csv")) {
                                        try {
                                            String[] successes = bat(script: '@findstr "succeeded" results.csv', returnStdout: true).trim().split('\n')
                                            println("[INFO] Successfully executed targets (${successes.length}):")
                                            for (success in successes) {
                                                println("[INFO] Target: ${success.trim().split(',')[0]}")
                                            }
                                        } catch (AbortException e) {
                                            // findstr returns exit code 1 if it didn't found any suitable line
                                            println("[INFO] Can't find successfully executed targets")
                                        }
                                        
                                        try {
                                            String[] failures = bat(script: '@findstr "failed" results.csv', returnStdout: true).trim().split('\n')
                                            println("[INFO] Failed targets (${failures.length}):")
                                            for (failure in failures) {
                                                println("[INFO] Target: ${failure.trim().split(',')[0]}")
                                            }
                                            println("[ERROR] Failed to build UE (there are failed targets)")
                                            throw new Exception("Failed to build UE (there are failed targets)")
                                        } catch (AbortException e) {
                                            // findstr returns exit code 1 if it didn't found any suitable line
                                            println("[INFO] Can't find failed targets")
                                        }
                                    } else {
                                        throw new Exception("Can't find result.csv file")
                                    }
                                }
                            }

                            try {
                                if (options.targets.contains("Tests")) {
                                    dir ("Deploy\\Tests") {
                                        bat """
                                            rename ${ue_version} ${win_build_name}
                                        """
                                    }
                                }  
                            } catch (e) {
                                println("[ERROR] Failed to access tests")
                                throw e
                            }

                        }
                    } catch (FlowInterruptedException e) {
                        throw e
                    } catch (e) {
                        println(e.toString());
                        println(e.getMessage());
                        currentBuild.result = "FAILURE"
                        println "[ERROR] Failed to build UE on Windows"
                    } finally {
                        String folderName = options['pluginType'] == "Standard" ? "UE-${ue_version}" : "UE-${ue_version}-${options.pluginType}"
                        dir("U\\integration") {
                            bat """
                                if exist ${folderName} rmdir /Q /S ${folderName}
                            """
                        }
                    }
                }
            }
        }
    }
    if (options.targets.contains("Tests")) {
        dir ("U\\integration") {
            if (fileExists("Deploy\\Tests")) {
                zip archive: false, dir: "Deploy", glob: '', zipFile: "WindowsTests.zip"

                stash includes: "WindowsTests.zip", name: "UEWindowsTests"
            } else {
                println "[ERROR] Can't find folder with tests!"
                currentBuild.result = "FAILURE"
            }
        }
    }
}

def executeBuild(String osName, Map options)
{
    try {        
        dir('U')
        {
            checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/UnrealEngine_dev.git')
            outputEnvironmentInfo(osName)
        }

        switch(osName)
        {
        case 'Windows': 
            executeBuildWindows(options)
            break;
        case 'OSX':
            println("[WARNING] OSX is not supported")
            break;
        default: 
            println("[WARNING] ${osName} is not supported")
        }
    }
    catch (e) {
        throw e
    }
    finally {
        dir("U") {
            archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
            archiveArtifacts artifacts: "integration/Logs/**/*.*", allowEmptyArchive: true
        }
    }                        
}

def executePreBuild(Map options)
{
    // manual job
    if (options.forceBuild) {
        options.executeBuild = true
        options.executeTests = true
    // auto job
    } else {
        // TODO: impelement initialization for auto jobs
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

        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:luxteam/UnrealEngine_dev.git', true)

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

def executeDeploy(Map options, List platformList, List testResultList)
{
    // TODO: implement deploy stage
}


def call(String projectBranch = "",
         String platforms = 'Windows',
         String targets = '',
         String versions = '',
         String pluginType = '',
         String buildConfigurations = '',
         String testsName = '',
         String visualStudioVersions = '',
         String graphicsAPI = '',
         String pluginRepository = '',
         String pluginBranch = '',
         Boolean forceDownloadUE = false,
         Boolean forceBuild = false,
         Boolean enableNotifications = false) {
    try
    {
        String PRJ_NAME="UE"
        String PRJ_ROOT="gpuopen"

        targets = targets.split(', ')
        versions = versions.split(',')
        buildConfigurations = buildConfigurations.split(',')
        visualStudioVersions = visualStudioVersions.split(',')
        graphicsAPI = graphicsAPI.split(',')

        println "Targets: ${targets}"
        println "Versions: ${versions}"
        println "Plugin type: ${pluginType}"
        println "Build configurations: ${buildConfigurations}"
        println "Tests name: ${testsName}"
        println "Visual Studio version: ${visualStudioVersions}"
        println "Graphics API: ${graphicsAPI}"

        String source = ""
        if (pluginRepository.contains("amfdev")) {
            source = "Clone"
        } else if (pluginRepository.contains("GPUOpen")) {
            source = "Origin"
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                targets:targets,
                                versions:versions,
                                pluginType:pluginType,
                                buildConfigurations:buildConfigurations,
                                testsName:testsName,
                                visualStudioVersions:visualStudioVersions,
                                graphicsAPI:graphicsAPI,
                                source:source,
                                pluginBranch:pluginBranch,
                                forceDownloadUE:forceDownloadUE,
                                forceBuild:forceBuild,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                BUILDER_TAG:'BuilderU',
                                BUILD_TIMEOUT:360])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
