import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def executeConvert(osName, gpuName, attemptNum, Map options) {
	currentBuild.result = 'SUCCESS'
	
	String tool = options['Tool'].split(':')[0].trim()
	String version = options['Tool'].split(':')[1].trim()
	String scene_name = options['sceneName']
	String scene_user = options['sceneUser']
	String fail_reason = "Unknown"
	
	switch(osName) {
		case 'Windows':
			try {
				// Send attempt number
				render_service_send_render_attempt(attemptNum, options.id, options.django_url)
				// Clean up work folder
				cleanWS(osName)
				// Download render service scripts
				try {
					render_service_send_render_status("Downloading scripts and install requirements", options.id, options.django_url)
					checkOutBranchOrScm(options['scripts_branch'], 'git@github.com:luxteam/render_service_scripts.git')
					dir(".\\install"){
						bat '''
						install_pylibs.bat
						'''
					}
				} catch(FlowInterruptedException e) {
					throw e
				} catch(e) {
					fail_reason = "Downloading scripts failed"
					throw e
				}

				// download and install plugin
				if (options["PluginLink"]) {
					render_service_install_plugin(options["PluginLink"], tool, version, options.id, options.django_url)
				}

				// download scene, check if it is already downloaded
				try {
					// initialize directory RenderServiceStorage
					bat """
						if not exist "..\\..\\RenderServiceStorage" mkdir "..\\..\\RenderServiceStorage"
					"""
					render_service_send_render_status("Downloading scene", options.id, options.django_url)
					def exists = fileExists "..\\..\\RenderServiceStorage\\${scene_user}\\${scene_name}"
					if (exists) {
						print("Scene is copying from Render Service Storage on this PC")
						bat """
							copy "..\\..\\RenderServiceStorage\\${scene_user}\\${scene_name}" "${scene_name}"
						"""
					} else {
						withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
							bat """
								curl -o "${scene_name}" -u %DJANGO_USER%:%DJANGO_PASSWORD% "${options.Scene}"
							"""
						}

						bat """
							if not exist "..\\..\\RenderServiceStorage\\${scene_user}\\" mkdir "..\\..\\RenderServiceStorage\\${scene_user}"
							copy "${scene_name}" "..\\..\\RenderServiceStorage\\${scene_user}"
							copy "${scene_name}" "..\\..\\RenderServiceStorage\\${scene_user}\\${scene_name}"
						"""
					}
				} catch(FlowInterruptedException e) {
					throw e
				} catch(e) {
					fail_reason = "Downloading scene failed"
					throw e
				}
			
				
				try {
					switch(tool) {
						case 'Maya (Redshift)':

							dir("RS2RPRConvertTool"){
								checkOutBranchOrScm(options['convert_branch'], 'git@github.com:luxteam/RS2RPRConvertTool.git')
							}
							// copy necessary scripts for render
							bat """
								copy "render_service_scripts\\launch_maya_redshift_conversion.py" "."
								copy "render_service_scripts\\conversion_redshift_render.py" "."
								copy "render_service_scripts\\conversion_rpr_render.py" "."
							"""
							// Launch render
							withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
								print(python3("launch_maya_redshift_conversion.py --tool ${version} --django_ip \"${options.django_url}/\" --id ${id} --scene_name \"${scene_name}\" --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout ${options.timeout} "))
							}
							break;
				
						case 'Maya (Vray)':

							dir("Vray2RPRConvertTool"){
								checkOutBranchOrScm(options['convert_branch'], 'git@github.com:luxteam/Vray2RPRConvertTool-Maya.git')
							}
							// copy necessary scripts for render
							bat """
								copy "render_service_scripts\\launch_maya_vray_conversion.py" "."
								copy "render_service_scripts\\conversion_vray_render.py" "."
								copy "render_service_scripts\\conversion_rpr_render.py" "."
							"""
							// Launch render
							withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
								print(python3("launch_maya_vray_conversion.py --tool ${version} --django_ip \"${options.django_url}/\" --id ${id} --scene_name \"${scene_name}\" --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout ${options.timeout} "))
							}
							break;
					}
				} catch(FlowInterruptedException e) {
					throw e
				} catch (e) {
					// if status == failure then copy full path and send to slack
					bat """
						mkdir "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
						copy "*" "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
					"""
					// if exit code is greater than 0 or received any other exception -> script finished with unexpected exception
					String[] messageParts = e.getMessage().split(" ")
					Integer exitCode = messageParts[messageParts.length - 1].isInteger() ? messageParts[messageParts.length - 1].toInteger() : null
					if (exitCode == null || exitCode > 0) {
						fail_reason = "Unknown" 
					} else {
						fail_reason = "Expected exception"
					}
					throw e
				}
				
			} catch(FlowInterruptedException e) {
				throw e
			} catch(e) {
				println(e.toString())
				println(e.getMessage())
				println(e.getStackTrace())
				print e
				if (fail_reason != "Expected exception") {
					render_service_send_render_status('Failure', options.id, options.django_url, currentBuild.number, fail_reason)
				}
				throw e
			}
	}
}


def main(String PCs, Map options) {
	
	timestamps {
		String PRJ_PATH="${options.PRJ_ROOT}/${options.PRJ_NAME}"
		String JOB_PATH="${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}".replace('%2F', '_')
		options['PRJ_PATH']="${PRJ_PATH}"
		options['JOB_PATH']="${JOB_PATH}"

		boolean PRODUCTION = true

		if (PRODUCTION) {
			options['django_url'] = "https://render.cis.luxoft.com/convert/jenkins/"
			options['plugin_storage'] = "https://render.cis.luxoft.com/media/plugins/"
			options['scripts_branch'] = "master"
			options['convert_branch'] = "master"
		} else {
			options['django_url'] = "https://testrender.cis.luxoft.com/convert/jenkins/"
			options['plugin_storage'] = "https://testrender.cis.luxoft.com/media/plugins/"
			options['scripts_branch'] = "master"
			options['convert_branch'] = "master"
		}

		List tokens = PCs.tokenize(':')
		String osName = tokens.get(0)
		String deviceName = tokens.get(1)

		String renderDevice = ""
		if (deviceName == "ANY") {
			// Maya (Plugin):Version -> PluginMaya
			String[] toolParts = options['Tool'].split(':')[0].trim().split(' \\(')
			String tool = toolParts[1].replace(')', '') + toolParts[0]
			renderDevice = tool
		} else {
			renderDevice = "gpu${deviceName}"
		}
	
		startConvert(osName, deviceName, renderDevice, options)
	}
	
}

def startConvert(osName, deviceName, renderDevice, options) {
	node("RenderService || Windows && Builder") {
		stage("Send Build Number") {
			render_service_send_build_number(currentBuild.number, options.id, options.django_url)
		}
	}

	def labels = "${osName} && RenderService && ${renderDevice}"
	def nodesCount = getNodesCount(labels)
	boolean successfullyDone = false

	print("Max attempts: ${options.maxAttempts}")
	def maxAttempts = "${options.maxAttempts}".toInteger()
	def testTasks = [:]
	def currentLabels = labels
	for (int attemptNum = 1; attemptNum <= maxAttempts && attemptNum <= nodesCount; attemptNum++) {
		def currentNodeName = ""

		echo "Scheduling Convert ${osName}:${deviceName}. Attempt #${attemptNum}"
		testTasks["Convert-${osName}-${deviceName}"] = {
			node(currentLabels) {
				stage("Conversion") {
					timeout(time: 65, unit: 'MINUTES') {
						ws("WS/${options.PRJ_NAME}_Conversion") {
							currentNodeName =  "${env.NODE_NAME}"
							try {
								executeConvert(osName, deviceName, attemptNum, options)
								successfullyDone = true
							} catch(FlowInterruptedException | InterruptedException e) {
								throw e
							} catch (e) {
								//Exclude failed node name
								currentLabels = currentLabels + " && !" + currentNodeName
								println(currentLabels)
							}
							if (successfullyDone || attemptNum == maxAttempts || attemptNum == nodesCount) {
								// Process finished - set attempt number as 0
								render_service_send_render_attempt(0, options.id, options.django_url)
							}
						}
					}
				}
			}
		}

		parallel testTasks	
		
		if (successfullyDone) {
			break
		}
	}

	if (!successfullyDone) {
		if (nodesCount == 0) {
			// master machine can't access necessary nodes. Run notification script on any machine
			node("RenderService || Windows && Builder") {
				stage("Notify about failure") {
					render_service_send_render_status('Failure', options.id, options.django_url, currentBuild.number, 'No machine with specified configuration')
				}
			}
		}
		throw new Exception("Job was failed by all used nodes!")
	} else {
		currentBuild.result = 'SUCCESS'
	}
}

def getNodesCount(labels) {
	def nodes = nodesByLabel label: labels, offline: false
	def nodesCount = nodes.size()

	return nodesCount
}
	
def call(String Tool = '',
	String Scene = '',
	String PluginLink = '',  
	String PCs = '',
	String id = '',
	String sceneName = '',
	String sceneUser = '',
	String maxAttempts = '',
	String timeout = ''
	) {
	String PRJ_ROOT='RenderServiceConvertJob'
	String PRJ_NAME='RenderServiceConvertJob'  
	main(PCs,[
		enableNotifications:false,
		PRJ_NAME:PRJ_NAME,
		PRJ_ROOT:PRJ_ROOT,
		Tool:Tool,
		Scene:Scene,
		PluginLink:PluginLink,
		id:id,
		sceneName:sceneName,
		sceneUser:sceneUser,
		maxAttempts:maxAttempts,
		timeout:timeout
		])
	}
