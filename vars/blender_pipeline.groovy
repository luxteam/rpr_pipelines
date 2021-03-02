import groovy.transform.Field
import groovy.json.JsonOutput
import utils
import net.sf.json.JSON
import net.sf.json.JSONSerializer
import net.sf.json.JsonConfig
import TestsExecutionType


def executeTestCommand(String osName, String asicName, Map options)
{
}


def executeTests(String osName, String asicName, Map options)
{
}


def executeBuildWindows(String osName, Map options)
{
    dir("lib\\win64_vc15"){
        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_SVN_REPO) {
            checkout([$class: 'SubversionSCM', additionalCredentials: [], excludedCommitMessages: '', excludedRegions: '', excludedRevprop: '', 
                excludedUsers: '', filterChangelog: false, ignoreDirPropChanges: false, includedRegions: '', 
                locations: [[cancelProcessOnExternalsFail: true, credentialsId: '', depthOption: 'infinity', 
                ignoreExternalsOption: true, local: '.', remote: 'https://svn.blender.org/svnroot/bf-blender/trunk/lib/win64_vc15']], 
                quietOperation: true, workspaceUpdater: [$class: 'UpdateUpdater']])
        }
    }

    dir("lib\\tests"){
        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_SVN_REPO) {
            checkout([$class: 'SubversionSCM', additionalCredentials: [], excludedCommitMessages: '', excludedRegions: '', excludedRevprop: '', 
                excludedUsers: '', filterChangelog: false, ignoreDirPropChanges: false, includedRegions: '', 
                locations: [[cancelProcessOnExternalsFail: true, credentialsId: '', depthOption: 'infinity', 
                ignoreExternalsOption: true, local: '.', remote: 'https://svn.blender.org/svnroot/bf-blender/trunk/lib/tests']], 
                quietOperation: true, workspaceUpdater: [$class: 'UpdateUpdater']])
        }
    }

    dir("blender"){
        bat """
            set msbuild="C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\MSBuild\\15.0\\Bin\\MSBuild.exe" >> ${STAGE_NAME}.log 2>&1

            mkdir build_windows
            cd build_windows

            cmake -G "Visual Studio 15 2017 Win64" -DCYCLES_TEST_DEVICES=OPENCL .. >> ..\\${STAGE_NAME}.log 2>&1
            %msbuild% INSTALL.vcxproj /property:Configuration=Release /p:platform=x64 >> ..\\${STAGE_NAME}.log 2>&1
        """
    }

    dir("blender\\build_windows"){
        try {
            bat """
                ctest -C Release -R opencl >> ..\\${STAGE_NAME}.test.log 2>&1
            """
        } catch (e) {
            currentBuild.result = "UNSTABLE"
        } finally {
            archiveArtifacts artifacts: "build_windows\\tests\\**\\*.*", allowEmptyArchive: true
            utils.publishReport(this, "${BUILD_URL}", "tests", "report.html", \
                "Blender Report", "Test Report")
        }
    }
}


def executeBuildOSX(Map options)
{
}


def executeBuildLinux(String osName, Map options)
{
}


def executeBuild(String osName, Map options)
{
    try {

        dir("blender") {
            withNotifications(title: osName, options: options, configuration: NotificationConfiguration.DOWNLOAD_SOURCE_CODE_REPO) {
                checkOutBranchOrScm(options["projectBranch"], options.projectRepo)
            }
        }

        outputEnvironmentInfo(osName, "blender/${STAGE_NAME}")

        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
            switch(osName) {
                case "Windows":
                    executeBuildWindows(osName, options);
                    break
                default:
                    println("Not supported")
            }
        }
    } catch (e) {
        throw e
    }
    finally {
        archiveArtifacts artifacts: "blender\\*.log", allowEmptyArchive: true
    }
}


def executePreBuild(Map options)
{
    dir("blender") {
        checkOutBranchOrScm(options.projectBranch, options.projectRepo, true)
    }

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

}


def executeDeploy(Map options, List platformList, List testResultList)
{
}



def call(String projectBranch = "master",
    String testsBranch = "master",
    String platforms = "Windows",
    String updateRefs = 'No'
    )
{
    ProblemMessageManager problemMessageManager = new ProblemMessageManager(this, currentBuild)
    Map options = [:]
    options["stage"] = "Init"
    options["problemMessageManager"] = problemMessageManager
 
    String PRJ_NAME="Blender"
    String PRJ_ROOT="rpr-plugins"

    try {
        withNotifications(options: options, configuration: NotificationConfiguration.INITIALIZATION) {
            println "Platforms: ${platforms}"

            options << [projectRepo:"git@github.com:Radeon-Pro/blender",
                        projectBranch:projectBranch,
                        testsBranch:testsBranch,
                        updateRefs:updateRefs,
                        BUILDER_TAG:'PC-FACTORY-HAMBURG-WIN10',
                        BUILD_TIMEOUT:1440,
                        PRJ_NAME:PRJ_NAME,
                        PRJ_ROOT:PRJ_ROOT,
                        problemMessageManager: problemMessageManager,
                        platforms:platforms,
                        executeBuild:true,
                        executeTests:true,
                        ]
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy, options)
    } catch(e) {
        currentBuild.result = "FAILURE"
        println(e.toString());
        println(e.getMessage());
        throw e
    } finally {
        problemMessageManager.publishMessages()
    }

}
