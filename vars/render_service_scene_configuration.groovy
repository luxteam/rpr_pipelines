import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def executeConfiguration(osName, attemptNum, Map options) {
	currentBuild.result = 'SUCCESS'

	String tool = options['Tool']
	String scene_name = options['sceneName']
	String scene_user = options['sceneUser']
	String fail_reason = "Unknown"

	switch(osName) {
		case 'Windows':
			try {
				// Clean up work folder
				cleanWS(osName)
				// Download render service scripts
				try {
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

				// download scene, check if it is already downloaded
				try {
					// initialize directory RenderServiceStorage
					bat """
						if not exist "..\\..\\RenderServiceStorage" mkdir "..\\..\\RenderServiceStorage"
					"""
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
						case 'Blender':
							// copy necessary scripts for render and start read process
							bat """
								copy "render_service_scripts\\scene_scanning\\read_blender_configuration.py" "."
								copy "render_service_scripts\\scene_scanning\\write_blender_configuration.py" "."
								copy "render_service_scripts\\scene_scanning\\launch_blender.py" "."
							"""
							// Launch render
							withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
								python3("launch_blender.py --tool \"2.83\" --django_ip \"${options.django_url}/\" --scene_name \"${scene_name}\" --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD% --action \"${options.Action}\" --options \"${options.Options}\" ")
							}
							break;
					}

				} catch(FlowInterruptedException e) {
					throw e
				} catch(e) {
					bat """
						mkdir "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
						copy "*" "..\\..\\RenderServiceStorage\\failed_${scene_name}_${id}_${currentBuild.number}"
					"""
					fail_reason = "Unknown" 
					throw e
				}
			} catch(FlowInterruptedException e) {
				throw e
			} catch(e) {
				println(e.toString())
				println(e.getMessage())
				println(e.getStackTrace())
				print e
				println(fail_reason)
				throw e
			}
	}
}

def main(Map options) {

	timestamps {
		String PRJ_PATH="${options.PRJ_ROOT}/${options.PRJ_NAME}"
		String JOB_PATH="${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}".replace('%2F', '_')
		options['PRJ_PATH']="${PRJ_PATH}"
		options['JOB_PATH']="${JOB_PATH}"

		options['django_url'] = "https://render.cis.luxoft.com/project/jenkins/"
		options['scripts_branch'] = "master"

		String osName = 'Windows'
		
		startConfiguration(osName, options)
	}    
	
}

def startConfiguration(osName, options) {
	node("RenderService || Windows && Builder") {
		stage("Send Build Number") {
			render_service_send_build_number(currentBuild.number, options.id, options.django_url)
		}
	}
	
	def labels = "${osName} && RenderService"
	def nodesCount = getNodesCount(labels)
	boolean successfullyDone = false

	print("Max attempts: ${options.maxAttempts}")
	def maxAttempts = "${options.maxAttempts}".toInteger()
	def currentLabels = labels
	for (int attemptNum = 1; attemptNum <= maxAttempts && attemptNum <= nodesCount; attemptNum++) {
		def currentNodeName = ""

		echo "Scheduling Configuration ${osName}. Attempt #${attemptNum}"
		node(currentLabels) {
			stage("Configuration") {
				timeout(time: 15, unit: 'MINUTES') {
					ws("WS/${options.PRJ_NAME}_Configuration") {
						currentNodeName = "${env.NODE_NAME}"
						try {
							executeConfiguration(osName, attemptNum, options)
							successfullyDone = true
						} catch(FlowInterruptedException e) {
							throw e
						} catch (e) {
							print e
							//Exclude failed node name
							currentLabels = currentLabels + " && !" + currentNodeName
							println("[INFO] Updated labels: ${currentLabels}")
						}
					}
				}
			}
		}
		
		if (successfullyDone) {
			break
		}
	}

	if (!successfullyDone) {
		node("RenderService") {
			render_service_send_render_status('Failure', options.id, options.django_url)
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
	
def call(String id = '',
	String Tool = '',
	String Scene = '',
	String sceneName = '',
	String sceneUser = '',
	String maxAttempts = '',
	String Action = '',
	String Options = ''
	) {
	String PRJ_ROOT='RenderServiceSceneConfiguration'
	String PRJ_NAME='RenderServiceSceneConfiguration' 

	Options = Options.replace('\"', '\"\"')

	main([
		id:id,
		PRJ_NAME:PRJ_NAME,
		PRJ_ROOT:PRJ_ROOT,
		Tool:Tool,
		Scene:Scene,
		sceneName:sceneName,
		sceneUser:sceneUser,
		maxAttempts:maxAttempts,
		Action:Action,
		Options:Options
		])
	}
