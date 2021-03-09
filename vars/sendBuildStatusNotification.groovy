
def call(String buildStatus = 'STARTED', String channel = '', String baseUrl = '', String token = '', Map options)
{
    println("Sending information about build status: ${buildStatus}")

    // build status of null means successful
    buildStatus =  buildStatus ?: 'SUCCESSFUL'
    buildStatus = options.CBR ?: buildStatus
    options.commitMessage = options.commitMessage ?: 'undefiend'
    String BRANCH_NAME = env.BRANCH_NAME ?: options.projectBranch

    // Default values
    def colorName = 'RED'
    def colorCode = '#FF0000'

    // Override default values based on build status
    if (buildStatus == 'SUCCESSFUL') {
      color = 'GREEN'
      colorCode = '#00FF00'
    } else if (buildStatus == 'ABORTED') {
      colorCode = '#ff8833'
    } else if (buildStatus == 'SKIPPED') {
      color = 'BLUE'
      colorCode = '#0000FF'
    } else if (buildStatus == 'UNSTABLE') {
      colorCode = '#f4f4c8'
    } else {
      color = 'RED'
      colorCode = '#FF0000'
    }

    // if env.CHANGE_BRANCH not empty display pull request link
    String INIT_BRANCH = env.CHANGE_BRANCH ? "\\nSource branch: *${env.CHANGE_BRANCH}*" : ''
    // if reportName not empty display link to html report
    String HTML_REPORT_LINK = options.reportName ? "${env.BUILD_URL}${options.reportName}" : ''

    List reports = []
    if (options.engines) {
      options.engines.each { engine ->
        reports << "-${engine}"
      }
    } else {
      reports << ""
    }

    for (report in reports) {
      String preText = 
      String testsStatus = """
      ,{
        "mrkdwn_in": ["text"],
        "title": "Brief info",
        "pretext": "AutoTests Results",
        "text": ${options['testsStatus' + report]},
        "footer": "LUX CIS",
        "actions": [
          {"text": "Report",
          "type": "button",
          "url": "${HTML_REPORT_LINK}"
          }]
      }"""

      testsStatus = options.testsStatus ? testsStatus  : ''

      String slackMessage = """[{
      "fallback": "${buildStatus} ${env.JOB_NAME}",
      "title": "${buildStatus}\\nCIS: ${env.JOB_NAME} [${env.BUILD_NUMBER}]",
      "title_link": "${env.BUILD_URL}",
      "color": "${colorCode}",
      "text": ">>> Branch: *${BRANCH_NAME}*${INIT_BRANCH}\\nAuthor: *${options.AUTHOR_NAME}*\\nCommit message:\\n```${utils.escapeCharsByUnicode(options.commitMessage.replace('\n', '\\n'))}```",
      "mrkdwn_in": ["text", "title"],
      "attachment_type": "default",
      "actions": [
        {"text": "PullRequest on GitHub",
        "type": "button",
        "url": "${env.CHANGE_URL}"
        }
      ]
      }${testsStatus}]""".replace('%2F', '_')

      // Send notifications
      try {
          slackSend (attachments: slackMessage, channel: channel, baseUrl: baseUrl, tokenCredentialId: token)
      } catch (e) {
          println("Error during slack notification to project channel")
          println(e.toString())
      }
    }
}
