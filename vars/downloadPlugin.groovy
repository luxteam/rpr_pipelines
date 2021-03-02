def runCurl(String curlCommand, Integer tries=5) {
    Integer currentTry = 0
    while (currentTry++ < tries) {
        println("[INFO] Try to download plugin through curl (try #${currentTry})")
        try {
            timeout(time: 90, unit: "SECONDS") {
                if (isUnix()) {
                    sh """
                        ${curlCommand}
                    """
                } else {
                    bat """
                        ${curlCommand}
                    """
                }
            }
            break
        } catch (e) {
            println("[ERROR] Failed to download plugin during try #${currentTry}: ${e.getMessage()}")
            if (currentTry == tries) {
                throw e
            }
        }
    }
}


def call(String osName, String tool, Map options, String credentialsId = '') {
    String customBuildLink = ""
    String extension = ""

    switch(osName) {
        case 'Windows':
            customBuildLink = options['customBuildLinkWindows']
            extension = "msi"
            break
        case 'OSX':
            customBuildLink = options['customBuildLinkOSX']
            extension = "dmg"
            break
        case 'Ubuntu':
            customBuildLink = options['customBuildLinkLinux']
            extension = "run"
        case 'Ubuntu18':
            customBuildLink = options['customBuildLinkUbuntu18']
        // Ubuntu20
        default:
            customBuildLink = options['customBuildLinkUbuntu20']
    }

    if (tool.contains("Blender") || tool.startsWith("bin")) {
        extension = "zip"
    }

    print "[INFO] Used specified pre built plugin for ${tool}."

    if (customBuildLink.startsWith("https://builds.rpr")) {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'builsRPRCredentials', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            runCurl("curl -L -o ${tool}_${osName}.${extension} -u $USERNAME:$PASSWORD \"${customBuildLink}\"")
        }
    } else if (customBuildLink.startsWith("https://rpr.cis")) {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'jenkinsUser', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            runCurl("curl -L -o ${tool}_${osName}.${extension} -u $USERNAME:$PASSWORD \"${customBuildLink}\"")
        }
    } else if (customBuildLink.startsWith("/CIS/")) {
        downloadFiles("/volume1${customBuildLink}", ".")
        if (isUnix()) {
            sh """
                mv ${customBuildLink.split("/")[-1]} ${tool}_${osName}.${extension}
            """
        } else {
            bat """
                move ${customBuildLink.split("/")[-1]} ${tool}_${osName}.${extension}
            """
        }
    } else {
        if (credentialsId) {
            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                runCurl("curl -L -o ${tool}_${osName}.${extension} -u $USERNAME:$PASSWORD \"${customBuildLink}\"")
            }
        } else {
            runCurl("curl -L -o ${tool}_${osName}.${extension} -u $USERNAME:$PASSWORD \"${customBuildLink}\"")
        }
    }

    validatePlugin(osName, "${tool}_${osName}.${extension}", options)

    // We haven't any branch so we use sha1 for identifying plugin build
    def pluginSha = sha1 "${tool}_${osName}.${extension}"
    println "Downloaded plugin sha1: ${pluginSha}"

    switch(osName) {
        case 'Windows':
            options['pluginWinSha'] = pluginSha
            break
        case 'OSX':
            options['pluginOSXSha'] = pluginSha
            break
        default:
            options['pluginUbuntuSha'] = pluginSha
    }
}
