def call(String osName, String tool, Map options, String credentialsId = '')
{
    String customBuildLink = ""
    String extentsion = ""

    switch(osName)
    {
        case 'Windows':
            customBuildLink = options['customBuildLinkWindows']
            extentsion = "msi"
            break;
        case 'MacOS':
            customBuildLink = options['customBuildLinkMacOS']
            extentsion = "dmg"
            break;
        default:
            customBuildLink = options['customBuildLinkLinux']
            extentsion = "run"
    }

    if (tool == "Blender") {
        extentsion = "zip"
    }

    print "[INFO] Used specified pre built plugin for ${tool}."

    if (customBuildLink.startsWith("https://builds.rpr"))
    {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'builsRPRCredentials', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            if (osName == "Windows") {
                bat """
                    curl -L -o RadeonProRender${tool}_${osName}.${extentsion} -u %USERNAME%:%PASSWORD% "${options['customBuildLinkWindows']}"
                """
            } else if (osName == "MacOS") {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extentsion} -u $USERNAME:$PASSWORD "${options['customBuildLinkMacOS']}"
                """
            } else {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extentsion} -u $USERNAME:$PASSWORD "${options['customBuildLinkLinux']}"
                """
            }
        }
    } 
    else if (customBuildLink.startsWith("https://rpr.cis")) 
    {
        withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'jenkinsUser', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
            if (osName == "Windows") {
                bat """
                    curl -L -o RadeonProRender${tool}_${osName}.${extentsion} -u %USERNAME%:%PASSWORD% "${options['customBuildLinkWindows']}"
                """
            } else if (osName == "MacOS") {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extentsion} -u $USERNAME:$PASSWORD "${options['customBuildLinkMacOS']}"
                """
            } else {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extentsion} -u $USERNAME:$PASSWORD "${options['customBuildLinkLinux']}"
                """
            }
        }
    }
    else
    {
        if (credentialsId) {
            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                if (osName == "Windows") {
                    bat """
                        curl -L -o RadeonProRender${tool}_${osName}.${extentsion} -u %USERNAME%:%PASSWORD% "${options['customBuildLinkWindows']}"
                    """
                } else if (osName == "MacOS") {
                    sh """
                        curl -L -o RadeonProRender${tool}_${osName}.${extentsion} -u $USERNAME:$PASSWORD "${options['customBuildLinkMacOS']}"
                    """
                } else {
                    sh """
                        curl -L -o RadeonProRender${tool}_${osName}.${extentsion} -u $USERNAME:$PASSWORD "${options['customBuildLinkLinux']}"
                    """
                }
            }
        } else {
            if (osName == "Windows") {
                bat """
                    curl -L -o RadeonProRender${tool}_${osName}.${extentsion} "${options['customBuildLinkWindows']}"
                """
            } else if (osName == "MacOS") {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extentsion} "${options['customBuildLinkMacOS']}"
                """
            } else {
                sh """
                    curl -L -o RadeonProRender${tool}_${osName}.${extentsion} "${options['customBuildLinkLinux']}"
                """
            }
        }
    }

    // We haven't any branch so we use sha1 for idetifying plugin build
    def pluginSha = sha1 "RadeonProRender${tool}_${osName}.${extentsion}"
    println "Downloaded plugin sha1: ${pluginSha}"

    switch(osName)
    {
        case 'Windows':
            options['pluginWinSha'] = pluginSha
            break;
        case 'MacOS':
            options['pluginMacOSSha'] = pluginSha
            break;
        default:
            options['pluginUbuntuSha'] = pluginSha
    }
}
