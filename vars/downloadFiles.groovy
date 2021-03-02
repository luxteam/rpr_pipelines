import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

/**
     * Download files using sh/bat from CIS_TOOLS (based on ssh & rsync)
     * @param server_path - full path to the folder on the server
     * @param local_path - full path to the folder on the target PC 
     * @param remoteHost - remote host url (default - NAS)
*/

def call(String server_path, String local_path, String remoteHost = "nasURL") {
    int times = 3
    int retries = 0
    int status = 0
    while (retries++ < times) {
        print("Try to download files with rsync №${retries}")
        try {
            withCredentials([string(credentialsId: remoteHost, variable: 'REMOTE_HOST')]) {
                // Avoid warnings connected with using Groovy String interpolation with credentials
                // See docs for more details: https://www.jenkins.io/doc/book/pipeline/jenkinsfile/#string-interpolation
                if (isUnix()) {
                    status = sh(returnStatus: true, 
                        script: '$CIS_TOOLS/downloadFilesSync.sh' + " \"${server_path}\" ${local_path} " + '$REMOTE_HOST')
                } else {
                    status = bat(returnStatus: true, 
                        script: '%CIS_TOOLS%\\downloadFilesSync.bat' + " \"${server_path}\" ${local_path} " + '%REMOTE_HOST%')
                }
            }
            if (status != 24) {
                return
            } else {
                print("Partial transfer due to vanished source files")
            }
        } catch (FlowInterruptedException error) {
            println("[INFO] Job was aborted during downloading files from ${remoteHost}.")
            throw error
        } catch(e) {
            println(e.toString())
            println(e.getMessage())
            println(e.getStackTrace())
        }
        sleep(60)
    }
}
