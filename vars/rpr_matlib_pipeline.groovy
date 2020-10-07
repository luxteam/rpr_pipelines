def executeTestCommand(String osName)
{}

def executeTests(String osName, String asicName, Map options)
{}

def executeBuildWindows()
{
    withEnv(["PATH=c:\\python35\\;c:\\python35\\scripts\\;${PATH}"]) {
        bat """
        pushd MatLibPkg
        build_windows_installer.cmd >> ..\\${STAGE_NAME}.log 2>&1        
        """
        bat "xcopy MatLibPkg\\RadeonProMaterialLibrary.msi RadeonProRenderMaterialLibrary.msi*"
    }
}

def executeBuildOSX()
{
    sh """
    pushd MatLibPkg
    ./build_osx_installer.sh >> ../${STAGE_NAME}.log 2>&1
    cp ./.installer_build/RadeonProRenderMaterialLibrary_2.0.0.dmg ../RadeonProRenderMaterialLibrary.dmg
    popd
    """
}

def executeBuildLinux()
{
    sh """
    cd MatLibPkg
    ./build_linux_installer.sh >> ../${STAGE_NAME}.log 2>&1
    cp ./.installer_build/RadeonProRenderMaterialLibraryInstaller_2.0.run ../RadeonProRenderMaterialLibrary.run
    cd ..
    """
}

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectURL'])

    options.AUTHOR_NAME = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%s -n 1", returnStdout: true).split('\r\n')[2].trim().replace('\n', '')
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
    options.commitShortSHA = options.commitSHA[0..6]

    println "The last commit was written by ${options.AUTHOR_NAME}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"
    println "Commit shortSHA: ${options.commitShortSHA}"

    if (options.projectBranch){
        currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }

    currentBuild.description += "<b>Commit author:</b> ${options.AUTHOR_NAME}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"
}

def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectURL'])
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows': 
            executeBuildWindows(); 
            break;
        case 'OSX':
            executeBuildOSX();
            break;
        default: 
            executeBuildLinux();
        }
        archiveArtifacts "RadeonProRenderMaterialLibrary*"
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{}

def call(String projectBranch = "",
         String projectURL = 'git@github.com:Radeon-Pro/RadeonProRenderPkgPlugin.git',
         String platforms = 'Windows;OSX;Ubuntu18',
         Boolean enableNotifications = true)
{
    String PRJ_ROOT="rpr-plugins"
    String PRJ_NAME="RadeonProRenderMaterialLibrary"

    properties([[$class: 'BuildDiscarderProperty', strategy: 
                 [$class: 'LogRotator', artifactDaysToKeepStr: '', 
                  artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5']]]);
    
    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, null,
                           [projectBranch:projectBranch,
                            enableNotifications:enableNotifications,
                            executeBuild:true,
                            executeTests:false,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            BUILD_TIMEOUT:'25',
                            TEST_TIMEOUT:'25',
                            BUILDER_TAG:'Builder',
                            projectURL:projectURL,
                            retriesForTestStage:1])
}
