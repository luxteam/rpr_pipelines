import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def call(String pluginLink, String tool, String version, String id, String django_url) {
	// get tool name without plugin name
	String toolName = tool.split(' ')[0].trim()
	Map installationOptions = [
		'isPreBuilt': true,
		'stageName': 'RenderServiceRender',
		'customBuildLinkWindows': pluginLink
	]

	try {
		render_service_send_render_status("Downloading plugin", id, django_url)
		clearBinariesWin()

		downloadPlugin('Windows', toolName, installationOptions, 'renderServiceCredentials')
		win_addon_name = installationOptions.pluginWinSha
	} catch(FlowInterruptedException e) {
		throw e
	} catch(e) {
		fail_reason = "Plugin downloading failed"
		throw e
	}

	try {
		render_service_send_render_status("Installing plugin", id, django_url)
		Boolean installationStatus = null

		switch(toolName) {
			case 'Blender':
				bat """
					IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
					move RadeonProRender*.zip "${CIS_TOOLS}\\..\\PluginsBinaries\\${win_addon_name}.zip"
				"""

				installationStatus = installBlenderAddon('Windows', version, installationOptions)
				break;

			default:
				bat """
					IF NOT EXIST "${CIS_TOOLS}\\..\\PluginsBinaries" mkdir "${CIS_TOOLS}\\..\\PluginsBinaries"
					move RadeonProRender*.msi "${CIS_TOOLS}\\..\\PluginsBinaries\\${win_addon_name}.msi"
				"""

				installationStatus = installMSIPlugin('Windows', toolName, installationOptions)
				break;
		}

		print "[INFO] Install function return ${installationStatus}"
	} catch(FlowInterruptedException e) {
		throw e
	} catch(e) {
		fail_reason = "Plugin installation failed"
		throw e
	}
}
