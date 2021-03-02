import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def executeBuildWindows(Map options) {
    String buildName = "${options.buildConfiguration}_${options.buildPlatform.replace(' ', '')}_${options.pluginVersion}"
    String msBuildPath = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Professional\\MSBuild\\Current\\Bin\\MSBuild.exe"

    try {
        dir ("RadeonProRenderInventorPlugin") {
            // build plugin
            bat """
                set msbuild="${msBuildPath}"
                %msbuild% RadeonProRenderInventorPlugin.sln /target:build /maxcpucount /property:Configuration="${options.buildConfiguration}";Platform="${options.buildPlatform}" >> "${STAGE_NAME}_${buildName}.log" 2>&1
            """

            // copy build results in separate directory
            bat """
                mkdir buildResults
            """

            String buildPlatformPath = (options.buildPlatform == 'Any CPU') ? "" : options.buildPlatform
            bat """
                xcopy /y/i "RadeonProRenderInventorPlugin\\bin\\${buildPlatformPath}\\${options.buildConfiguration}\\UsdConvertor.dll" buildResults
            """

            // copy thirdparty libraries and necessary files from repository in results directory
            bat """
                xcopy /y/i RadeonProRenderInventorPlugin\\ThirdParty\\usd-unity-sdk\\USD.NET.dll buildResults
                xcopy /y/i RadeonProRenderInventorPlugin\\ThirdParty\\usd-unity-sdk\\UsdCs.dll buildResults
                xcopy /y/i RadeonProRenderInventorPlugin\\ThirdParty\\usd-unity-sdk\\libusd_ms.dll buildResults
                xcopy /y/i RadeonProRenderInventorPlugin\\Autodesk.UsdConvertor.Inventor.addin  buildResults
            """

            zip archive: true, dir: "buildResults", glob: '', zipFile: "Windows_${buildName}.zip"
            rtp nullAction: '1', parserName: 'HTML', stableText: """<h3><a href="${BUILD_URL}/artifact/Windows_${buildName}.zip">[BUILD: ${BUILD_ID}] Windows_${buildName}.zip</a></h3>"""

            bat """
                rename Windows_${buildName}.zip PluginWindows.zip
            """
            stash includes: "PluginWindows.zip", name: 'appWindows'
            options.pluginWinSha = sha1 "PluginWindows.zip"
        }
    } catch (FlowInterruptedException error) {
        println "[INFO] Job was aborted during build stage"
        throw error
    } catch (e) {
        println(e.toString())
        println(e.getMessage())
        currentBuild.result = "FAILED"
        println "[ERROR] Failed to build Inventor plugin on Windows"
    }
}


def executeBuild(String osName, Map options) {
    try {
        dir('RadeonProRenderInventorPlugin') {
            checkOutBranchOrScm(options.projectBranch, 'git@github.com:Radeon-Pro/RadeonProRenderInventorPlugin.git', true)
        }
        
        switch(osName) {
            case 'Windows':
                executeBuildWindows(options)
                break
        }
    } catch (e) {
        options.failureMessage = "[ERROR] Failed to build plugin on ${osName}"
        options.failureError = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    } finally {
        dir ('RadeonProRenderInventorPlugin') {
            archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        }
    }
}


def executePreBuild(Map options) {
    // manual job
    if (options.forceBuild) {
        options.executeBuild = true
        // TODO add tests stage initialization
        //options.executeTests = true
    // auto job
    } else {
        options.executeBuild = true
        // TODO add tests stage initialization
        //options.executeTests = true
        if (env.CHANGE_URL)
        {
            println "[INFO] Branch was detected as Pull Request"
            // TODO add tests stage initialization
            //options.testsPackage = "PR"
        }
        else if("${env.BRANCH_NAME}" == "master")
        {
           println "[INFO] master branch was detected"
            // TODO add tests stage initialization
           //options.testsPackage = "master"
        } else {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
            // TODO add tests stage initialization
            //options.testsPackage = "smoke"
        }
    }

    dir ('RadeonProRenderInventorPlugin') {
        checkOutBranchOrScm(options.projectBranch, 'git@github.com:Radeon-Pro/RadeonProRenderInventorPlugin.git', true)

        options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
        options.commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true).split('\r\n')[2].trim()
        options.commitSHA = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

        // clone repo with version increment
        dir("../inc") {
            checkOutBranchOrScm("master", "git@github.com:luxteam/RadeonProRenderInventorPluginIncrement.git", true)

            options.pluginVersion = bat(script: "@git describe --tags --abbrev=0", returnStdout: true).trim()
        }

        println """
            The last commit was written by ${options.commitAuthor}
            Commit message: ${options.commitMessage}
            Commit SHA: ${options.commitSHA}
        """

        if (options.incrementVersion) {
            if (env.BRANCH_NAME == "master") {
                println("[INFO] Incrementing version of change made by ${options.commitAuthor}.")

                dir("../inc") {
                    // init submodule
                    checkOutBranchOrScm("master", "git@github.com:luxteam/RadeonProRenderInventorPluginIncrement.git")

                    println("[INFO] Current build version: ${options.pluginVersion}")

                    dir("RadeonProRenderInventorPlugin") {
                        bat """
                            git checkout -B master origin/master
                        """
                    }

                    String pluginVersion = utils.incrementVersion(self: this, currentVersion: options.pluginVersion, index: 4)
                    Boolean hasUpdates

                    try {
                        bat """
                            git add RadeonProRenderInventorPlugin
                            git commit -m "buildmaster: version update to ${pluginVersion}"
                        """
                        hasUpdates = true
                    } catch (e) {
                        // nothing to commit
                        hasUpdates = false
                    }

                    if (hasUpdates) {
                        println("[INFO] New commits were found. Version incrementing in progress...")

                        options.pluginVersion = pluginVersion
                        println("[INFO] New build version: ${options.pluginVersion}")

                        bat """
                            git tag -a "${options.pluginVersion}" -m "version update to ${options.pluginVersion}"
                            git push origin HEAD:master --tags
                        """
                    } else {
                        println("[INFO] New commit weren't found. Version incrementing won't run")
                    }
                }
            }
        }

        if (options.projectBranch){
            currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
        } else {
            currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
        }
        currentBuild.description += "<b>Version:</b> ${options.pluginVersion}<br/>"
        currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
        currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
        currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"
    }
}


def call(String projectBranch = "",
    String platforms = 'Windows',
    String buildConfiguration = "release",
    String buildPlatform = "x64",
    Boolean incrementVersion = true,
    Boolean forceBuild = false) {
    try {
        String PRJ_NAME="RadeonProRenderInventorPlugin"
        String PRJ_ROOT="rpr-plugins"

        gpusCount = 0
        platforms.split(';').each() { platform ->
            List tokens = platform.tokenize(':')
            if (tokens.size() > 1) {
                gpuNames = tokens.get(1)
                gpuNames.split(',').each() {
                    gpusCount += 1
                }
            }
        }

        // TODO impelemnt Test and Deploy stages
        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, null,
                               [projectBranch:projectBranch,
                                incrementVersion:incrementVersion,
                                forceBuild:forceBuild,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                buildConfiguration:buildConfiguration,
                                buildPlatform:buildPlatform,
                                gpusCount:gpusCount,
                                BUILDER_TAG:"BuilderInv",
                                ])
    } catch(e) {
        currentBuild.result = "FAILED"
        failureMessage = "INIT FAILED"
        failureError = e.getMessage()
        println(e.toString())
        println(e.getMessage())
        throw e
    }
}