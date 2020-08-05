def call(Map options) {

    if (currentBuild.result == "FAILURE") {

        String debagSlackMessage = """[{
          "title": "${env.JOB_NAME} [${env.BUILD_NUMBER}]",
          "title_link": "${env.BUILD_URL}",
          "color": "#fc0356",
          "pretext": "${currentBuild.result}",
          "text": "Failed in: ${options.FAILED_STAGES.join("\n")}"
          }]
        """;
        // TODO: foreach
        /*
        "fields": [ { "title": '', value: '', short: "false"}, ... ]
         */

        try {
            if ((env.BRANCH_NAME && (env.BRANCH_NAME == "master" || env.BRANCH_NAME == "develop")) || env.JOB_NAME.contains("Weekly")) {
                // notify about master & weekly failures
                slackSend(attachments: debagSlackMessage, channel: 'cis_failed_master', baseUrl: env.debagUrl, tokenCredentialId: 'debug-channel-master')
            } else if (env.BRANCH_NAME || env.CHANGE_BRANCH) {
                // notify about auto jobs failures
                slackSend (attachments: debagSlackMessage, channel: env.debagChannel, baseUrl: env.debagUrl, tokenCredentialId: 'debug-channel')
            }
        } catch (e) {
            println("Error during slack notification to debug channel")
            println(e.toString())
        }
    }
}
