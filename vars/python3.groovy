def call(String command)
{
    echo command
    String ret
    if(isUnix())
    {
        ret = sh(returnStdout: true, script:"python3 ${command}")
    }
    else
    {
        echo "before withEnv"
        withEnv(["PATH=c:\\python35\\;c:\\python35\\scripts\\;${PATH}"]) {
            echo "before bat"
            ret = bat(
                script: """
                python ${command}
                """,
                returnStdout: true
            )
            echo "after bat"
        }
    }
    echo "before return"
    return ret
}
