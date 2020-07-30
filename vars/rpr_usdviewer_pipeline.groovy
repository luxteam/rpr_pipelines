import groovy.json.JsonOutput


def executeTests(String osName, String asicName, Map options)
{}

def executeBuildWindows(Map options)
{
    bat "del *.log"
    withEnv(["PATH=c:\\python366\\;c:\\python366\\scripts\\;${PATH}", "WORKSPACE=${env.WORKSPACE.toString().replace('\\', '/')}"]) {
        outputEnvironmentInfo("Windows", "${STAGE_NAME}.initEnv")

        dir("RadeonProVulkanWrapper") {
            checkOutBranchOrScm("${options.vulkanWrappersBranch}", "git@github.com:Radeon-Pro/RadeonProVulkanWrapper.git")

            bat """mkdir build
            cd build
            cmake ${options['cmakeKeysVulkanWrapper']} -G "Visual Studio 15 2017 Win64" .. >> ..\\..\\${STAGE_NAME}.VulkanWrapper.log 2>&1
            cmake --build . --config Release >> ..\\..\\${STAGE_NAME}.VulkanWrapper.log 2>&1"""
        }
//        dir("RadeonImageFilter") {
//            checkOutBranchOrScm("master", "git@github.com:GPUOpen-LibrariesAndSDKs/RadeonImageFilter.git")
//        }
//        dir("RadeonProRenderSDK") {
//            checkOutBranchOrScm("master", "git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderSDK.git")
//        }
        dir("RPRViewer") {
            checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

            powershell """(Get-Content USDPixar/build_scripts/build_usd.py) -replace '-DCMAKE_PREFIX_PATH="{depsInstDir}" ', '-DCMAKE_PREFIX_PATH="{depsInstDir};C:/JN/pyside-setup/pyside-setup/testenv3_install/py3.6-qt5.14.2-64bit-release/lib/cmake/PySide2-5.14.2.3;C:/JN/pyside-setup/pyside-setup/testenv3_install/py3.6-qt5.14.2-64bit-release/lib/cmake/Shiboken2-5.14.2.3" ' | Out-File -encoding ASCII USDPixar/build_scripts/build_usd.py"""

            bat """
            call "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\VC\\Auxiliary\\Build\\vcvars64.bat" >> ..\\${STAGE_NAME}.USD.log 2>&1
            
            pushd USDPixar
            git apply ..\\usd_dev.patch >> ..\\..\\${STAGE_NAME}.USD.log 2>&1
            popd
            
            python USDPixar/build_scripts/build_usd.py --build RPRViewer/build --src RPRViewer/deps RPRViewer/inst ^
            --build-args "USD,-DRPR_LOCATION=${WORKSPACE}/RPRViewer/HdRPRPlugin/deps/RPR/RadeonProRender -DVID_WRAPPERS_DIR=${WORKSPACE}/RadeonProVulkanWrapper -DSHIBOKEN_BINARY=C:/JN/pyside-setup/pyside-setup/testenv3_install/py3.6-qt5.14.2-64bit-release/bin/shiboken2.exe" >> ..\\${STAGE_NAME}.USD.log 2>&1
            
            set PATH=${WORKSPACE}\\RPRViewer\\RPRViewer\\inst\\bin;${WORKSPACE}\\RPRViewer\\RPRViewer\\inst\\lib;%PATH%
            set PYTHONPATH=${WORKSPACE}\\RPRViewer\\RPRViewer\\inst\\lib\\python;%PYTHONPATH%
            
            pushd RPRViewer\\build\\USDPixar
            cmake --build . --config RelWithDebInfo --target INSTALL >> ..\\..\\..\\..\\${STAGE_NAME}.USDPixar.log 2>&1 
            popd
    
            pushd HdRPRPlugin
            mkdir build
            pushd build
            
            cmake -G "Visual Studio 15 2017 Win64" -DUSD_ROOT=${WORKSPACE}/RPRViewer/RPRViewer/inst ^
            -Dpxr_DIR=${WORKSPACE}/RPRViewer/USDPixar ^
            -DRPR_BUILD_AS_HOUDINI_PLUGIN=FALSE ^
            -DCMAKE_INSTALL_PREFIX=${WORKSPACE}/RPRViewer/RPRViewer/inst ^
            -DPXR_USE_PYTHON_3=ON ^
            .. >> ..\\..\\..\\${STAGE_NAME}.HdRPRPlugin.log 2>&1

            cmake --build . --config RelWithDebInfo --target INSTALL >> ..\\..\\..\\${STAGE_NAME}.HdRPRPlugin.log 2>&1 
            """

        }
    }
}

def executeBuild(String osName, Map options)
{
    //TODO: remove full WS resetting
    cleanWS()

    try {
        switch (osName) {
            case 'Windows':
                executeBuildWindows(options);
                break;
            case 'OSX':
                println "OS isn't supported."
                break;
            default:
                println "OS isn't supported."
        }
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectRepo'], true)

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
        echo "branch was detected as Pull Request"
        options['isPR'] = true
        options.testsPackage = "PR"
    }
    else if(env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.testsPackage = "master"
    }
    else if(env.BRANCH_NAME) {
        options.testsPackage = "smoke"
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{}

def call(String projectBranch = "",
        // FIXME: to master branch
         String vulkanWrappersBranch = "db51573e1b65ff5f343f691bc95f7bc5400ef94d",
         String testsBranch = "master",
         String platforms = 'Windows',
         Boolean updateRefs = false,
         Boolean enableNotifications = true)
{
    def nodeRetry = []
    String PRJ_ROOT='rpr-core'
    String PRJ_NAME='USDViewer'
    String projectRepo='git@github.com:Radeon-Pro/RPRViewer.git'

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, null,
            [projectBranch:projectBranch,
             vulkanWrappersBranch:vulkanWrappersBranch,
             testsBranch:testsBranch,
             updateRefs:updateRefs,
             enableNotifications:enableNotifications,
             PRJ_NAME:PRJ_NAME,
             PRJ_ROOT:PRJ_ROOT,
             projectRepo:projectRepo,
             BUILDER_TAG:'RPRUSDVIEWER',
             executeBuild:true,
             executeTests:true,
             BUILD_TIMEOUT:90,
             DEPLOY_TIMEOUT:45,
             cmakeKeysVulkanWrapper:"-DCMAKE_BUILD_TYPE=Release -DVW_ENABLE_RRNEXT=OFF",
             nodeRetry: nodeRetry])
}
