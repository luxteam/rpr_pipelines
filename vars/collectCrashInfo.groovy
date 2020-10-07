def call(String osName, Map options, Integer retryNumber){
    String testsOrTestPackage = options['tests'];
    if (testsOrTestPackage == ''){
        testsOrTestPackage = options['testsPackage']
    }
    logName = "${testsOrTestPackage}.${env.NODE_NAME}.retry_${retryNumber}.crash.log"
    println "Collect crash logs"
    switch(osName){
        case 'Windows':
            powershell """
                echo ${env.NODE_NAME} >> ${logName}
                Get-Date >> ${logName}
                Get-EventLog -LogName System -Newest 200 >> ${logName}
                Get-EventLog -LogName Application -Newest 200 >> ${logName}
                ps | sort -des cpu | select -f 200 | ft -a >> ${logName}
            """
            break;
        case 'OSX':
            sh """
                echo ${env.NODE_NAME} >> ${logName}
                date >> ${logName}
                log show --no-info --color always --predicate 'eventMessage CONTAINS[c] "radeon" OR eventMessage CONTAINS[c] "gpu" OR eventMessage CONTAINS[c] "amd"' --last 1h >> ${logName}
                top -l 1 | head -n 200 >> ${logName}
                sudo fs_usage -f filesys | head -n 200 >> ${logName}
            """
            break;
        default:
            sh """
                echo ${env.NODE_NAME} >> ${logName}
                date >> ${logName}
                dmesg | tail -n 200 >> ${logName}
                top -b | head -n 200 >> ${logName}
                sudo iotop --only -b | head -n 200 >> ${logName}
            """
            break;
    }
    archiveArtifacts artifacts: "*.crash.log"
    stash includes: "${logName}", name: "${logName}"
}