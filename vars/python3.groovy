/**
 * @param command
 * @return for Windows returned string contains executed command
 */
def call(String command, String version = "") {
    println(command)
    String ret

    if(isUnix()) {
        version = version.isEmpty() ? 3 : version
        ret = sh(returnStdout: true, script:"python${version} ${command}")
    } else {
        version = version.isEmpty() ? "35" : version
        withEnv(["PATH=c:\\python${version}\\;c:\\python${version}\\scripts\\;${PATH}"]) {
            ret = bat(
                script: """
                python ${command}
                """,
                returnStdout: true
            )
        }
    }
    return ret
}
