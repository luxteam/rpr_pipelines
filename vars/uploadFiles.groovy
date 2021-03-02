import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

/**
     * Upload files using sh/bat from CIS_TOOLS (based on ssh & rsync)
     * @param server_path - full path to the folder on the server
     * @param local_path - full path to the folder on the target PC 
     * @param remoteHost - remote host url (default - NAS)
*/

def call(String local_path, String server_path, String remoteHost = "nasURL") {
    int times = 1
    int retries = 0
    int status = 0
    while (retries++ < times) {
        print("Try to upload files with rsync №${retries}")
        try {
            withCredentials([string(credentialsId: remoteHost, variable: 'REMOTE_HOST')]) {
                // Avoid warnings connected with using Groovy String interpolation with credentials
                // See docs for more details: https://www.jenkins.io/doc/book/pipeline/jenkinsfile/#string-interpolation
                if (isUnix()) {
                    sh '$CIS_TOOLS/uploadFiles.sh' + " \"${local_path}\" ${server_path} " + '$REMOTE_HOST'
                } else {
                    bat '%CIS_TOOLS%\\uploadFiles.bat' + " \"${local_path}\" ${server_path} " + '%REMOTE_HOST%'
                }
            }
        } catch (FlowInterruptedException error) {
            println("[INFO] Job was aborted during uploading files to ${remoteHost}.")
            throw error
        } catch(e) {
            println(e.toString())
            println(e.getMessage())
            println(e.getStackTrace())
        }
        sleep(60)
    }
}