/**
 *
 * @param remote - path where the files will be taken from.
 * @param local - path where files will be downloaded. For Windows specify relative path from %CIS_TOOLS% dir or absolute
 * path for Bash Windows version ("mnt/c/" for example).
 */
def call(String remote, String local)
{
    withCredentials([string(credentialsId: 'buildsRemoteHost', variable: 'REMOTE_HOST')]) {
        if(isUnix()) {
            sh "${CIS_TOOLS}/receiveFiles.sh \"${remote}\" ${local} $REMOTE_HOST"
        } else {
            bat "%CIS_TOOLS%\\receiveFiles.bat ${remote} ${local} %REMOTE_HOST%"
        }
    }
}