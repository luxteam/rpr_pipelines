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
    
    // installPlugin(osName, options)
    try {
        // buildRenderCache(osName, "${options.stageName}.log")
        println "no tests functions"
    } catch(e) {
        println(e.toString())
        println("ERROR during building render cache")
    }

    switch(osName)
    {
    case 'Windows':
        dir('scripts')
        {
            //bat """
            //run.bat ${options.renderDevice} ${options.testsPackage} \"${options.tests}\">> ../${options.stageName}.log  2>&1
            //"""
            println "no tests functions"
        }
        break;
    case 'OSX':
        dir('scripts')
        {
            //sh """
            //./run.sh ${options.renderDevice} ${options.testsPackage} \"${options.tests}\" >> ../${options.stageName}.log 2>&1
            //"""
            println "no tests functions"
        }
        break;
    default:
        println "no tests functions"
    }
}


def executeTests(String osName, String asicName, Map options)
{
}


def executeBuildWindows(Map options)
{
    if (options.rebuildUSD){
        bat """
            if exist USDgen rmdir /s/q USDgen
            if exist USDinst rmdir /s/q USDinst
            call "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\VC\\Auxiliary\\Build\\vcvarsall.bat" amd64 >> ${STAGE_NAME}_USD.log 2>&1
            C:\\Python27\\python.exe USD\\build_scripts\\build_usd.py -v --build ${WORKSPACE}/USDgen/build --src ${WORKSPACE}/USDgen/src ${WORKSPACE}/USDinst > USD/${STAGE_NAME}_USD.log 2>&1
        """
    }
    
    dir ("RadeonProRenderUSD") {
        if (options.enableHoudini) {
            bat """
                mkdir build
                set PATH=c:\\python35\\;c:\\python35\\scripts\\;%PATH%;
                python pxr\\imaging\\plugin\\hdRpr\\package\\generatePackage.py -i "." -o "build" >> ..\\${STAGE_NAME}.log 2>&1
            """

        } else {
            bat """
                mkdir build
                set PATH=c:\\python35\\;c:\\python35\\scripts\\;%PATH%;
                python pxr\\imaging\\plugin\\hdRpr\\package\\generatePackage.py -i "." -o "build" --cmake_options "-Dpxr_DIR=USDinst" >> ..\\${STAGE_NAME}.log 2>&1
            """
        } 
    }
}


def executeBuildOSX(Map options) {

    if (options.rebuildUSD) {
        sh """
            if [ -d "./USDgen" ]; then
                rm -fdr ./USDgen
            fi

            if [ -d "./USDinst" ]; then
                rm -fdr ./USDinst
            fi

            mkdir -p USDgen
            mkdir -p USDinst

            python USD/build_scripts/build_usd.py -vvv --build USDgen/build --src USDgen/src USDinst > USD/${STAGE_NAME}_USD.log 2>&1
        """
    }

    dir ("RadeonProRenderUSD") {
        if (options.enableHoudini) {
            sh """
                mkdir build
                export HFS=/Applications/Houdini/Current/Frameworks/Houdini.framework/Versions/Current/Resources
                python3 pxr/imaging/plugin/hdRpr/package/generatePackage.py -i "." -o "build" >> ../${STAGE_NAME}.log 2>&1
            """
        } else {
            sh """
                mkdir build
                python3 pxr/imaging/plugin/hdRpr/package/generatePackage.py -i "." -o "build" --cmake_options "-Dpxr_DIR=USDinst" >> ../${STAGE_NAME}.log 2>&1
            """
        }
    }
}


def executeBuildLinux(Map options) {

    if (options.rebuildUSD) {
        sh """
            if [ -d "./USDgen" ]; then
                rm -fdr ./USDgen
            fi

            if [ -d "./USDinst" ]; then
                rm -fdr ./USDinst
            fi

            mkdir -p USDgen
            mkdir -p USDinst

            python USD/build_scripts/build_usd.py -vvv --build USDgen/build --src USDgen/src USDinst > USD/${STAGE_NAME}_USD.log 2>&1
        """
    }

    dir ("RadeonProRenderUSD") {
        if (options.enableHoudini) {
            sh """
                mkdir build
                export HFS=/opt/hfs18.0.460
                python3 pxr/imaging/plugin/hdRpr/package/generatePackage.py -i "." -o "build" >> ../${STAGE_NAME}.log 2>&1
            """
        } else {
            sh """
                mkdir build
                python3 pxr/imaging/plugin/hdRpr/package/generatePackage.py -i "." -o "build" --cmake_options "-Dpxr_DIR=USDinst" >> ../${STAGE_NAME}.log 2>&1
            """
        }
    }
}


def executeBuildCentOS(Map options) {

    if (options.rebuildUSD) {
        sh """
            if [ -d "./USDgen" ]; then
                rm -fdr ./USDgen
            fi

            if [ -d "./USDinst" ]; then
                rm -fdr ./USDinst
            fi

            mkdir -p USDgen
            mkdir -p USDinst

            python USD/build_scripts/build_usd.py -vvv --build USDgen/build --src USDgen/src USDinst > USD/${STAGE_NAME}_USD.log 2>&1
        """
    }

    dir ("RadeonProRenderUSD") {
        if (options.enableHoudini) {
            sh """
                mkdir build
                export HFS=/opt/hfs18.0.460
                python3 pxr/imaging/plugin/hdRpr/package/generatePackage.py -i "." -o "build" >> ../${STAGE_NAME}.log 2>&1
            """
        } else {
            sh """
                mkdir build
                python3 pxr/imaging/plugin/hdRpr/package/generatePackage.py -i "." -o "build" --cmake_options "-Dpxr_DIR=USDinst" >> ../${STAGE_NAME}.log 2>&1
            """
        }
    }
}


def executeBuild(String osName, Map options) {

    try {
        dir('RadeonProRenderUSD') {
            checkOutBranchOrScm(options['projectBranch'], 'git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderUSD.git')
        }
        if (options.rebuildUSD) {
            dir('USD') {
                checkOutBranchOrScm(options['usdBranch'], 'git@github.com:PixarAnimationStudios/USD.git')
            }
        }

        outputEnvironmentInfo(osName)

        switch(osName) {
            case 'Windows':
                executeBuildWindows(options);
                break;
            case 'OSX':
                executeBuildOSX(options);
                break;
            case 'CentOS':
                executeBuildCentOS(options);
                break;
            case 'CentOS7_6':
                executeBuildCentOS(options);
                break;
            default:
                executeBuildLinux(options);
        }
        archiveArtifacts "RadeonProRenderUSD/build/hdRpr-*.tar.gz"
    }
    catch (e) {
        currentBuild.result = "FAILED"
        if (options.sendToRBS) {
            try {
                options.rbs_prod.setFailureStatus()
                options.rbs_dev.setFailureStatus()
            } catch (err) {
                println(err)
            }
        }
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        if (options.rebuildUSD) {
            archiveArtifacts "USD/*.log"
        }
    }
}

def executePreBuild(Map options) {

    // manual job
    if (options.forceBuild) {
        options.executeBuild = true
        options.executeTests = true
    // auto job
    } else {
        if (env.CHANGE_URL) {
            println "[INFO] Branch was detected as Pull Request"
            options.isPR = true
            options.executeBuild = true
            options.executeTests = true
            options.testsPackage = "PR"
        } else if (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop") {
           println "[INFO] ${env.BRANCH_NAME} branch was detected"
           options.enableNotifications = true
           options.executeBuild = true
           options.executeTests = true
           options.testsPackage = "master"
        } else {
            println "[INFO] ${env.BRANCH_NAME} branch was detected"
            options.testsPackage = "master"
        }
    }

    dir('RadeonProRenderUSD')
    {
        checkOutBranchOrScm(options['projectBranch'], 'git@github.com:GPUOpen-LibrariesAndSDKs/RadeonProRenderUSD.git')

        options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
        options.commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true).split('\r\n')[2].trim()
        options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()

        options.majorVersion = version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_MAJOR_VERSION "', '')
        options.minorVersion = version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_MINOR_VERSION "', '')
        options.patchVersion = version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_PATCH_VERSION "', '')

        println "The last commit was written by ${options.commitAuthor}."
        println "Commit message: ${options.commitMessage}"
        println "Commit SHA: ${options.commitSHA}"

        if (options.projectBranch){
            currentBuild.description = "<b>Project branch:</b> ${options.projectBranch}<br/>"
        } else {
            currentBuild.description = "<b>Project branch:</b> ${env.BRANCH_NAME}<br/>"
        }
        currentBuild.description += "<b>Version:</b> ${options.majorVersion}.${options.minorVersion}.${options.patchVersion}<br/>"
        currentBuild.description += "<b>Commit author:</b> ${options.commitAuthor}<br/>"
        currentBuild.description += "<b>Commit message:</b> ${options.commitMessage}<br/>"

        if(options['incrementVersion'])
        {
            if(env.BRANCH_NAME == "develop" && options.commitAuthor != "radeonprorender")
            {
                println "[INFO] Incrementing version of change made by ${options.commitAuthor}."
                println "[INFO] Current build version: ${options.pluginVersion}"

                new_version = version_inc(options.patchVersion, 1, ' ')
                println "[INFO] New build version: ${new_version}"

                version_write("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_PATCH_VERSION "', new_version, '')
                def updated_version = version_read("${env.WORKSPACE}\\RadeonProRenderUSD\\cmake\\defaults\\Version.cmake", 'set(HD_RPR_PATCH_VERSION "', '')
                println "[INFO] Updated build version: ${updated_version}"

                bat """
                    git add cmake/defaults/Version.cmake
                    git commit -m "buildmaster: version update to ${options.majorVersion}.${options.minorVersion}.${updated_version}"
                    git push origin HEAD:develop
                """

                //get commit's sha which have to be build
                options['projectBranch'] = bat ( script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
                println "[INFO] Project branch hash: ${options.projectBranch}"
            }
        }
    }
}

def executeDeploy(Map options, List platformList, List testResultList)
{
}

def call(String projectBranch = "",
        String usdBranch = "master",
        String testsBranch = "master",
        String platforms = 'Windows;Ubuntu18;OSX;CentOS7_6',
        Boolean updateRefs = false,
        Boolean enableNotifications = false,
        Boolean incrementVersion = true,
        String testsPackage = "",
        String tests = "",
        Boolean forceBuild = false,
        Boolean splitTestsExectuion = false,
        Boolean enableHoudini = true,
        Boolean rebuildUSD = false)
{
    try
    {
        String PRJ_NAME="RadeonProRenderUSD"
        String PRJ_ROOT="rpr-plugins"

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy,
                               [projectBranch:projectBranch,
                                usdBranch:usdBranch,
                                testsBranch:testsBranch,
                                updateRefs:updateRefs,
                                enableNotifications:enableNotifications,
                                PRJ_NAME:PRJ_NAME,
                                PRJ_ROOT:PRJ_ROOT,
                                incrementVersion:incrementVersion,
                                testsPackage:testsPackage,
                                tests:tests,
                                forceBuild:forceBuild,
                                reportName:'Test_20Report',
                                splitTestsExectuion:splitTestsExectuion,
                                TEST_TIMEOUT:30,
                                enableHoudini:enableHoudini,
                                rebuildUSD:rebuildUSD,
                                BUILDER_TAG:'Builder6'
                                ])
    }
    catch(e) {
        currentBuild.result = "FAILED"
        println(e.toString());
        println(e.getMessage());
        throw e
    }
}
