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

    dir("blender"){
        bat """
            make >> ../${STAGE_NAME}.log 2>&1
        """
    }

    dir("build_windows_x64_vc15_Release"){
        try {
            bat """
                ctest -C Release >> ../${STAGE_NAME}.test.log 2>&1
            """
        } catch (e) {
            currentBuild.result = "UNSTABLE"
        } finally {
            archiveArtifacts artifacts: "tests/**/*.*", allowEmptyArchive: true
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
                checkOutBranchOrScm(options["projectBranch"], "git@github.com:blender/blender.git")
            }
        }

        outputEnvironmentInfo(osName)

        withNotifications(title: osName, options: options, configuration: NotificationConfiguration.BUILD_SOURCE_CODE) {
            switch(osName) {
                case "Windows":
                    executeBuildWindows(osName, options);
                    break;
                default:
                    println("Not supported")
            }
        }
    } catch (e) {
        throw e
    }
    finally {
        archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
    }
}


def executePreBuild(Map options)
{

    checkOutBranchOrScm(options.projectBranch, "git@github.com:blender/blender.git", true)

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

    if (env.BRANCH_NAME && env.BRANCH_NAME == "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10']]]);
    } else if (env.BRANCH_NAME && env.BRANCH_NAME != "master") {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '3']]]);
    } else {
        properties([[$class: 'BuildDiscarderProperty', strategy:
                         [$class: 'LogRotator', artifactDaysToKeepStr: '',
                          artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10']]]);
    }

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

            options << [projectBranch:projectBranch,
                        testsBranch:testsBranch,
                        updateRefs:updateRefs,
                        BUILDER_TAG:'PC-FACTORY-HAMBURG-WIN10',
                        PRJ_NAME:PRJ_NAME,
                        PRJ_ROOT:PRJ_ROOT,
                        problemMessageManager: problemMessageManager,
                        platforms:platforms,
                        executeBuild:true,
                        executeTests:true,
                        ]
        }

        multiplatform_pipeline(platforms, this.&executePreBuild, this.&executeBuild, this.&executeTests, this.&executeDeploy, options)
    }
    catch(e)
    {
        currentBuild.result = "FAILURE"
        println(e.toString());
        println(e.getMessage());

        throw e
    }
    finally
    {
        problemMessageManager.publishMessages()
    }

}