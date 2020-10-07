import groovy.json.JsonOutput
import java.text.SimpleDateFormat

@NonCPS
def parseResponse(String response) {
    def jsonSlurper = new groovy.json.JsonSlurperClassic()

    return jsonSlurper.parseText(response)
}

def parseDescriptionRow(String row) {
    return row.split(':')[1].replace('<b>', '').replace('</b>', '').trim()
}

def createRelease(String jobName, String repositoryUrl, String branch) {
    List possibleArtifactsExtensions = ['.zip', '.msi', '.dmg'] 

    String jenkinsUrl = 'https://rpr.cis.luxoft.com'
    String repositoryApiUrl = repositoryUrl.replace('https://github.com', 'https://api.github.com/repos')
    String repositoryUploadUrl = repositoryUrl.replace('https://github.com', 'https://uploads.github.com/repos')

    cleanWS('Windows')
    def buildsList = httpRequest(
        url: "${jenkinsUrl}/job/${jobName}/job/${branch}/api/json?tree=builds[url]",
        authentication: 'jenkinsCredentials',
        httpMode: 'GET'
    )
    String targetUrl
    String description
    def buildsListParsed = parseResponse(buildsList.content)
    for (build in buildsListParsed['builds']) {
        String buildUrl = build['url']
        def buildInfo = httpRequest(
            url: "${buildUrl}/api/json?tree=result,description",
            authentication: 'jenkinsCredentials',
            httpMode: 'GET'
        )
        def buildInfoParsed = parseResponse(buildInfo.content)
        if (buildInfoParsed['result'] == 'SUCCESS' || buildInfoParsed['result'] == 'UNSTABLE') {
            println("[INFO] Success build was found: ${buildUrl}")
            targetUrl = buildUrl
            description = buildInfoParsed['description']
            break
        }
    }
    if (targetUrl) {
        String version = ""
        String commitSha = ""
        String[] descriptionParts = description.split('<br/>')
        for (part in descriptionParts) {
            if (part.contains('Version')) {
                version = parseDescriptionRow(part)
            } else if (part.contains('Commit SHA')) {
                commitSha = parseDescriptionRow(part)
            }
        }
        if (version) {
            println("[INFO] Plugin version was found: ${version}")
        } else {
            println("[ERROR] Plugin version wan't found")
            throw new Exception("Plugin version wan't found")
        }
        if (commitSha) {
            println("[INFO] Commit SHA was found: ${commitSha}")
        } else {
            println("[ERROR] Commit SHA wan't found")
            throw new Exception("Commit SHA wasn't found")
        }

        //delete previous release
        def releases = httpRequest(
            url: "${repositoryApiUrl}/releases",
            authentication: 'radeonprorender',
            httpMode: 'GET'
        )
        def releasesParsed = parseResponse(releases.content)
        def shouldBeUploaded = false
        def tagIsBusy = false
        def releaseAlreadyPushed = false
        for (release in releasesParsed) {
            if (release['tag_name'] == "v${version}" && release['author']['login'] != 'radeonprorender') {
                println("[INFO] Release with same tag has already published by other user")
                tagIsBusy = true
                break
            } else if (release['name'].contains("Weekly Development builds")) {
                println("[INFO] Previous release was found")
                if (release['tag_name'] == "v${version}") {
                    println("[INFO] Previous release has same tag. It won't be reuploaded")
                    releaseAlreadyPushed = true
                } else {
                    httpRequest(
                        url: "${repositoryApiUrl}/releases/${release.id}",
                        authentication: 'radeonprorender',
                        httpMode: 'DELETE'
                    )
                    println("[INFO] Previous release was deleted")
                }

                break
            }
        }
        if (!tagIsBusy && !releaseAlreadyPushed) {
            shouldBeUploaded = true
        }

        if (shouldBeUploaded) {
            //get parent commits of found commit
            def parentCommit = httpRequest(
                url: "${repositoryApiUrl}/commits/${commitSha}",
                authentication: 'radeonprorender',
                httpMode: 'GET'
            )
            def parentCommitParsed = parseResponse(parentCommit.content)

            def parentCommitSha = parentCommitParsed['parents'][0]['sha']

            //get list of artifacts
            def fileNames = httpRequest(
                url: "${targetUrl}/api/json?tree=artifacts[fileName]",
                authentication: 'jenkinsCredentials',
                httpMode: 'GET'
            )

            def date = new Date()
            def dateFormat = new SimpleDateFormat("MM/dd/yyyy")
            def formattedDate = dateFormat.format(date)

            def releaseData = [
                    tag_name: "v${version}",
                    target_commitish: parentCommitSha,
                    name: "Weekly Development builds v${version} (${formattedDate})",
                    prerelease: true
            ]

            def releaseInfo = httpRequest(
                url: "${repositoryApiUrl}/releases",
                authentication: 'radeonprorender',
                httpMode: 'POST',
                requestBody: JsonOutput.toJson(releaseData),
                customHeaders: [
                    [name: 'Content-type', value: 'application/json']
                ]
            )
            def releaseInfoParsed = parseResponse(releaseInfo.content)

            def fileNamesParsed = parseResponse(fileNames.content)
            for (fileName in fileNamesParsed['artifacts']) {
                for (extension in possibleArtifactsExtensions) {
                    String assetName = fileName['fileName']
                    if (assetName.endsWith(extension)) {
                        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'jenkinsCredentials', usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_PASSWORD']]) {
                            bat """
                                curl --retry 5 -L -O -J -u %JENKINS_USERNAME%:%JENKINS_PASSWORD% "${targetUrl}/artifact/${fileName.fileName}"
                            """
                        }
                        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'radeonprorender', usernameVariable: 'GITHUB_USERNAME', passwordVariable: 'GITHUB_PASSWORD']]) {
                            bat """
                                curl -X POST --retry 5 -H "Content-Type: application/octet-stream" --data-binary @${assetName} -u %GITHUB_USERNAME%:%GITHUB_PASSWORD% "${repositoryUploadUrl}/releases/${releaseInfoParsed.id}/assets?name=${assetName}"
                            """
                        }
                    }
                }
            }
        }
    } else {
        println("[ERROR] Success build wasn't found!")
    }
}

def call(List targets) {
    String PRJ_NAME='GithubRelease'

    timestamps {
        def tasks = [:]

        for (int i = 0; i < targets.size(); i++) {
            def target = targets[i]
            String stageName = target['jobName'].replace('Auto', '')

            try {
                tasks[stageName] = {
                    stage(stageName) {
                        node("Windows && Builder") {
                            ws("WS/${PRJ_NAME}") {
                                createRelease(target['jobName'], target['repositoryUrl'], target['branch'])
                            }
                        }
                    }
                }
            } catch (e) {
                currentBuild.result = "FAILURE"
                println "Exception: ${e.toString()}"
                println "Exception message: ${e.getMessage()}"
                println "Exception stack trace: ${e.getStackTrace()}"
            }
        }

        parallel tasks
    }
}