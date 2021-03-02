
def call(String osName, String logName="", def currentTry="")
{
    // logName null - use STAGE_NAME
    logName =  logName ?: "${STAGE_NAME}"
    currentTry = currentTry != "" && currentTry != null ? "_${currentTry}" : ""

    if (osName == 'Windows') {
         bat "HOSTNAME  >  \"${logName}${currentTry}.log\""
         bat "set       >> \"${logName}${currentTry}.log\""
    } else {
         sh "uname -a   >  \"${logName}${currentTry}.log\""
         sh "env        >> \"${logName}${currentTry}.log\""
    }
}
