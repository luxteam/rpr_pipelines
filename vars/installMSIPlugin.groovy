def call(String osName, String tool, Map options, Boolean matlib=false, Boolean deinstall=false)
{

    // deinstall plugin from tool pipeline
    if (deinstall) {
        println "[INFO] Uninstalling RPR Plugin for ${osName}"
        uninstallPlugin(osName, tool, options)
        return
    }

    // Prebuilt plugin will be reinstalled in any cases
    if (options.isPreBuilt) { 
        reinstallPlugin(osName, tool, options, matlib)
        return true

    } else {

        if (checkExistenceOfPlugin(osName, tool, options)) {
            println '[INFO] Current plugin is already installed.'
            return false
        } else {
            reinstallPlugin(osName, tool, options, matlib)
            return true
        }

    }
}
    

def checkExistenceOfPlugin(String osName, String tool, Map options) 
{
    println "[INFO] Checking existence of the RPR plugin on test PC."

    try {

        String installedProductCode = ""

        switch(osName)
        {
            case 'Windows':
                // Finding installed plugin on test PC
                installedProductCode = powershell(
                        script: """
                        (Get-WmiObject -Class Win32_Product -Filter \"Name LIKE 'Radeon%${tool}%'\").IdentifyingNumber
                        """, returnStdout: true).trim()[1..-2]

                println "[INFO] Installed MSI product code: ${installedProductCode}"
                println "[INFO] Built MSI product code: ${options.productCode}"

                return installedProductCode==options.productCode

            default:
                echo "[WARNING] Check existence of installed plugin is not supported for ${osName}. Reinstalling..."
        }

    } catch (e) {
        echo "[ERROR] Failed to compare installed and built plugin. Reinstalling..."
        println(e.toString())
        println(e.getMessage())
    }

    return false
}

def reinstallPlugin(String osName, String tool, Map options, Boolean matlib) {

    println "[INFO] Uninstalling RPR Plugin for ${osName}"
    uninstallPlugin(osName, tool, options)
    println "[INFO] Installing RPR Plugin for ${osName}"
    installPlugin(osName, tool, options)
    if (matlib){
        installMatLib(osName, options)
    }

}

def uninstallPlugin(String osName, String tool, Map options){

    switch(osName)
    {
        case 'Windows':
            uninstallMSI("Radeon%${tool}%", options.stageName, options.currentTry)
            break;

        default:
            echo "[WARNING] Uninstalling plugin for ${osName} is not supported. The plugin will be installed on top of the previous one."
    }

}

def installPlugin(String osName, String tool, Map options){

    switch(osName)
    {
        case 'Windows':
            dir("..\\..\\PluginsBinaries") {
                if (options['isPreBuilt']) {
                    addon_name = options.pluginWinSha
                } else {
                    addon_name = options.productCode
                }
                println "[INFO] MSI name: ${addon_name}.msi"
                installMSI("${addon_name}.msi", options.stageName, options.currentTry)
            }
            break;

        case 'OSX':
            sh """
                $CIS_TOOLS/install${tool}Plugin.sh ${CIS_TOOLS}/../PluginsBinaries/${options.pluginOSXSha}.dmg >> \"${options.stageName}_${options.currentTry}.install.log\" 2>&1
            """
            break;

        default:
            echo "[WARNING] Uninstalling plugin for ${osName} is not supported. The plugin will not be installed."
    }

}


