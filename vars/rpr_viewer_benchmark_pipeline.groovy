def executeGenTestRefCommand(String osName, Map options)
{
    executeTestCommand(osName, options)

    try
    {
        //for update existing manifest file
        receiveFiles("${options.REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
    }
    catch(e)
    {
        println("baseline_manifest.json not found")
    }

    dir('scripts')
    {
        switch(osName)
        {
            case 'Windows':
                bat """
                make_results_baseline.bat
                """
                break;
            case 'OSX':
                sh """
                ./make_results_baseline.sh
                """
                break;
            default:
                sh """
                ./make_results_baseline.sh
                """
        }
    }
}

def executeTestCommand(String osName, Map options)
{
    switch(osName)
    {
    case 'Windows':
        dir('scripts')
        {
            bat """
            run.bat ${options.testsPackage} \"${options.tests}\">> ../${options.stageName}.log  2>&1
            """
        }
        break;
    case 'OSX':
        echo "empty"
        break
    default:
        dir('scripts')
        {
            withEnv(["LD_LIBRARY_PATH=../RprViewer/engines/hybrid:\$LD_LIBRARY_PATH"]) {
                sh """
                ./run.sh ${options.testsPackage} \"${options.tests}\">> ../${options.stageName}.log  2>&1
                """
            }
        }
    }
}

def executeTests(String osName, String asicName, Map options)
{
    cleanWs()

    // update assets
    if(isUnix())
    {
        sh """
        ${CIS_TOOLS}/receiveFilesSync.sh ${options.PRJ_ROOT}/${options.PRJ_NAME}/Assets/ ${CIS_TOOLS}/../TestResources/RprViewer
        """
    }
    else
    {
        bat """
        %CIS_TOOLS%\\receiveFilesSync.bat ${options.PRJ_ROOT}/${options.PRJ_NAME}/Assets/ /mnt/c/TestResources/RprViewer
        """
    }

    String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
    String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

    options.REF_PATH_PROFILE = REF_PATH_PROFILE

    try {
        checkOutBranchOrScm(options['testsBranch'], 'https://github.com/luxteam/jobs_test_rprviewer.git')
        outputEnvironmentInfo(osName)

        unstash "app${osName}"

        if(options['updateRefs']) {
            echo "Updating Reference Images"
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        } else {
            echo "Execute Tests"
            try {
                receiveFiles("${REF_PATH_PROFILE}/baseline_manifest.json", './Work/Baseline/')
                options.tests.split(" ").each() {
                    receiveFiles("${REF_PATH_PROFILE}/${it}", './Work/Baseline/')
                }
            } catch (e) {println("Baseline doesn't exist.")}
            executeTestCommand(osName, options)
        }
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        echo "Stashing test results to : ${options.testResultsName}"
        dir('Work')
        {
            stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true
        }
    }
}

def executeBuildWindows(Map options)
{
    bat"""
    cmake . -B build -G "Visual Studio 15 2017" -A x64 >> ${STAGE_NAME}.log 2>&1
    cmake --build build --target RadeonProViewer --config Release >> ${STAGE_NAME}.log 2>&1

    mkdir ${options.DEPLOY_FOLDER}
    xcopy config.json ${options.DEPLOY_FOLDER}
    xcopy README.md ${options.DEPLOY_FOLDER}
    xcopy UIConfig.json ${options.DEPLOY_FOLDER}
    xcopy sky.hdr ${options.DEPLOY_FOLDER}
    xcopy build\\Viewer\\Release\\RadeonProViewer.exe ${options.DEPLOY_FOLDER}\\RadeonProViewer.exe*

    xcopy shaders ${options.DEPLOY_FOLDER}\\shaders /y/i/s
    xcopy support ${options.DEPLOY_FOLDER}\\support /y/i/s

    mkdir ${options.DEPLOY_FOLDER}\\rpml\\lib
    xcopy rpml\\lib\\RadeonML-DirectML.dll ${options.DEPLOY_FOLDER}\\rpml\\lib\\RadeonML-DirectML.dll*
    xcopy rif\\models ${options.DEPLOY_FOLDER}\\rif\\models /s/i/y
    xcopy rif\\lib ${options.DEPLOY_FOLDER}\\rif\\lib /s/i/y
    del /q ${options.DEPLOY_FOLDER}\\rif\\lib\\*.lib
    """

    //temp fix
    bat"""
    xcopy build\\viewer\\engines ${options.DEPLOY_FOLDER}\\engines /s/i/y
    """

    def controlFiles = ['config.json', 'UIConfig.json', 'sky.hdr', 'RadeonProViewer.exe', 'rpml/lib/RadeonML-DirectML.dll']
        controlFiles.each() {
        if (!fileExists("${options.DEPLOY_FOLDER}/${it}")) {
            error "Not found ${it}"
        }
    }
}

def executeBuildOSX(Map options)
{
}

def executeBuildLinux(Map options)
{
    sh """
    mkdir build
    cd build
    cmake .. >> ../${STAGE_NAME}.log 2>&1
    make >> ../${STAGE_NAME}.log 2>&1
    """

    sh """
    mkdir ${options.DEPLOY_FOLDER}
    cp config.json ${options.DEPLOY_FOLDER}
    cp README.md ${options.DEPLOY_FOLDER}
    cp UIConfig.json ${options.DEPLOY_FOLDER}
    cp sky.hdr ${options.DEPLOY_FOLDER}
    cp build/viewer/RadeonProViewer ${options.DEPLOY_FOLDER}/RadeonProViewer

    cp -rf shaders ${options.DEPLOY_FOLDER}/shaders
    cp -rf support ${options.DEPLOY_FOLDER}/support

    mkdir ${options.DEPLOY_FOLDER}/rif
    cp -rf rif/models ${options.DEPLOY_FOLDER}/rif/models
    cp -rf rif/lib ${options.DEPLOY_FOLDER}/rif/lib

    cp -rf build/viewer/engines ${options.DEPLOY_FOLDER}/engines
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

    options['commitSHA'] = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

    if (env.CHANGE_URL) {
        options.testsPackage = "PR"
    }
    else if(env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        options.testsPackage = "master"
    }
    else if(env.BRANCH_NAME) {
        options.testsPackage = "smoke"
    }

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '50']]]);
    }
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

        stash includes: "${options.DEPLOY_FOLDER}/**/*", name: "app${osName}"
        zip archive: true, dir: "${options.DEPLOY_FOLDER}", glob: '', zipFile: "RprViewer_${osName}.zip"
    }
    catch (e) {
        currentBuild.result = "FAILED"
        throw e
    }
    finally {
        archiveArtifacts "${STAGE_NAME}.log"
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    try
    {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'https://github.com/luxteam/jobs_test_rprviewer.git')

            dir("summaryTestResults")
            {
                testResultList.each()
                {
                    dir("$it".replace("testResult-", ""))
                    {
                        try
                        {
                            unstash "$it"
                        }
                        catch(e)
                        {
                            echo "Can't unstash ${it}"
                            println(e.toString())
                            println(e.getMessage())
                        }
                    }
                }
            }
            String branchName = env.BRANCH_NAME ?: options.projectBranch

            try {
                withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"])
                {
                    dir("jobs_launcher") {
                        bat """
                        build_reports.bat ..\\summaryTestResults "${escapeCharsByUnicode('RprViewer')}" ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
                        """
                    }
                }
            } catch(e) {
                println("ERROR during report building")
                println(e.toString())
                println(e.getMessage())
            }

            try
            {
                dir("jobs_launcher") {
                    bat "get_status.bat ..\\summaryTestResults"
                }
            }
            catch(e)
            {
                println("ERROR during slack status generation")
                println(e.toString())
                println(e.getMessage())
            }

            try
            {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                if (summaryReport.failed > 0 || summaryReport.error > 0)
                {
                    println("Some tests failed")
                    currentBuild.result="UNSTABLE"
                }
            }
            catch(e)
            {
                println("CAN'T GET TESTS STATUS")
            }

            try
            {
                options.testsStatus = readFile("summaryTestResults/slack_status.json")
            }
            catch(e)
            {
                println(e.toString())
                println(e.getMessage())
                options.testsStatus = ""
            }

            publishHTML([allowMissing: false,
                         alwaysLinkToLastBuild: false,
                         keepAll: true,
                         reportDir: 'summaryTestResults',
                         reportFiles: 'summary_report.html, performance_report.html, compare_report.html, render_temp.html',
                         reportName: 'Test Report',
                         reportTitles: 'Summary Report, Performance Report, Compare Report, Benchmarks Report'])
        }
    }
    catch(e)
    {
        println(e.toString())
    }
}

def call(String projectBranch = "",
         String testsBranch = "master",
         String platforms = 'Windows:AMD_RadeonVII;Ubuntu18:AMD_RadeonVII',
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         String testsPackage = "",
         String tests = "") {

    String PRJ_ROOT='rpr-core'
    String PRJ_NAME='RadeonProViewer'
    String projectRepo='https://github.com/Radeon-Pro/RadeonProViewer.git'

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                           [projectBranch:projectBranch,
                            testsBranch:testsBranch,
                            updateRefs:updateRefs,
                            enableNotifications:enableNotifications,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            projectRepo:projectRepo,
                            BUILDER_TAG:'BuilderS',
                            TESTER_TAG:'RprViewer',
                            executeBuild:true,
                            executeTests:true,
                            DEPLOY_FOLDER:"RprViewer",
                            testsPackage:testsPackage,
                            TEST_TIMEOUT:180,
                            tests:tests])
}
