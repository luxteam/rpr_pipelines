def executeTests(String osName, String asicName, Map options)
{
}

def executeBuildWindows(Map options)
{
    powershell """
        cd C:\\Cinema4d_r23_project_tool_314356_win-macos
        Start-Process -Wait -FilePath "kernel_app_64bit.exe" -ArgumentList "g_updateproject=${WORKSPACE} g_console=false" -PassThru
    """

    bat """
        cd plugins\\project
        set msbuild=\"C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Professional\\MSBuild\\Current\\Bin\\MSBuild.exe\"
        %msbuild% plugins.sln -property:Configuration=Release >> ..\\..\\${STAGE_NAME}.log 2>&1
    """
    
    // TODO: filter files for archive
    zip archive: true, dir: "plugins\\gpurenderer", glob: '', zipFile: "Cinema4D_Release_Windows.zip"
    
}


def executeBuildOSX(Map options)
{
    sh """ 
        cd $CIS_TOOLS/../cinema4d_r23_project_tool_314356_win-macos
        kernel_app.app/Contents/MacOS/kernel_app -g_updateproject=${WORKSPACE} g_console=false
    """

    sh """
        cd plugins/project
        xcodebuild -scheme gpurenderer -configuration Release -project ./plugins.xcodeproj -UseModernBuildSystem=NO >> ../../${STAGE_NAME}.log 2>&1
    """
    
    // TODO: filter files for archive
    //sh """
    //    tar cf Cinema4D_Release_MacOS.tar plugins/gpurenderer
    //"""

    //archiveArtifacts "Cinema4D_Release_MacOS.tar"
    zip archive: true, dir: "plugins/gpurenderer", glob: '', zipFile: "Cinema4D_Release_MacOS.zip"
    
}



def executeBuild(String osName, Map options)
{
    checkOutBranchOrScm(options.projectBranch, "git@github.com:Radeon-Pro/RadeonProRenderC4DPlugin.git")
    outputEnvironmentInfo(osName)

    try {
        switch (osName) {
            case 'Windows':
                executeBuildWindows(options)
                break
            case 'OSX':
                executeBuildOSX(options)
                break
            default:
                println "OS isn't supported."
        }
    } catch (e) {
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}


def executePreBuild(Map options)
{
    checkOutBranchOrScm(options.projectBranch, "git@github.com:Radeon-Pro/RadeonProRenderC4DPlugin.git", true)

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim().replace('\n', '')
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"

    if (options.projectBranch) {
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }
    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    if (env.CHANGE_URL) {
        println("Build was detected as Pull Request")
        options.testsPackage = "PR"
    } else if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.testsPackage = "master"
    } else if (env.BRANCH_NAME) {
        options.testsPackage = "smoke"
    }
}


def executeDeploy(Map options, List platformList, List testResultList)
{
}


def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows;OSX',
         Boolean updateRefs = false,
         Boolean enableNotifications = true)
{
    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, null,
            [projectBranch:projectBranch,
             testsBranch:testsBranch,
             updateRefs:updateRefs,
             enableNotifications:enableNotifications,
             PRJ_NAME:'RadeonProRenderCinema4DPlugin',
             PRJ_ROOT:'rpr-plugins',
             executeBuild:true,
             executeTests:false
             ])
}
