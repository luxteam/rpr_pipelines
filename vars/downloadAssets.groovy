import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(String original_folder, String destination_folder)
{
    int times = 5
    int retries = 0
    int status = 0
    while (retries++ < times) {
        print('Try download assets with rsync #' + retries)
        try {
            withCredentials([string(credentialsId: 'buildsRemoteHost', variable: 'REMOTE_HOST')]) {
                if (isUnix()) {
                    status = sh(returnStatus: true, 
                        script: "${CIS_TOOLS}/receiveFilesSync.sh ${original_folder} ${CIS_TOOLS}/../TestResources/${destination_folder} ${REMOTE_HOST}")
                } else {
                    status = bat(returnStatus: true, 
                        script: "%CIS_TOOLS%\\receiveFilesSync.bat ${original_folder} /mnt/c/TestResources/${destination_folder} ${REMOTE_HOST}")
                }
            }
            if (status != 24) {
                return
            } else {
                print('Partial transfer due to vanished source files')
            }
        } catch (FlowInterruptedException error) {
            println "[INFO] Job was aborted during downloading assets."
            throw error
        } catch(e){
            println(e.toString())
            println(e.getMessage())
            println(e.getStackTrace())
        }
        sleep(60)
    }
}