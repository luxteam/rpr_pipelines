def executeGenTestRefCommand(String osName, Map options)
{
    if(options.BaikalTest) {
        dir('BaikalTest') {
            switch(osName) {
                case 'Windows':
                    bat """
                    ..\\Build\\bin\\Release\\BaikalTest.exe -genref 1 --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ..\\Baikal${STAGE_NAME}.log 2>&1
                    """
                    break;
                case 'OSX':
                    sh """
                        export LD_LIBRARY_PATH=`pwd`/../Build/bin/:\$LD_LIBRARY_PATH
                        ../Build/bin/BaikalTest -genref 1 --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../Baikal${STAGE_NAME}.log 2>&1
                    """
                    break;
                default:
                    sh """
                        export LD_LIBRARY_PATH=`pwd`/../Build/bin/:\${LD_LIBRARY_PATH}
                        ../Build/bin/BaikalTest -genref 1 --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../Baikal${STAGE_NAME}.log 2>&1
                    """
            }
        }
    }
    if(options.RprTest) {
        dir('RprTest') {
            switch(osName) {
                case 'Windows':
                    bat """
                    ..\\Build\\bin\\Release\\RprTest.exe -genref 1 --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ..\\Rpr${STAGE_NAME}.log 2>&1
                    """
                    break;
                case 'OSX':
                    sh """
                        export LD_LIBRARY_PATH=`pwd`/../Build/bin/:\$LD_LIBRARY_PATH
                        ../Build/bin/RprTest -genref 1 --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../Rpr${STAGE_NAME}.log 2>&1
                    """
                    break;
                default:
                    sh """
                        export LD_LIBRARY_PATH=`pwd`/../Build/bin/:\${LD_LIBRARY_PATH}
                        ../Build/bin/RprTest -genref 1 --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../Rpr${STAGE_NAME}.log 2>&1
                    """
            }
        }
    }
}

def executeTestCommand(String osName, Map options)
{
    if(options.BaikalTest) {
        dir('BaikalTest') {
            switch(osName)
            {
            case 'Windows':
                bat """
                    ..\\Build\\bin\\Release\\BaikalTest.exe --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ..\\Baikal${STAGE_NAME}.log 2>&1
                """
                break;
            case 'OSX':
                sh """
                    export LD_LIBRARY_PATH=`pwd`/../Build/bin/:\$LD_LIBRARY_PATH
                    ../Build/bin/BaikalTest --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../Baikal${STAGE_NAME}.log 2>&1
                """
                break;
            default:
                sh """
                    export LD_LIBRARY_PATH=`pwd`/../Build/bin/:\${LD_LIBRARY_PATH}
                    ../Build/bin/BaikalTest --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../Baikal${STAGE_NAME}.log 2>&1
                """
            }
        }
    }
    if(options.RprTest) {
        dir('RprTest') {
            switch(osName) {
                case 'Windows':
                    bat """
                    ..\\Build\\bin\\Release\\RprTest.exe --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ..\\Rpr${STAGE_NAME}.log 2>&1
                    """
                    break;
                case 'OSX':
                    sh """
                        export LD_LIBRARY_PATH=`pwd`/../Build/bin/:\$LD_LIBRARY_PATH
                        ../Build/bin/RprTest --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../Rpr${STAGE_NAME}.log 2>&1
                    """
                    break;
                default:
                    sh """
                        export LD_LIBRARY_PATH=`pwd`/../Build/bin/:\${LD_LIBRARY_PATH}
                        ../Build/bin/RprTest --gtest_output=xml:../${STAGE_NAME}.gtest.xml >> ../Rpr${STAGE_NAME}.log 2>&1
                    """
            }
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    //String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
    String REF_PATH_PROFILE="rpr-core/RadeonProRender-Baikal/ReferenceImages"
    String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

        outputEnvironmentInfo(osName)
        unstash "app${osName}"
        
        if(options['updateRefs']) {
            echo "Updating Reference Images"
            
            timeout(time: 1, unit: 'HOURS')
            {
                executeGenTestRefCommand(osName, options)
            }
            
            if(options.BaikalTest) {
                sendFiles('./BaikalTest/ReferenceImages/*.*', "${REF_PATH_PROFILE}/BaikalTest/${asicName}-${osName}")
            }
            if(options.RprTest) {
                sendFiles('./RprTest/ReferenceImages/*.*', "${REF_PATH_PROFILE}/RprTest/${asicName}-${osName}")
            }
        } else {
            echo "Execute Tests"
            if(options.BaikalTest) {
                receiveFiles("${REF_PATH_PROFILE}/BaikalTest/${asicName}-${osName}/*", './BaikalTest/ReferenceImages/')
                
            }
            if(options.RprTest) {
                receiveFiles("${REF_PATH_PROFILE}/RprTest/${asicName}-${osName}/*", './RprTest/ReferenceImages/')
            }
            timeout(time: 1, unit: 'HOURS')
            {
                executeTestCommand(osName, options)
            }
        }
        
        echo "Stashing test results to : Baikal${options.testResultsName}"
        if(options['updateRefs'])
        {
            dir('BaikalTest/ReferenceImages')
            {
                stash includes: '**/*', name: "Baikal${options.testResultsName}"
            }
        }
        else
        {
            dir('BaikalTest/OutputImages')
            {
                stash includes: '**/*', name: "Baikal${options.testResultsName}"
            }
        }
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        
        dir('BaikalTest')
        {
            sendFiles('./ReferenceImages/*.*', "${options.JOB_PATH}/BaikalTest/${asicName}-${osName}/ReferenceImages")
            sendFiles('./OutputImages/*.*', "${options.JOB_PATH}/BaikalTest/${asicName}-${osName}/OutputImages")
        }
        dir('RprTest')
        {
            sendFiles('./ReferenceImages/*.*', "${options.JOB_PATH}/RprTest/${asicName}-${osName}/ReferenceImages")
            sendFiles('./OutputImages/*.*', "${options.JOB_PATH}/RprTest/${asicName}-${osName}/OutputImages")
        }
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        junit "*.gtest.xml"
    }
}

def executeBuildWindows(Map options)
{
    bat """
    mkdir Build
    cd Build
    cmake ${options['cmakeKeys']} -G "Visual Studio 15 2017 Win64" .. >> ..\\${STAGE_NAME}.log 2>&1
    cmake --build . --config Release >> ..\\${STAGE_NAME}.log 2>&1
    """
}

def executeBuildOSX(Map options)
{
    sh """
    mkdir Build
    cd Build
    cmake ${options['cmakeKeys']} .. >> ../${STAGE_NAME}.log 2>&1
    make >> ../${STAGE_NAME}.log 2>&1
    """
}

def executeBuildLinux(Map options)
{
    sh """
    mkdir Build
    cd Build
    cmake ${options['cmakeKeys']} .. >> ../${STAGE_NAME}.log 2>&1
    make >> ../${STAGE_NAME}.log 2>&1
    """
}

def executePreBuild(Map options)
{
    checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])

    AUTHOR_NAME = bat (
            script: "git show -s --format=%%an HEAD ",
            returnStdout: true
            ).split('\r\n')[2].trim()

    echo "The last commit was written by ${AUTHOR_NAME}."
    options.AUTHOR_NAME = AUTHOR_NAME

    commitMessage = bat ( script: "git log --format=%%B -n 1", returnStdout: true ).split('\r\n')[2].trim()
    echo "Commit message: ${commitMessage}"
    options.commitMessage = commitMessage
}

def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        outputEnvironmentInfo(osName)

        switch(osName)
        {
        case 'Windows': 
            executeBuildWindows(options); 
            break;
        case 'OSX':
            executeBuildOSX(options);
            break;
        default: 
            executeBuildLinux(options);
        }
        
        stash includes: 'Build/bin/**/*', name: "app${osName}"
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
{
    try
    {
        dir("Binaries") {
            platformList.each() {
                dir(it) {
                    try {
                        unstash "app${it}"
                    }
                    catch (e) {
                        println(e.toString())
                    }
                }
            }
        }
        
        archiveArtifacts "Binaries/**/*.*"
    }
    catch (e) {
        currentBuild.result = "FAILED"        
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally {

    }
    
    /*try
    {
        if(testResultList)
        {
            //bat """
            //rmdir /S /Q summaryTestResults
            //"""
            
            dir("summaryTestResults")
            {
                testResultList.each()
                {
                    try {
                        dir("$it".replace("testResult-", "")) {
                            unstash "$it"
                        }
                    }
                    catch(e) {
                        echo "Error while unstash ${it}"
                    }
                }
            }

            dir("summaryTestResults")
            {
                bat """
                C:\\Python35\\python.exe %CIS_TOOLS%\\baikal_html\\main.py --input_path %CD%
                """
            }
            
            if(options['updateRefs'])
            {
                String REF_PATH_PROFILE="rpr-core/RadeonProRender-Baikal/ReferenceImages"
                sendFiles('./summaryTestResults/compare.html', "${REF_PATH_PROFILE}")
            }

            publishHTML([allowMissing: false, 
                         alwaysLinkToLastBuild: false, 
                         keepAll: true, 
                         reportDir: 'summaryTestResults', 
                         reportFiles: 'compare.html', reportName: 'Test Report', reportTitles: 'Summary Report'])
        }
    }
    catch (e) {
        currentBuild.result = "FAILED"        
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally {}*/ 
}

def call(String projectBranch = "", 
         String platforms = 'Windows:AMD_RXVEGA,AMD_WX9100,AMD_WX7100,NVIDIA_GF1080TI;OSX:Intel_Iris,AMD_WX9100;CentOS7',
         String PRJ_ROOT='rpr-core',
         String PRJ_NAME='RadeonProRender-Baikal',
         String projectRepo='git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRender-Baikal.git',
         Boolean updateRefs = false, 
         Boolean enableNotifications = true,
         String cmakeKeys = "-DCMAKE_BUILD_TYPE=Release -DBAIKAL_ENABLE_RPR=ON",
         Boolean BaikalTest = true,
         Boolean RprTest = true) {

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                           [projectBranch:projectBranch,
                            updateRefs:updateRefs, 
                            enableNotifications:enableNotifications,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            projectRepo:projectRepo,
                            BUILDER_TAG:'BuilderS',
                            executeBuild:true,
                            executeTests:true,
                            slackChannel:"${SLACK_BAIKAL_CHANNEL}",
                            slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
                            slackTocken:"${SLACK_BAIKAL_TOCKEN}",
                            cmakeKeys:cmakeKeys,
                            BaikalTest:BaikalTest,
                            RprTest: RprTest,
                            retriesForTestStage:1])
}
