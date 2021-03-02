/**
 * Function for validate specified plugin (checks it's size and integrity of zipped plugins)
 *
 * @param osName - name of OS on which plugin is being validated
 * @param pluginPath - plugin name
 * @param options - Map of options
 */
Boolean call(String osName, String pluginPath, Map options) {

    def pluginFile = findFiles(glob: pluginPath)[0]
    // check that size of plugin isn't less than 10Mb
    if (pluginFile.length < 10 * 1024 * 1024) {
        def exception = new ExpectedExceptionWrapper(NotificationConfiguration.INVALID_PLUGIN_SIZE, new Exception(NotificationConfiguration.INVALID_PLUGIN_SIZE))
        exception.abortCurrentOS = true
        throw exception
    }

    // validate zipped plugins
    if (pluginFile.name.endsWith(".zip")) {
        try {
            unzip(zipFile: pluginPath, dir: "unpackedPlugin", quiet: true)
            dir("unpackedPlugin") {
                deleteDir()
            }
        } catch (e) {
            def exception = new ExpectedExceptionWrapper(NotificationConfiguration.CORRUPTED_ZIP_PLUGIN, e)
            exception.abortCurrentOS = true
            throw exception
        }
    }

    // validate msi plugins
    if (pluginFile.name.endsWith(".msi")) {
        try {
            bat """
                echo import msilib >> getMsiProductCode.py
                echo db = msilib.OpenDatabase(r'${pluginPath}', msilib.MSIDBOPEN_READONLY) >> getMsiProductCode.py
                echo view = db.OpenView("SELECT Value FROM Property WHERE Property='ProductCode'") >> getMsiProductCode.py
                echo view.Execute(None) >> getMsiProductCode.py
                echo print(view.Fetch().GetString(1)) >> getMsiProductCode.py
            """            
            String productCode = python3("getMsiProductCode.py").split('\r\n')[2].trim()[1..-2]

            println("[INFO] Product code of pre built plugin: ${productCode}")
        } catch (e) {
            def exception = new ExpectedExceptionWrapper(NotificationConfiguration.CORRUPTED_MSI_PLUGIN, e)
            exception.abortCurrentOS = true
            throw exception
        }
    }
}