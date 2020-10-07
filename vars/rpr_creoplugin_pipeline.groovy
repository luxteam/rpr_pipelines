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
                echo 'sample image' > ./ReferenceImages/sample_image.txt
                """
                break;
            default:
                sh """
                ./make_results_baseline.sh
                """
        }
    }
}

def installPlugin(String osName, Map options)
{
    switch(osName)
    {
    case 'Windows':
        // uninstall plugin
        try
        {
            powershell"""
            \$uninstall = Get-WmiObject -Class Win32_Product -Filter "Name = 'Radeon ProRender for Autodesk Maya®'"
            if (\$uninstall) {
            Write "Uninstalling..."
            \$uninstall = \$uninstall.IdentifyingNumber
            start-process "msiexec.exe" -arg "/X \$uninstall /qn /quiet /L+ie ${STAGE_NAME}.uninstall.log /norestart" -Wait
            }else{
            Write "Plugin not found"}
            """
        }
        catch(e)
        {
            println("Error while deinstall plugin")
            println(e.toString())
            println(e.getMessage())
        }

        // install new plugin
        dir('temp/install_plugin')
        {
            bat """
            IF EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" (
                forfiles /p "${CIS_TOOLS}\\..\\PluginsBinaries" /s /d -2 /c "cmd /c del @file"
                powershell -c "\$folderSize = (Get-ChildItem -Recurse \"${CIS_TOOLS}\\..\\PluginsBinaries\" | Measure-Object -Property Length -Sum).Sum / 1GB; if (\$folderSize -ge 10) {Remove-Item -Recurse -Force \"${CIS_TOOLS}\\..\\PluginsBinaries\";};"
            )
            """

            if(!(fileExists("${CIS_TOOLS}/../PluginsBinaries/${options.pluginWinSha}.msi")))
            {
                unstash 'appWindows'
                bat """
                IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
                rename RadeonProRenderForMaya.msi ${options.pluginWinSha}.msi
                copy ${options.pluginWinSha}.msi "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.msi"
                """
            }
            else
            {
                bat """
                copy "${CIS_TOOLS}\\..\\PluginsBinaries\\${options.pluginWinSha}.msi" ${options.pluginWinSha}.msi
                """
            }

            bat """
            msiexec /i "${options.pluginWinSha}.msi" /quiet /qn PIDKEY=${env.RPR_PLUGIN_KEY} /L+ie ../../${STAGE_NAME}.install.log /norestart
            """
        }

        //temp solution new matlib migration
        try
        {
            try
            {
                powershell"""
                \$uninstall = Get-WmiObject -Class Win32_Product -Filter "Name = 'Radeon ProRender Material Library'"
                if (\$uninstall) {
                Write "Uninstalling..."
                \$uninstall = \$uninstall.IdentifyingNumber
                start-process "msiexec.exe" -arg "/X \$uninstall /qn /quiet /L+ie ${STAGE_NAME}.matlib.uninstall.log /norestart" -Wait
                }else{
                Write "Plugin not found"}
                """
            }
            catch(e)
            {
                println("Error while deinstall plugin")
                println(e.toString())
            }

            receiveFiles("/bin_storage/RadeonProMaterialLibrary.msi", "/mnt/c/TestResources/")
            bat """
            msiexec /i "C:\\TestResources\\RadeonProMaterialLibrary.msi" /quiet /L+ie ${STAGE_NAME}.matlib.install.log /norestart
            """
        }
        catch(e)
        {
            println(e.getMessage())
            println(e.toString())
        }
        break
    default:
        echo "skip"
    }
}

def buildRenderCache(String osName)
{
    switch(osName)
    {
    case 'Windows':
        dir("scripts")
        {
            bat "build_rpr_cache.bat"
        }
        break;
    case 'OSX':
        echo "pass"
        break;
    default:
        echo "pass"
    }
}

def executeTestCommand(String osName, Map options)
{
    if (!options['skipBuild'])
    {
        installPlugin(osName, options)
        buildRenderCache(osName)
    }

    switch(osName)
    {
    case 'Windows':
        dir('scripts')
        {
            //bat"""
            //auto_config.bat >> ../${STAGE_NAME}.log 2>&1
            //"""
            bat """
            run.bat ${options.renderDevice} ${options.testsPackage} \"${options.tests}\">> ../${STAGE_NAME}.log  2>&1
            """
        }
        break;
    case 'OSX':
        sh """
        echo 'sample image' > ./OutputImages/sample_image.txt
        """
        break;
    default:
        sh """
        echo 'sample image' > ./OutputImages/sample_image.txt
        """
    }
}

def executeTests(String osName, String asicName, Map options)
{
    try {
        checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_maya.git')

        // update assets
        if(isUnix())
        {
            sh """
            ${CIS_TOOLS}/receiveFilesSync.sh ${options.PRJ_ROOT}/${options.PRJ_NAME}/MayaAssets/ ${CIS_TOOLS}/../TestResources/MayaAssets
            """
        }
        else
        {
            bat """
            %CIS_TOOLS%\\receiveFilesSync.bat ${options.PRJ_ROOT}/${options.PRJ_NAME}/MayaAssets/ /mnt/c/TestResources/MayaAssets
            """
        }

        String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
        String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

        options.REF_PATH_PROFILE = REF_PATH_PROFILE

        outputEnvironmentInfo(osName, options.stageName)

        if(options['updateRefs'])
        {
            executeGenTestRefCommand(osName, options)
            sendFiles('./Work/Baseline/', REF_PATH_PROFILE)
        }
        else
        {
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
        throw e
    }
    finally
    {
        archiveArtifacts "*.log"
        echo "Stashing test results to : ${options.testResultsName}"
        dir('Work')
        {
            stash includes: '**/*', name: "${options.testResultsName}", allowEmpty: true

            try
            {
                def sessionReport = readJSON file: 'Results/Maya/session_report.json'
                // if none launched tests - mark build failed
                if (sessionReport.summary.total == 0)
                {
                    options.failureMessage = "Noone test was finished for: ${asicName}-${osName}"
                }

                if (options.sendToRBS)
                {
                    writeJSON file: 'temp_machine_info.json', json: sessionReport.machine_info
                    String token = rbs_get_token("https://rbsdbdev.cis.luxoft.com/api/login", "847a5a5d-700d-439b-ace1-518f415eb8d8")
                    String branchTag = env.JOB_NAME == "RadeonProRenderMayaPlugin-WeeklyFull" ? "weekly" : "master"
                    rbs_push_group_results("https://rbsdbdev.cis.luxoft.com/report/group", token, branchTag, "Maya", options)

                    bat "del temp_group_report.json"

                    token = rbs_get_token("https://rbsdb.cis.luxoft.com/api/login", "ddd49290-412d-45c3-9ae4-65dba573b4c0")
                    rbs_push_group_results("https://rbsdb.cis.luxoft.com/report/group", token, branchTag, "Maya", options)
                }
            }
            catch (e)
            {
                println(e.toString())
                println(e.getMessage())
            }
        }
    }
}

def executeBuildWindows(Map options)
{

    withEnv(["CREO_INSTALL_DIR=C:\\Program Files\\PTC\\Creo 4.0\\M030\\",
            "PROTOOL_SRC=C:\\Program Files\\PTC\\Creo 4.0\\M030\\Common Files\\protoolkit\\includes\\",
            "PROTOOL_OBJ=C:\\Program Files\\PTC\\Creo 4.0\\M030\\Common Files\\protoolkit\\x86e_win64\\obj\\"])
    {
        bat """
        cd RadeonProRenderCreoPlugin
        call install_prerequisites.bat >> ..\\${STAGE_NAME}.log 2>&1

        pushd c:\\local\\boost_1_70_0\\
        call bootstrap.bat
        b2 -j16 toolset=msvc-14.1 address-model=64 architecture=x86 link=static threading=multi runtime-link=static --build-type=complete stage --stagedir=stage/x64
        b2 -j16 toolset=msvc-14.1 address-model=64 architecture=x86 link=static threading=multi runtime-link=static --build-type=complete install --stagedir=stage/x64 --prefix=${env.WORKSPACE}\\RadeonProRenderCreoPlugin\\ThirdParty\\boost
        popd

        rmdir /S /Q build
        mkdir build
        cd build
        cmake -G "Visual Studio 15 2017 Win64" --build . --config Release -DBOOST_ROOT="c:/local/boost_1_70_0" .. >> ..\\..\\${STAGE_NAME}.log 2>&1
        cd ..

        set msbuild=\"C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe\"
        %msbuild% build/rpr_creo.sln /property:Configuration=Release /property:Platform=x64 >> ..\\..\\${STAGE_NAME}.log 2>&1

        pushd installer
        IF NOT EXIST \"%ISCCL%\" set ISCCL=%CD%\\Inno Setup 5\\iscc.exe
        "%ISCCL%" \"%CD%\\FireRender.iss\" >> ..\\..\\..\\${STAGE_NAME}.log 2>&1
        """
    }

    dir('RadeonProRenderCreoPlugin/build/Installer')
    {
        String branch_postfix = ""
        if(env.BRANCH_NAME && BRANCH_NAME != "master")
        {
            branch_postfix = BRANCH_NAME.replace('/', '-').trim()
            echo "Detected as autobuild, postfix: ${branch_postfix}"
        }
        if(env.Branch && Branch != "master")
        {
            branch_postfix = Branch.replace('/', '-').trim()
            echo "Detected as manualbuild, postfix: ${branch_postfix}"
        }
        if(branch_postfix)
        {
            bat """
            rename \"FireRender*exe\" \"*.(${branch_postfix}).exe\"
            """
            echo "Rename build"
        }

        archiveArtifacts "FireRender*.exe"

        bat """
        for /r %%i in (\"FireRender*.exe\") do copy \"%%i\" RadeonProRenderForCreo.exe
        """

        stash includes: 'RadeonProRenderForCreo.exe', name: 'appWindows'
        options.pluginWinSha = sha1 'RadeonProRenderForCreo.exe'
    }
}

def executeBuildOSX(Map options)
{

}

def executeBuildLinux(Map options)
{

}

def executeBuild(String osName, Map options)
{
    try {
        dir('RadeonProRenderCreoPlugin')
        {
            checkOutBranchOrScm(options['projectBranch'], 'git@github.com:Radeon-Pro/RadeonProRenderCreoPlugin.git')
        }

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
    }
    catch (e) {
        if (options.sendToRBS)
        {
            String token = rbs_get_token("https://rbsdbdev.cis.luxoft.com/api/login", "847a5a5d-700d-439b-ace1-518f415eb8d8")
            String branchTag = env.JOB_NAME == "RadeonProRenderCreoPlugin-WeeklyFull" ? "weekly" : "master"
            rbs_push_builder_failure("https://rbsdbdev.cis.luxoft.com/report/jobStatus", token, branchTag, "Creo")

            token = rbs_get_token("https://rbsdb.cis.luxoft.com/api/login", "ddd49290-412d-45c3-9ae4-65dba573b4c0")
            rbs_push_builder_failure("https://rbsdb.cis.luxoft.com/report/jobStatus", token, branchTag, "Creo")
        }
        throw e
    }
    finally {
        archiveArtifacts "*.log"
    }
}

def executePreBuild(Map options)
{
    currentBuild.description = ""
    ['projectBranch', 'thirdpartyBranch', 'packageBranch'].each
    {
        if(options[it] != 'master' && options[it] != "")
        {
            currentBuild.description += "<b>${it}:</b> ${options[it]}<br/>"
        }
    }

    dir('RadeonProRenderCreoPlugin')
    {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:Radeon-Pro/RadeonProRenderCreoPlugin.git')

        AUTHOR_NAME = bat (
                script: "git show -s --format=%%an HEAD ",
                returnStdout: true
                ).split('\r\n')[2].trim()

        echo "The last commit was written by ${AUTHOR_NAME}."
        options.AUTHOR_NAME = AUTHOR_NAME

        commitMessage = bat ( script: "git log --format=%%B -n 1", returnStdout: true )
        echo "Commit message: ${commitMessage}"
        options.commitMessage = commitMessage.split('\r\n')[2].trim()

        options['commitSHA'] = bat(script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

        if(options['incrementVersion'])
        {
            // TODO: implement auto-increment
            if("${BRANCH_NAME}" == "master" && "${AUTHOR_NAME}" != "radeonprorender")
            {
                options.testsPackage = "master"
                echo "Incrementing version of change made by ${AUTHOR_NAME}."
                String currentversion=version_read('version.h', '#define PLUGIN_VERSION')
                echo "currentversion ${currentversion}"

                new_version=version_inc(currentversion, 3)
                echo "new_version ${new_version}"

                version_write('version.h', '#define PLUGIN_VERSION', new_version)

                String updatedversion=version_read('version.h', '#define PLUGIN_VERSION')
                echo "updatedversion ${updatedversion}"

                bat """
                    git add version.h
                    git commit -m "buildmaster: version update to ${updatedversion}"
                    git push origin HEAD:master
                   """

                //get commit's sha which have to be build
                options['projectBranch'] = bat ( script: "git log --format=%%H -1 ",
                                    returnStdout: true
                                    ).split('\r\n')[2].trim()

                options['executeBuild'] = true
                options['executeTests'] = true
            }
            else
            {
                options.testsPackage = "smoke"
                if(commitMessage.contains("CIS:BUILD"))
                {
                    options['executeBuild'] = true
                }

                if(commitMessage.contains("CIS:TESTS"))
                {
                    options['executeBuild'] = true
                    options['executeTests'] = true
                }

                if (env.CHANGE_URL)
                {
                    echo "branch was detected as Pull Request"
                    options['executeBuild'] = true
                    options['executeTests'] = true
                    options.testsPackage = "PR"
                }

                if("${BRANCH_NAME}" == "master") {
                   echo "rebuild master"
                   options['executeBuild'] = true
                   options['executeTests'] = true
                   options.testsPackage = "master"
                }

            }
        }
        options.pluginVersion = "stub"
        // options.pluginVersion = version_read('version.h', '#define PLUGIN_VERSION')
    }
    if(options['forceBuild'])
    {
        options['executeBuild'] = true
        options['executeTests'] = true
    }

    currentBuild.description += "<b>Version:</b> ${options.pluginVersion}<br/>"
    if(!env.CHANGE_URL)
    {
        currentBuild.description += "<b>Commit author:</b> ${options.AUTHOR_NAME}<br/>"
        currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"
    }

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '25']]]);
    } else if (env.BRANCH_NAME && BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else if (env.JOB_NAME == "RadeonProRenderCreoPlugin-WeeklyFull") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '60']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20']]]);
    }

    if(options.splitTestsExectuion)
    {
        def tests = []
        if(options.testsPackage != "none")
        {
            dir('jobs_test_creo')
            {
                checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_creo.git')
                // json means custom test suite. Split doesn't supported
                if(options.testsPackage.endsWith('.json'))
                {
                    options.testsList = ['']
                }
                // options.splitTestsExecution = false
                String tempTests = readFile("jobs/${options.testsPackage}")
                tempTests.split("\n").each {
                    // TODO: fix: duck tape - error with line ending
                    tests << "${it.replaceAll("[^a-zA-Z0-9_]+","")}"
                }
                options.testsList = tests
                options.testsPackage = "none"
            }
        }
        else
        {
            options.tests.split(" ").each()
            {
                tests << "${it}"
            }
            options.testsList = tests
        }

        // for autojobs - push only weekly job and master branch
        if (env.BRANCH_NAME && env.BRANCH_NAME == "master" || env.JOB_NAME == "RadeonProRenderCreoPlugin-WeeklyFull")
        {
            options.sendToRBS = false
        }
    }
    else
    {
        options.testsList = ['']
    }

    if (options.sendToRBS)
    {
        String token = rbs_get_token("https://rbsdbdev.cis.luxoft.com/api/login", "847a5a5d-700d-439b-ace1-518f415eb8d8")
        String branchTag = env.JOB_NAME == "RadeonProRenderCreoPlugin-WeeklyFull" ? "weekly" : "master"

        rbs_push_job_start("https://rbsdbdev.cis.luxoft.com/report/job", token, branchTag, "Creo", options)

        token = rbs_get_token("https://rbsdb.cis.luxoft.com/api/login", "ddd49290-412d-45c3-9ae4-65dba573b4c0")
        rbs_push_job_start("https://rbsdb.cis.luxoft.com/report/job", token, branchTag, "Creo", options)
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
    try {
        if(options['executeTests'] && testResultList)
        {
            checkOutBranchOrScm(options['testsBranch'], 'git@github.com:luxteam/jobs_test_maya.git')

            dir("summaryTestResults")
            {
                testResultList.each()
                {
                    dir("$it".replace("testResult-", ""))
                    {
                        try
                        {
                            unstash "$it"
                        }catch(e)
                        {
                            echo "Can't unstash ${it}"
                            println(e.toString());
                            println(e.getMessage());
                        }

                    }
                }
            }

            dir("jobs_launcher")
            {
                String branchName = env.BRANCH_NAME ?: options.projectBranch

                try
                {
                    withEnv(["JOB_STARTED_TIME=${options.JOB_STARTED_TIME}"])
                    {
                        bat """
                        build_reports.bat ..\\summaryTestResults "${escapeCharsByUnicode('Maya 2017')}" ${options.commitSHA} ${branchName} \"${escapeCharsByUnicode(options.commitMessage)}\"
                        """
                    }
                } catch(e) {
                    println("ERROR during report building")
                    println(e.toString())
                    println(e.getMessage())
                }

                try
                {
                    bat "get_status.bat ..\\summaryTestResults"
                }
                catch(e)
                {
                    println("ERROR during slack status generation")
                    println(e.toString())
                    println(e.getMessage())
                }
            }

            try
            {
                def summaryReport = readJSON file: 'summaryTestResults/summary_status.json'
                if (summaryReport.error > 0) {
                    println("[INFO] Some tests marked as error. Build result = FAILURE.")
                    currentBuild.result = "FAILURE"
                }
                else if (summaryReport.failed > 0) {
                    println("[INFO] Some tests marked as failed. Build result = UNSTABLE.")
                    currentBuild.result = "UNSTABLE"
                }
            }
            catch(e)
            {
                println(e.toString())
                println(e.getMessage())
                println("CAN'T GET TESTS STATUS")
                currentBuild.result = "UNSTABLE"
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

            utils.publishReport(this, "${BUILD_URL}", "summaryTestResults", "summary_report.html, performance_report.html, compare_report.html", \
                "Test Report", "Summary Report, Performance Report, Compare Report")

            if (options.sendToRBS)
            {
                String token = rbs_get_token("https://rbsdbdev.cis.luxoft.com/api/login", "847a5a5d-700d-439b-ace1-518f415eb8d8")
                String branchTag = env.JOB_NAME == "RadeonProRenderMayaPlugin-WeeklyFull" ? "weekly" : "master"
                rbs_push_job_status("https://rbsdbdev.cis.luxoft.com/report/end", token, branchTag, "Maya")

                token = rbs_get_token("https://rbsdb.cis.luxoft.com/api/login", "ddd49290-412d-45c3-9ae4-65dba573b4c0")
                rbs_push_job_status("https://rbsdb.cis.luxoft.com/report/end", token, branchTag, "Maya")
            }
        }
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally
    {}
}


def call(String projectBranch = "", String thirdpartyBranch = "master",
         String packageBranch = "master", String testsBranch = "master",
         String platforms = 'Windows',
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         Boolean incrementVersion = false,
         Boolean skipBuild = false,
         String renderDevice = "gpu",
         String testsPackage = "",
         String tests = "",
         Boolean forceBuild = false,
         Boolean splitTestsExectuion = false,
         Boolean sendToRBS = false)
{
    try
    {
        String PRJ_NAME="RadeonProRenderCreoPlugin"
        String PRJ_ROOT="rpr-plugins"

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

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                thirdpartyBranch:thirdpartyBranch,
                                packageBranch:packageBranch,
                                testsBranch:testsBranch,
                                updateRefs:updateRefs,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                incrementVersion:incrementVersion,
                                skipBuild:skipBuild,
                                renderDevice:renderDevice,
                                testsPackage:testsPackage,
                                tests:tests,
                                executeBuild:false,
                                executeTests:false,
                                forceBuild:forceBuild,
                                reportName:'Test_20Report',
                                splitTestsExectuion:splitTestsExectuion,
                                sendToRBS:sendToRBS,
                                gpusCount:gpusCount,
                                TEST_TIMEOUT:540,
                                BUILDER_TAG:"Builder && Creo"])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
