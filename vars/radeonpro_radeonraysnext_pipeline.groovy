def executeTestCommand(String osName)
{
    switch(osName) {
        case 'Windows':
            bat """
                cd ..\\build\\unittests
                call Release\\UnitTests.exe  --gtest_output=xml:..\\..\\${STAGE_NAME}.gtest.xml >> ..\\..\\${STAGE_NAME}.log  2>&1
            """
            break
        case 'OSX':
            sh """
                cd ../build/unittests
                ./UnitTests --gtest_output=xml:../../${STAGE_NAME}.gtest.xml >> ../../${STAGE_NAME}.log  2>&1
            """
            break
        default:
            sh """
                cd ../build/unittests
                ./UnitTests --gtest_output=xml:../../${STAGE_NAME}.gtest.xml >> ../../${STAGE_NAME}.log  2>&1
            """
    }
}

def executeTests(String osName, String asicName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

        outputEnvironmentInfo(osName)
        unstash "app${osName}"
        unstash "app${osName}_shaders"

        dir('unittests') {
            executeTestCommand(osName)
        }                
    } catch (e) {
        println(e.toString())
        println(e.getMessage())
        throw e
    } finally {
        archiveArtifacts "*.log"
        junit "*.gtest.xml"
    }
}

def executeBuildWindows()
{
    bat """
        mkdir build
        cd build
        cmake -DCMAKE_BUILD_TYPE=Release -G "Visual Studio 15 2017 Win64" .. >> ..\\${STAGE_NAME}.log 2>&1
        cmake --build . --config Release >> ..\\${STAGE_NAME}.log 2>&1
    """
}

def executeBuildOSX()
{
    sh """
        mkdir build
        cd build
        cmake -DCMAKE_BUILD_TYPE=Release .. >> ../${STAGE_NAME}.log 2>&1
        make -j 4 >> ../${STAGE_NAME}.log 2>&1
    """
}

def executeBuildLinux()
{
    sh """
        mkdir build
        cd build
        cmake -DCMAKE_BUILD_TYPE=Release -DRR_NEXT_ENABLE_EXAMPLES=OFF .. >> ../${STAGE_NAME}.log 2>&1
        make -j 8 >> ../${STAGE_NAME}.log 2>&1
    """
}

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options.projectBranch, options.projectRepo)

    options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
    options.commitMessage = bat ( script: "git log --format=%%B -n 1", returnStdout: true ).split('\r\n')[2].trim()
    options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
    println "The last commit was written by ${options.commitAuthor}."
    println "Commit message: ${options.commitMessage}"
    println "Commit SHA: ${options.commitSHA}"
}

def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options.projectBranch, options.projectRepo)
        outputEnvironmentInfo(osName)

        switch(osName) {
            case 'Windows': 
                executeBuildWindows()
                break
            case 'OSX':
                executeBuildOSX()
                break
            default: 
                executeBuildLinux()
        }
       
        stash includes: 'build/**/*', name: "app${osName}"
        stash includes: 'shaders/**/*', name: "app${osName}_shaders"
    } catch (e) {
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts "*.log"
    }                        
}

def executeDeploy(Map options, List platformList, List testResultList)
{
}

def call(String projectBranch = "", 
         String projectRepo = 'git@github.com:Radeon-Pro/RadeonRaysNext.git',
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,NVIDIA_GF1080TI;Ubuntu18;CentOS7',
         String PRJ_NAME="RadeonRaysNext",
         Boolean enableNotifications = true) {

    String PRJ_ROOT="rpr-core"
    
    properties([[$class: 'BuildDiscarderProperty', strategy: 
        [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10']]])
    
    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, null, 
                           [projectBranch:projectBranch,
                            enableNotifications:enableNotifications,
                            executeBuild:true,
                            executeTests:true,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            BUILD_TIMEOUT:'10',
                            TEST_TIMEOUT:'10',
                            projectRepo:projectRepo,
                            slackChannel:"${SLACK_BAIKAL_CHANNEL}",
                            slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
                            slackTocken:"${SLACK_BAIKAL_TOCKEN}",
                            retriesForTestStage:1])
}
