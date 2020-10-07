def call(String osName, Map options)
{
    switch(osName)
    {
        case 'Windows':
            receiveFiles("bin_storage/RadeonProMaterialLibrary.msi", "${CIS_TOOLS}\\..\\TestResources/")

            echo '[INFO] Reinstalling Material Library'
            uninstallMSI("Radeon%Material%", options.stageName, options.currentTry)
            installMSI("${CIS_TOOLS}/../PluginsBinaries/RadeonProMaterialLibrary.msi", options.stageName)
            break;

        case 'OSX':
            println "not supported"
            break;

        default:
            receiveFiles("bin_storage/RadeonProRenderMaterialLibraryInstaller_2.0.run", "${CIS_TOOLS}/../TestResources/")

            echo '[INFO] Installing Material Library'
            sh """
                #!/bin/bash
                ${CIS_TOOLS}/../TestResources/RadeonProRenderMaterialLibraryInstaller_2.0.run --nox11 --just-do-it >> \"${options.stageName}_${options.currentTry}.matlib.install.log\" 2>&1
            """
    }
}