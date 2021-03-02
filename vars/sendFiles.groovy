
def call(String local, String remote)
{
    withCredentials([string(credentialsId: 'buildsRemoteHost', variable: 'REMOTE_HOST')]) {
        if (isUnix()) {
            sh "${CIS_TOOLS}/sendFiles.sh \"${local}\" ${remote} $REMOTE_HOST"
        } else {
            bat "%CIS_TOOLS%\\sendFiles.bat ${local} ${remote} ${REMOTE_HOST} %REMOTE_HOST%"
        }
    }
}
