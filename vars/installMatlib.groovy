import utils

/**
 * Install Radeon ProRender Material Library support for all available tools
 * @param osName - os on which the check will take place
 * @param options - extra info like Jenkins stage name or current try try of that stage
 */
 
def call(String osName, Map options) {
    switch(osName) {
        case 'Windows':
            String installer = "C:\\TestResources\\RadeonProRenderMaterialLibrary.msi"
            if (!fileExists(installer)) {
                withCredentials([string(credentialsId: "jenkinsURL", variable: "JENKINS_URL")]) {
                    utils.downloadFile(
                            this,
                            "${JENKINS_URL}/job/RadeonProRenderMaterialLibrary/lastSuccessfulBuild/artifact/RadeonProRenderMaterialLibrary.msi",
                            "C:\\TestResources\\",
                            "jenkinsUser"
                    )
                }
            }
            uninstallMSI("Radeon%Material%", options.stageName, options.currentTry)
            // msiexec doesn't work with relative paths
            bat """
                start /wait msiexec /i ${installer} /passive
            """
            break

        case 'OSX':
            println "Not supported"
            break

        default:
            String installer = "${CIS_TOOLS}/../TestResources/RadeonProRenderMaterialLibrary.run"
            if (!fileExists(installer)) {
                withCredentials([string(credentialsId: "jenkinsURL", variable: "JENKINS_URL")]) {
                    utils.downloadFile(
                            this,
                            "${JENKINS_URL}/job/RadeonProRenderMaterialLibrary/lastSuccessfulBuild/artifact/RadeonProRenderMaterialLibrary.run",
                            "${CIS_TOOLS}/../TestResources/",
                            "jenkinsUser"
                    )
                }
            }
            sh """
                /home/\$USER/local/share/rprmaterials/uninstall.py --just-do-it 
                chmod +x ${installer}
                ${installer}
            """
    }
}