def executeTests(String osName, String asicName, Map options)
{
}


def executeBuildWindows(String osName, Map options)
{
    bat """
        set msbuild="C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe" >> ..\\${STAGE_NAME}.log 2>&1
        mkdir build
        cd build
        cmake -G "Visual Studio 15 2017 Win64" -DRIF_BUILD=1 -DMIOPEN_BACKEND=OpenCL -DBoost_INCLUDE_DIR=C:/local/boost_1_69_0 -DBoost_LIB_DIR=C:/local/boost_1_69_0/lib64-msvc-14.1 .. >> ..\\${STAGE_NAME}.log 2>&1
        %msbuild% INSTALL.vcxproj -property:Configuration=Release >> ..\\${STAGE_NAME}.log 2>&1
    """

    bat """
        mkdir release\\miopen
        xcopy /s/y/i build\\bin\\Release\\MIOpen.dll release
        xcopy /s/y/i build\\lib\\Release\\MIOpen.lib release
        xcopy /s/y/i build\\include\\miopen\\*.h release\\miopen
        xcopy /s/y/i include\\miopen\\*.h release\\miopen
    """

    zip archive: true, dir: 'release', zipFile: "${options.packageName}-${osName}.zip"
}


def executeBuildOSX(String osName, Map options)
{
    println "OSX build is not supported"
}


def executeBuildUbuntu(String osName, Map options)
{
    sh """
        mkdir build
        cd build
        cmake -DRIF_BUILD=1 -DMIOPEN_BACKEND=OpenCL .. >> ../${STAGE_NAME}.log 2>&1
        cmake --build . --config Release >> ../${STAGE_NAME}.log 2>&1
    """
 
    sh """
        mkdir release
        mkdir release/miopen
        cp -R build/lib/libMIOpen.so* release
        cp build/include/miopen/*.h release/miopen
        cp include/miopen/*.h release/miopen
    """
 
    sh """
        tar cf ${options.packageName}-${osName}.tar release
    """

    archiveArtifacts "${options.packageName}-${osName}.tar"

}


def executeBuildCentOS(String osName, Map options)
{
    sh """
        mkdir build
        cd build
        cmake -DRIF_BUILD=1 -DMIOPEN_BACKEND=OpenCL -DBoost_INCLUDE_DIR=/opt/boost/include -DBoost_LIB_DIR=/opt/boost/lib -DCMAKE_CXX_FLAGS="-fPIC" .. >> ../${STAGE_NAME}.log 2>&1
        cmake --build . --config Release >> ../${STAGE_NAME}.log 2>&1
    """

    sh """
        mkdir release
        mkdir release/miopen
        cp -R build/lib/libMIOpen.so* release
        cp build/include/miopen/*.h release/miopen
        cp include/miopen/*.h release/miopen
    """
 
    sh """
        tar cf ${options.packageName}-${osName}.tar release
    """

    archiveArtifacts "${options.packageName}-${osName}.tar"

}


def executePreBuild(Map options)
{
    checkOutBranchOrScm(options.projectBranch, options.projectRepo, true)

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true).split('\r\n')[2].trim()
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"

    currentBuild.description = "<b>GitHub repo:</b> ${options.projectRepo}<br/>"

    if (options.projectBranch){
        currentBuild.description += "<b>Project branch:</b> ${options.projectBranch}<br/>"
    } else {
        currentBuild.description += "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
    }

    currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
    currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    currentBuild.description += "<b>Commit SHA:</b> ${options.commitSHA}<br/>"

    options.commit = bat (
        script: '''@echo off
                   git rev-parse --short=6 HEAD''',
        returnStdout: true
    ).trim()

    String branch = env.BRANCH_NAME ? env.BRANCH_NAME : env.Branch
    options.branch = branch.replace('origin/', '')

    String packageName = "miopen" + (options.branch ? '-' + options.branch : '') + (options.commit ? '-' + options.commit : '')
    options.packageName = packageName.replaceAll('[^a-zA-Z0-9-_.]+','')

    if (env.CHANGE_URL) {
        println("Build was detected as Pull Request")
    }

}


def executeBuild(String osName, Map options)
{
    cleanWS(osName)

    try {
        checkOutBranchOrScm(options.projectBranch, options.projectRepo)
        outputEnvironmentInfo(osName)

        switch (osName) {
            case 'Windows':
                executeBuildWindows(osName, options)
                break
            case 'OSX':
                executeBuildOSX(osName, options)
                break
            case 'Ubuntu18':
                executeBuildUbuntu(osName, options)
                break
            default:
                executeBuildCentOS(osName, options)
        }

        if (options.updateBinaries) {
            uploadFiles("release/*", "/volume1/CIS/rpr-ml/MIOpen/${osName}")
        }

    } catch (e) {
        println(e.getMessage())
        error_message = e.getMessage()
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts "*.log"
    }
}


def executeDeploy(Map options, List platformList, List testResultList)
{
}


def call(String projectRepo='git@github.com:BenjaminCoquelle/MIOpen.git',
         String projectBranch = "master",
         String platforms = 'Windows;Ubuntu18;CentOS7',
         String updateBinaries = false,
         String PRJ_ROOT='rpr-ml',
         String PRJ_NAME='MIOpen'
         )
{


    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, null,
                           [platforms:platforms,
                            projectBranch:projectBranch,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            updateBinaries:updateBinaries,
                            executeBuild:true,
                            executeTests:false,
                            projectRepo:projectRepo
                            ])
}
