import java.util.concurrent.atomic.AtomicBoolean
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

/**
 * Class which manages status checks of PRs
 */
public class GithubNotificator {

    def context
    String repositoryUrl
    String commitSHA
    GithubApiProvider githubApiProvider
    List buildCases = []
    List testCases = []
    List deployCases = []
    Boolean hasDeployStage
    // this variable is used for prevent multiple closing of status checks in multi thread logic of pipeline
    AtomicBoolean statusesClosed = new AtomicBoolean(false)

    /**
     * Main constructor
     *
     * @param context
     * @param options Options map
     */
    GithubNotificator(def context, Map options) {
        this.context = context
        githubApiProvider = new GithubApiProvider(context)
        // format as a http url
        this.repositoryUrl = options["projectRepo"].replace("git@github.com:", "https://github.com/").replaceAll(".git\$", "")
    }

    /**
     * Initialization of GithubNotificator object
     *
     * @param options Options map
     */
    def init(Map options) {
        // check that it's PR or not
        if (context.env.CHANGE_URL) {
            this.commitSHA = githubApiProvider.getPullRequest(context.env.CHANGE_URL)["head"]["sha"]
        } else {
            this.commitSHA = options["commitSHA"]
        }
        context.println("Github Notificator initialized for commit ${commitSHA} in repo ${repositoryUrl}")
    }

    /**
     * Function for init status check of PreBuild stage
     *
     * @param url Build url
     */
    def initPreBuild(String url) {
        try {
            context.println("[INFO] Started initialization of notification for PreBuild stage")
            String statusTitle = "[PREBUILD] Jenkins build configuration"
            githubApiProvider.createOrUpdateStatusCheck(
                repositoryUrl: repositoryUrl,
                status: "in_progress",
                name: statusTitle,
                head_sha: commitSHA,
                details_url: url,
                output: [
                    title: "Status check for PreBuild stage initialized.",
                    summary: "Use link below to check build details"
                ]
            )
            context.println("[INFO] Finished initialization of notification for PreBuild stage")
        } catch (e) {
            context.println("[ERROR] Failed to notification for PreBuild stage")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    /**
     * Function for init status checks of PR: create checks with pending status for all steps which will be executed
     *
     * @param options Options map
     * @param url Build url
     * @param hasDeployStage Specify that status for deploy stage should be created or not
     */
    def initChecks(Map options, String url, Boolean hasDeployStage = true) {
        try {
            context.println("[INFO] Started initialization of PR notifications")
            this.hasDeployStage = hasDeployStage

            options.platforms.split(';').each() {
                if (it) {
                    List tokens = it.tokenize(':')
                    String osName = tokens.get(0)
                    buildCases << osName

                    String gpuNames = ""
                    if (tokens.size() > 1) {
                        gpuNames = tokens.get(1)
                    }

                    if(gpuNames) {
                        gpuNames.split(',').each() { asicName ->
                            if (options.splitTestsExecution) {
                                options.tests.each() { testName ->
                                    // check that group isn't fully skipped
                                    if (options.skippedTests && options.skippedTests.containsKey(testName) && options.skippedTests[testName].contains("${asicName}-${osName}")) {
                                        return
                                    }
                                    String parsedTestsName = testName
                                    if (testName.contains("~")) {
                                        String[] testsNameParts = parsedTestsName.split("~")
                                        String engine = ""
                                        if (testsNameParts.length == 2 && testsNameParts[1].contains("-")) {
                                            engine = testsNameParts[1].split("-")[1]
                                        }
                                        parsedTestsName = engine ? "${testsNameParts[0]}-${engine}" : "${testsNameParts[0]}"
                                        parsedTestsName = parsedTestsName.replace(".json", "")
                                    }
                                    testCases << "${asicName}-${osName}-${parsedTestsName}"
                                }
                            } else {
                                testCases << "${asicName}-${osName}"
                            }
                        }
                    }
                }
            }

            String statusTitle = ""

            Map paramsBase = [
                repositoryUrl: repositoryUrl,
                status: "queued",
                head_sha: commitSHA,
                details_url: url,
                output: [
                    title: "This stage will be executed later...",
                    summary: "Use link below to check build details"
                ]
            ]

            for (buildCase in buildCases) {
                paramsBase["name"] = "[BUILD] ${buildCase}"
                githubApiProvider.createOrUpdateStatusCheck(paramsBase)
            }

            for (testCase in testCases) {
                paramsBase["name"] = "[TEST] ${testCase}"
                githubApiProvider.createOrUpdateStatusCheck(paramsBase)
            }
            if (hasDeployStage) {
                if (options.enginesNames) {
                    options.enginesNames.each { engine ->
                        String message = "Building test report for {engine} engine"
                        paramsBase["name"] = "[DEPLOY] ${message}"
                        githubApiProvider.createOrUpdateStatusCheck(paramsBase)
                        deployCases << message
                    }
                } else {
                    String message = "Building test report"
                    paramsBase["name"] = "[DEPLOY] ${message}"
                    githubApiProvider.createOrUpdateStatusCheck(paramsBase)
                    deployCases << message
                }
            }
            context.println("[INFO] Finished initialization of PR notifications")
        } catch (e) {
            context.println("[ERROR] Failed to initialize PR notifications")
            context.println(e.toString())
            context.println(e.getMessage())
        }

    }

    /**
     * Function for update existing status check
     * Error status check can be replaced only by other error status check
     *
     * @param stageName Name of stage
     * @param title Name of status check which should be updated
     * @param status New status of status check
     * @param options Options map
     * @param message Message of status check. If it's empty current message will be gotten
     * @param url Url of status check. If it's empty current url will be gotten
     */
    static def updateStatus(String stageName, String title, String status, Map options, String message = "", String url = "") {
        if (options.githubNotificator) {
            options.githubNotificator.updateStatusPr(stageName, title, status, message, url)
        }
    }

    private def updateStatusPr(String stageName, String title, String status, String message = "", String url = "") {
        String statusTitle = "[${stageName.toUpperCase()}] ${title}"
        try {
            // prevent updating of checks by errors which are generated by aborted build
            if (commitSHA && isBuildWithSameSHA(commitSHA)) {
                context.println("[INFO] Found next build which has same SHA of target commit as this commit. Status check won't be updated")
                return
            }
            if (statusTitle.contains("~")) {
                String[] statusTitleParts = statusTitle.split("~")
                String engine = ""
                if (statusTitleParts.length == 2 && statusTitleParts[1].contains("-")) {
                    engine = statusTitleParts[1].split("-")[1]
                }
                statusTitle = engine ? "${statusTitleParts[0]}-${engine}" : "${statusTitleParts[0]}"
                statusTitle = statusTitle.replace(".json", "")
            }

            def statusChecks = githubApiProvider.getStatusChecks(
                repositoryUrl: repositoryUrl,
                head_sha: commitSHA
            )

            Boolean checkFound = false
            if (statusChecks["total_count"] > 0) {
                for (prStatus in statusChecks["check_runs"]) {
                    if (statusTitle == prStatus["name"]) {
                        checkFound = true
                        if (!url) {
                            url = prStatus["details_url"] ?: ""
                        }
                        if (!message) {
                            message = prStatus["output"]["title"] ?: ""
                        }
                        break
                    }
                }
            }
            if (!checkFound) {
                throw new Exception("Could not find suitable status check")
            }

            githubApiProvider.createOrUpdateStatusCheck(
                repositoryUrl: repositoryUrl,
                status: status,
                name: statusTitle,
                head_sha: commitSHA,
                details_url: url,
                output: [
                    title: message,
                    summary: "Use link below to check build details"
                ]
            )
        } catch (e) {
            context.println("[ERROR] Failed to update status for ${statusTitle}")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    /**
     * Function for receive current status of existing status check
     *
     * @param stageName Name of stage
     * @param title Name of status check which should be updated
     * @param options Options map
     */
    static def getCurrentStatus(String stageName, String title, Map options) {
        if (options.githubNotificator) {
            options.githubNotificator.getCurrentStatusPr(stageName, title)
        }
    }

    private def getCurrentStatusPr(String stageName, String title) {
        String statusTitle = "[${stageName.toUpperCase()}] ${title}"
        try {
            if (statusTitle.contains("~")) {
                String[] statusTitleParts = statusTitle.split("~")
                String engine = ""
                if (statusTitleParts.length == 2 && statusTitleParts[1].contains("-")) {
                    engine = statusTitleParts[1].split("-")[1]
                }
                statusTitle = engine ? "${statusTitleParts[0]}-${engine}" : "${statusTitleParts[0]}"
                statusTitle = statusTitle.replace(".json", "")
            }

            def statusChecks = githubApiProvider.getStatusChecks(
                repositoryUrl: repositoryUrl,
                head_sha: commitSHA
            )

            Boolean checkFound = false
            if (statusChecks["total_count"] > 0) {
                for (prStatus in statusChecks["check_runs"]) {
                    if (statusTitle == prStatus["name"]) {
                        checkFound = true
                        if (prStatus["conclusion"]) {
                            return prStatus["conclusion"]
                        } else {
                            return prStatus["status"]
                        }
                    }
                }
            } 
            if (!checkFound) {
                throw new Exception("Could not find suitable status check")
            }
        } catch (e) {
            context.println("[ERROR] Failed to get status for ${statusTitle}")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    /**
     * Function for close unfinished stages (pending or failure status checks)
     * All pending and failure status checks will be marked as errored
     * It can be useful if build was aborted
     *
     * @param options Options map
     * @param message Message of status check. If it's empty current message will be gotten
     */
    static def closeUnfinishedSteps(Map options, String message = "") {
        if (options.githubNotificator) {
            options.githubNotificator.closeUnfinishedStepsPr(message)
        }
    }

    private def closeUnfinishedStepsPr(String message = "") {
        try {
            if(statusesClosed.compareAndSet(false, true)) {
                if (isBuildWithSameSHA(commitSHA)) {
                    context.println("[INFO] Found next build which has same SHA of target commit as this commit. Status checks won't be closed")
                    return
                }
                List stagesList = []
                stagesList << "[PREBUILD] Jenkins build configuration"
                buildCases.each { stagesList << "[BUILD] " + it }
                testCases.each { stagesList << "[TEST] " + it }
                if (hasDeployStage) {
                    deployCases.each { stagesList << "[DEPLOY] " + it }
                }
                def statusChecks = githubApiProvider.getStatusChecks(
                    repositoryUrl: repositoryUrl,
                    head_sha: commitSHA
                )
                for (prStatus in statusChecks["check_runs"]) {
                    if (stagesList.contains(prStatus["name"])) {
                        if (prStatus["status"] == "in_progress" || prStatus["status"] == "queued" || prStatus["conclusion"] == "failure" || prStatus["timed_out"] == "failure") {
                            if (!message) {
                                message = prStatus["output"]["title"]
                            }
                            githubApiProvider.createOrUpdateStatusCheck(
                                repositoryUrl: repositoryUrl,
                                status: "error",
                                name: prStatus["name"],
                                head_sha: commitSHA,
                                details_url: prStatus["details_url"] ?: "",
                                output: [
                                    title: message,
                                    summary: "Use link below to check build details"
                                ]
                            )
                        }
                        stagesList.remove(prStatus["name"])
                    }
                    if (stagesList.size() == 0) {
                        break
                    }
                }
                context.println("[INFO] Unfinished steps were closed")
            }
        } catch (e) {
            context.println("[ERROR] Failed to close unfinished steps")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    private Boolean isBuildWithSameSHA(String commitSHA) {
        try {
            //check that some of next builds (if it exists) has different sha of target commit
            RunWrapper nextBuild = context.currentBuild.getNextBuild()
            while(nextBuild) {
                String nextBuildSHA = ""
                nextBuildSHA = commitSHA
                //if it isn't possible to find commit SHA in description - it isn't initialized yet. Wait 1 minute
                if(!nextBuildSHA) {
                    context.sleep(60)
                }
                //if it still isn't possible to get SHA or SHAs are same - it isn't necessary to close status checks (next build will do it if it'll be necessary)
                if((nextBuild && !nextBuildSHA) || nextBuildSHA == commitSHA) {
                    return true
                }
                nextBuild = nextBuild.getNextBuild()

                return false
            }
        } catch (e) {
            context.println("[ERROR] Failed to find build with same SHA")
            context.println(e.toString())
            context.println(e.getMessage())
        }
        return false
    }

    /**
     * Function for close status checks which represents test stages on some OS
     * It can be useful if building on some OS didn't finish successfully
     *
     * @param options Options map
     * @param osName OS name on which building failed
     */
    static def failPluginBuilding(Map options, String osName) {
        if (options.githubNotificator) {
            options.githubNotificator.failPluginBuildingPr(osName)
        }
    }

    private def failPluginBuildingPr(String osName) {
        try {
            def statusChecks = githubApiProvider.getStatusChecks(
                repositoryUrl: repositoryUrl,
                head_sha: commitSHA
            )
            for (prStatus in statusChecks["check_runs"]) {
                if (prStatus["name"].contains("[TEST]") && prStatus["name"].contains(osName)) {
                    githubApiProvider.createOrUpdateStatusCheck(
                        repositoryUrl: repositoryUrl,
                        status: "error",
                        name: prStatus["name"],
                        head_sha: commitSHA,
                        details_url: prStatus["details_url"] ?: "",
                        output: [
                            title: prStatus["output"]["title"] ?: "",
                            summary: "Use link below to check build details"
                        ]
                    )
                }
            }
            context.println("[INFO] Test steps were closed")
        } catch (e) {
            context.println("[ERROR] Failed to close test steps")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

}
