def call(Map params) {
    def osName = params["osName"]
    def options = params["options"]
    def extension = params["extension"]

    switch(osName) {
        case "Windows":
            String name = options.pluginWinSha ?: options.commitSHA
            extension = extension ?: "msi"
            utils.removeFile(this, osName, "${CIS_TOOLS}\\..\\PluginsBinaries\\${name}.${extension}")
            utils.removeFile(this, osName, "${CIS_TOOLS}\\..\\PluginsBinaries\\${name}_${osName}.${extension}")
            break
        case "OSX":
            String name = options.pluginOSXSha ?: options.commitSHA
            extension = extension ?: "dmg"
            utils.removeFile(this, osName, "${CIS_TOOLS}/../PluginsBinaries/${name}.${extension}")
            utils.removeFile(this, osName, "${CIS_TOOLS}/../PluginsBinaries/${name}_${osName}.${extension}")
            break
        // Linux
        default:
            String name = options.pluginUbuntuSha ?: options.commitSHA
            extension = extension ?: "run"
            utils.removeFile(this, osName, "${CIS_TOOLS}/../PluginsBinaries/${name}.${extension}")
            utils.removeFile(this, osName, "${CIS_TOOLS}/../PluginsBinaries/${name}_${osName}.${extension}")
    }
}