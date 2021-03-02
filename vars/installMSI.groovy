def call(String msiName, String log, Integer currentTry)
{
    log = log ?: "${STAGE_NAME}"
    if (fileExists(msiName)) {
        bat """
            msiexec /i "${msiName}" /quiet /qn /L+ie \"${env.WORKSPACE}\\${log}_${currentTry}.msi.install.log\" /norestart
        """
    } else {
        println "Missing msi ${msiName}"
    }
}
