def executeGenTestRefCommand(String osName, Map options)
{
}

def executeTestCommand(String osName, Map options)
{
}

def executeTests(String osName, String asicName, Map options)
{   
}

def executeBuildWindows(Map options)
{
    Boolean failure = false
    def configurations = ['DXR_ON':'-DVW_ENABLE_DXR=ON -DVW_ENABLE_DXR_SUPPORT=ON',
                            'DXR_OFF':'-DVW_ENABLE_DXR=ON -DVW_ENABLE_DXR_SUPPORT=OFF',
                            'RRNEXT_OFF':'-DVW_ENABLE_RRNEXT=OFF',
                            'KHR_ON': '-DVW_ENABLE_RRNEXT=OFF -DVW_ENABLE_VK_INLINE_RAYTRACING_SUPPORT=ON']

    configurations.each() { KEY, VALUE ->
        try {

            checkOutBranchOrScm(options.projectBranch, "git@github.com:Radeon-Pro/RadeonProVulkanWrapper.git")

            if (KEY == "KHR_ON"){
                bat """
                    set VK_SDK_PATH=C:/VulkanSDK/1.2.148.5
                    set VULKAN_SDK=C:/VulkanSDK/1.2.148.5

                    mkdir build
                    cd build
                    cmake ${options['cmakeKeys']} ${VALUE} -G "Visual Studio 15 2017 Win64" .. >> ..\\${STAGE_NAME}.${KEY}.log 2>&1
                    cmake --build . --config Release >> ..\\${STAGE_NAME}.${KEY}.log 2>&1
                """
            } else {
               bat """
                    mkdir build
                    cd build
                    cmake ${options['cmakeKeys']} ${VALUE} -G "Visual Studio 15 2017 Win64" .. >> ..\\${STAGE_NAME}.${KEY}.log 2>&1
                    cmake --build . --config Release >> ..\\${STAGE_NAME}.${KEY}.log 2>&1
                """ 
            }
            
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
            if (KEY != "KHR_ON") {
                failure = true
            } 
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
        make -j 4 >> ../${STAGE_NAME}.log 2>&1
    """
}


def executeBuildLinux(Map options, String osName="linux")
{
    Boolean failure = false

    try {
       sh """
            mkdir build
            cd build
            cmake ${options['cmakeKeys']} .. >> ../${STAGE_NAME}.log 2>&1
            make -j 8 >> ../${STAGE_NAME}.log 2>&1
        """  
    } catch(e) {
        println("Error during build on ${osName}")
        println(e.toString())
        failure = true
    } finally {
        archiveArtifacts "*.log"
    }
   
    if (osName == "Ubuntu18") {

        checkOutBranchOrScm(options.projectBranch, "git@github.com:Radeon-Pro/RadeonProVulkanWrapper.git")

        sh """
            mkdir build
            cd build
            cmake ${options['cmakeKeys']} -DVW_ENABLE_RRNEXT=OFF .. >> ../${STAGE_NAME}-RRNEXT_OFF.log 2>&1
            make -j 8 >> ../${STAGE_NAME}-RRNEXT_OFF.log 2>&1
        """

        // Release Package
        sh """
            cd build
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

        zip archive: true, dir: 'build/publish-archive-norrn', glob: '', zipFile: "RadeonProVulkanWrapper-Ubuntu18-RPRNEXT_OFF.zip"
   }

   if (failure) {
        currentBuild.result = "FAILED"
        error "error during build"
    }
}


def executeBuild(String osName, Map options)
{
    try {
        checkOutBranchOrScm(options.projectBranch, "git@github.com:Radeon-Pro/RadeonProVulkanWrapper.git")
        outputEnvironmentInfo(osName)

        switch(osName) {
            case 'Windows':
                executeBuildWindows(options)
                break
            case 'OSX':
                executeBuildOSX(options)
                break
            default:
                executeBuildLinux(options, osName)
        }
    } catch (e) {
        currentBuild.result = "FAILED"
        throw e
    } finally {
        archiveArtifacts "*.log"
    }
}



def executePreBuild(Map options)
{
    dir('RadeonProVulkanWrapper') {
        checkOutBranchOrScm(options.projectBranch, "git@github.com:Radeon-Pro/RadeonProVulkanWrapper.git")

        options.commitAuthor = bat (script: "git show -s --format=%%an HEAD ",returnStdout: true).split('\r\n')[2].trim()
        commitMessage = bat (script: "git log --format=%%B -n 1", returnStdout: true)
        options.commitSHA = bat (script: "git log --format=%%H -1 ", returnStdout: true).split('\r\n')[2].trim()
        println "The last commit was written by ${options.commitAuthor}."
        println "Commit message: ${commitMessage}"
        println "Commit SHA: ${options.commitSHA}"

        if (env.BRANCH_NAME == "master" || options.projectBranch == "master") {
            try {
                bat "tools\\doxygen\\doxygen.exe tools\\doxygen\\Doxyfile >> doxygen_build.log 2>&1"
                archiveArtifacts allowEmptyArchive: true, artifacts: 'doxygen_build.log'
                sendFiles('./docs/', "/volume1/CIS/doxygen-docs")
            } catch(e) {
                println("Can't build doxygen documentation")
                println(e.toString())
                currentBuild.result = "UNSTABLE"
            }
        }
    }
}


def executeDeploy(Map options, List platformList, List testResultList)
{
}


def call(String projectBranch = "",
         String platforms = 'Windows;Ubuntu18;CentOS7',
         Boolean updateRefs = false,
         Boolean enableNotifications = true,
         String cmakeKeys = "-DCMAKE_BUILD_TYPE=Release") {

    multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, null, null,
                           [projectBranch:projectBranch,
                            updateRefs:updateRefs,
                            enableNotifications:enableNotifications,
                            PRJ_NAME:'RadeonProVulkanWrapper',
                            PRJ_ROOT:'rpr-core',
                            executeBuild:true,
                            executeTests:false,
                            BUILD_TIMEOUT:'40',
                            slackChannel:"${SLACK_BAIKAL_CHANNEL}",
                            slackBaseUrl:"${SLACK_BAIKAL_BASE_URL}",
                            slackTocken:"${SLACK_BAIKAL_TOCKEN}",
                            cmakeKeys:cmakeKeys])
}
