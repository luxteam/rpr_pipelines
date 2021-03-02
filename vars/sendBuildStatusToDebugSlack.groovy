def call(Map options) {

    if (currentBuild.result == "FAILURE") {

        String text 
        if (options["problemMessageManager"]) {
            text = options["problemMessageManager"].getMessages()
        } else {
            text = "Failed in:  ${options.FAILED_STAGES.join('\n')}"
        } 

        String debagSlackMessage = """[{
            "title": "${env.JOB_NAME} [${env.BUILD_NUMBER}]",
            "title_link": "${env.BUILD_URL}",
            "color": "#fc0356",
            "pretext": "${currentBuild.result}",
            "text": "${text.replace('\n', '\\n')}"
            }]
        """

        try {
            if ((env.BRANCH_NAME && env.BRANCH_NAME == "master") || env.CHANGE_BRANCH || env.JOB_NAME.contains("Weekly")) {
                slackSend(attachments: debagSlackMessage, channel: 'cis_failed_master', baseUrl: env.debagUrl, tokenCredentialId: 'debug-channel-master')
            } else {
                slackSend (attachments: debagSlackMessage, channel: env.debagChannel, baseUrl: env.debagUrl, tokenCredentialId: 'debug-channel')
            }
        } catch (e) {
            println("Error during slack notification to debug channel")
            println(e.toString())
        }
    }

}
