import groovy.json.JsonSlurperClassic
import groovy.json.JsonOutput


/**
 * Class which implements some requests to GithubApi
 */
class GithubApiProvider {

    static final List COMPLETED_STATUSES = ["success", "failure", "neutral", "cancelled", "skipped", "timed_out", "action_required"]

    def context
    volatile def installation_token

    /**
     * Main constructor
     *
     * @param context
     */
    GithubApiProvider(def context) {
        this.context = context
    }

    private def parseResponse(String response) {
        def jsonSlurper = new JsonSlurperClassic()

        return jsonSlurper.parseText(response)
    }

    private def buildQueryParams(Map params) {
        List paramPairs = []
        for (param in params) {
            paramPairs << "${param.key}=${param.value.replaceAll(' ', '%20')}"
        }
        return paramPairs.join("&")
    }

    private String receiveInstallationToken(String organization_name) {
        try {
            context.withCredentials([context.string(credentialsId: "githubNotificationAppKey_dev", variable: "GITHUB_APP_KEY"),
                context.string(credentialsId: "githubNotificationAppId_dev", variable: "GITHUB_APP_ID")]) {
    
                context.withEnv(["GITHUB_APP_KEY=${context.GITHUB_APP_KEY}"]) {
                    if (context.isUnix()) {
                        installation_token = context.python3("${context.CIS_TOOLS}/auth_github.py --github_app_id ${context.GITHUB_APP_ID} --organization_name ${organization_name} --duration 540").split("\n")[-1]
                    } else {
                        installation_token = context.python3("${context.CIS_TOOLS}\\auth_github.py --github_app_id ${context.GITHUB_APP_ID} --organization_name ${organization_name} --duration 540").split("\n")[-1]
                    }
                }
            }
        } catch (Exception e) {
            context.println("[ERROR] Failed to get installation token")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    private def withInstallationAuthorization(String organization_name, Closure function) {
        if (!installation_token) {
            receiveInstallationToken(organization_name)
        }
        def response = function()
        if (response.status == 401) {
            receiveInstallationToken(organization_name)
        } else {
            return parseResponse(response.content)
        }
        response = function()
        if (response.status == 401) {
            throw new Exception("Could not authorize request with token ${installation_token}")
        } else {
            return parseResponse(response.content)
        }
    }

    /**
     * Function for create or update status check (see https://docs.github.com/en/rest/reference/checks#create-a-check-run)
     *
     * @param params params of request (all params are specified by documentation except additional required params: repositoryUrl and status(set always insted of conslusion))
     */
    def createOrUpdateStatusCheck(Map params) {
        params = params.clone()
        def repositoryUrl = params["repositoryUrl"]
        params.remove("repositoryUrl")
        def organization_name = repositoryUrl.split("/")[-2]
        if (COMPLETED_STATUSES.contains(params["status"])) {
            params["conclusion"] = params["status"]
            params["status"] = "completed"
        }

        return withInstallationAuthorization(organization_name) {
            def response = context.httpRequest(
                url: "${repositoryUrl.replace('https://github.com', 'https://api.github.com/repos')}/check-runs",
                httpMode: "POST",
                requestBody: JsonOutput.toJson(params),
                customHeaders: [
                    [name: "Content-type", value: "application/json"],
                    [name: "Authorization", value: "token ${installation_token}"]
                ],
                validResponseCodes: '0:401'
            )

            return response
        }
    }

    /**
     * Function for get list of status checks (see https://docs.github.com/en/rest/reference/checks#list-check-runs-for-a-git-reference)
     *
     * @param params params of request (all params are specified by documentation except additional required params: repositoryUrl and head_sha)
     */
    def getStatusChecks(Map params) {
        params = params.clone()
        def repositoryUrl = params["repositoryUrl"]
        params.remove("repositoryUrl")
        def organization_name = repositoryUrl.split("/")[-2]
        def commitSHA = params["head_sha"]
        params.remove("head_sha")

        String queryParams = "${buildQueryParams(params)}"

        return withInstallationAuthorization(organization_name) {
            def response = context.httpRequest(
                url: "${repositoryUrl.replace('https://github.com', 'https://api.github.com/repos')}/commits/${commitSHA}/check-runs?${queryParams}",
                httpMode: "GET",
                customHeaders: [
                    [name: "Authorization", value: "token ${installation_token}"]
                ],
                validResponseCodes: '0:401'
            )

            return response
        }
    }

    /**
     * Function for get info about specified pull request (see https://docs.github.com/en/rest/reference/pulls#get-a-pull-request)
     *
     * @param repositoryUrl url to target pull request
     */
    def getPullRequest(String repositoryUrl) {
        def response = context.httpRequest(
            url: "${repositoryUrl.replace('https://github.com', 'https://api.github.com/repos').replace('/pull/', '/pulls/')}",
            authentication: 'radeonprorender',
            httpMode: "GET",
        )

        return parseResponse(response.content)
    }

}