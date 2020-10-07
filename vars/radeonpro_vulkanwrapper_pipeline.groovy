def executeGenTestRefCommand(String osName, Map options)
{

}

def executeTestCommand(String osName, Map options)
{

}

def executeTests(String osName, String asicName, Map options)
{
    cleanWS(osName)
    String REF_PATH_PROFILE="${options.REF_PATH}/${asicName}-${osName}"
    String JOB_PATH_PROFILE="${options.JOB_PATH}/${asicName}-${osName}"

    try {
        //checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        outputEnvironmentInfo(osName)
        unstash "app${osName}"

        if(options['updateRefs']) {
            echo "Updating Reference Images"
            executeGenTestRefCommand(osName, options)

        } else {
            echo "Execute Tests"

            executeTestCommand(osName, options)
        }
    }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        throw e
    }
    finally {
        archiveArtifacts "*.log"
        cleanWS(osName)
    }
}

def executeBuildWindows(Map options)
{

    Boolean failure = false
    def configurations = ['DXR_ON':'-DVW_ENABLE_DXR=ON -DVW_ENABLE_DXR_SUPPORT=ON',
                            'DXR_OFF':'-DVW_ENABLE_DXR=ON -DVW_ENABLE_DXR_SUPPORT=OFF',
                            'RRNEXT_OFF':'-DVW_ENABLE_RRNEXT=OFF',
                            'KHR_ON': '-DVW_ENABLE_VK_INLINE_RAYTRACING_SUPPORT=ON']

    configurations.each() {
        KEY, VALUE ->

        checkOutBranchOrScm(options['projectBranch'], options['projectRepo'])
        try {
            bat """
            mkdir build

            cd build
            cmake ${options['cmakeKeys']} ${VALUE} -G "Visual Studio 15 2017 Win64" .. >> ..\\${STAGE_NAME}.${KEY}.log 2>&1
            cmake --build . --config Release >> ..\\${STAGE_NAME}.${KEY}.log 2>&1
            """

            if (KEY == "RRNEXT_OFF") {
                bat """
                cd build
                cmake --build . --config Debug >> ..\\${STAGE_NAME}.${KEY}.log 2>&1
                """

                // Release Package
                bat """
                cd build
                mkdir publish-archive-norrn
                xcopy ..\\include publish-archive-norrn\\inc /s/y/i
                xcopy ..\\math publish-archive-norrn\\inc\\math /s/y/i

                xcopy Release\\VidWrappers.lib publish-archive-norrn\\lib\\VidWrappers.lib*
                xcopy external\\glslang\\glslang\\Release\\glslang.lib publish-archive-norrn\\lib\\glslang.lib*
                xcopy external\\glslang\\hlsl\\Release\\HLSL.lib publish-archive-norrn\\lib\\HLSL.lib*
                xcopy external\\glslang\\OGLCompilersDLL\\Release\\OGLCompiler.lib publish-archive-norrn\\lib\\OGLCompiler.lib*
                xcopy external\\glslang\\SPIRV\\Release\\SPIRV.lib publish-archive-norrn\\lib\\SPIRV.lib*
                xcopy external\\glslang\\SPIRV\\Release\\SPVRemapper.lib publish-archive-norrn\\lib\\SPVRemapper.lib*
                xcopy external\\spirv-cross\\Release\\SpirvCross.lib publish-archive-norrn\\lib\\SpirvCross.lib*
                xcopy external\\glslang\\glslang\\OSDependent\\Windows\\Release\\OSDependent.lib publish-archive-norrn\\lib\\OSDependent.lib*
                """

                // Debug Package
                bat """
                cd build
                xcopy Debug\\VidWrappers.lib publish-archive-norrn\\libd\\VidWrappers.lib*
                xcopy external\\glslang\\glslang\\Debug\\glslangd.lib publish-archive-norrn\\libd\\glslangd.lib*
                xcopy external\\glslang\\hlsl\\Debug\\HLSLd.lib publish-archive-norrn\\libd\\HLSLd.lib*
                xcopy external\\glslang\\OGLCompilersDLL\\Debug\\OGLCompilerd.lib publish-archive-norrn\\libd\\OGLCompilerd.lib*
                xcopy external\\glslang\\SPIRV\\Debug\\SPIRVd.lib publish-archive-norrn\\libd\\SPIRVd.lib*
                xcopy external\\glslang\\SPIRV\\Debug\\SPVRemapperd.lib publish-archive-norrn\\libd\\SPVRemapperd.lib*
                xcopy external\\spirv-cross\\Debug\\SpirvCrossd.lib publish-archive-norrn\\libd\\SpirvCrossd.lib*
                xcopy external\\glslang\\glslang\\OSDependent\\Windows\\Debug\\OSDependentd.lib publish-archive-norrn\\libd\\OSDependentd.lib*
                """

                zip archive: true, dir: 'build/publish-archive-norrn', glob: '', zipFile: "RadeonProVulkanWrapper-Windows-${KEY}.zip"
            }
        } catch(e) {
            println("Error during build ${KEY} configuration, with cmakeKeys: ${VALUE}")
            println(e.toString())
            failure = true
        }
        finally {
            archiveArtifacts "*.log"
        }
    }

    if (failure) {
        currentBuild.result = "FAILED"
        error "error during build"
    }
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

def executeBuildLinux(Map options, String osName="linux")
{

    sh """
    mkdir build
    cd build
    cmake ${options['cmakeKeys']} .. >> ../${STAGE_NAME}.log 2>&1
    make >> ../${STAGE_NAME}.log 2>&1
    """

    if (osName == "Ubuntu18") {
        sh """
        mkdir Build
        cd Build
        cmake ${options['cmakeKeys']} -DVW_ENABLE_RRNEXT=OFF .. >> ../${STAGE_NAME}-RRNEXT_OFF.log 2>&1
        make >> ../${STAGE_NAME}-RRNEXT_OFF.log 2>&1
        """

        // Release Package
        sh """
        cd Build
        mkdir -p publish-archive-norrn/lib
        cp -r ../include publish-archive-norrn/inc
        cp -r ../math publish-archive-norrn/inc/math

        cp libVidWrappers.a publish-archive-norrn/lib/libVidWrappers.a
        cp external/glslang/glslang/libglslang.a publish-archive-norrn/lib/libglslang.a
        cp external/glslang/hlsl/libHLSL.a publish-archive-norrn/lib/libHLSL.a
        cp external/glslang/OGLCompilersDLL/libOGLCompiler.a publish-archive-norrn/lib/libOGLCompiler.a
        cp external/glslang/SPIRV/libSPIRV.a publish-archive-norrn/lib/libSPIRV.a
        cp external/glslang/SPIRV/libSPVRemapper.a publish-archive-norrn/lib/libSPVRemapper.a
        cp external/spirv-cross/libSpirvCross.a publish-archive-norrn/lib/libSpirvCross.a
        cp external/glslang/glslang/OSDependent/Unix/libOSDependent.a publish-archive-norrn/lib/libOSDependent.a
        """

        zip archive: true, dir: 'Build/publish-archive-norrn', glob: '', zipFile: "RadeonProVulkanWrapper-Ubuntu18-RPRNEXT_OFF.zip"
    }
}

def executePreBuild(Map options)
{
    dir('RadeonProVulkanWrapper')
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

        if("${env.BRANCH_NAME}" == "master" || "${options.projectBranch}" == "master")
        {
            try
            {
                bat "tools\\doxygen\\doxygen.exe tools\\doxygen\\Doxyfile >> doxygen_build.log 2>&1"
                archiveArtifacts allowEmptyArchive: true, artifacts: 'doxygen_build.log'
                sendFiles('./docs/', "/${options.PRJ_ROOT}/${options.PRJ_NAME}/doxygen-docs")
            }
            catch(e)
            {
                println("Can't build doxygen documentation")
                println(e.toString())
                currentBuild.result = "UNSTABLE"
            }
        }
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
            executeBuildLinux(options, osName);
        }
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
    cleanWS()
}

def call(String projectBranch = "",
         //TODO: OSX
         String platforms = 'Windows;Ubuntu18;CentOS7',
         String PRJ_ROOT='rpr-core',
         String PRJ_NAME='RadeonProVulkanWrapper',
         String projectRepo='git@github.com:Radeon-Pro/RadeonProVulkanWrapper.git',
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         String cmakeKeys = "-DCMAKE_BUILD_TYPE=Release") {

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, null,
                           [projectBranch:projectBranch,
                            updateRefs:updateRefs,
                            enableNotifications:enableNotifications,
                            PRJ_NAME:PRJ_NAME,
                            PRJ_ROOT:PRJ_ROOT,
                            projectRepo:projectRepo,
                            BUILDER_TAG:'BuilderS',
                            executeBuild:true,
                            executeTests:true,
                            BUILD_TIMEOUT:'40',
                            slackChannel:"${SLACK_BAIKAL_CHANNEL}",
                            slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
                            slackTocken:"${SLACK_BAIKAL_TOCKEN}",
                            cmakeKeys:cmakeKeys])
}
