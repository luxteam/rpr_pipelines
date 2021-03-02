import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException

def executeBuildViewer(osName, gpuName, attemptNum, isLastAttempt, Map options) {
	currentBuild.result = 'SUCCESS'
   
	String scene_name = options['scene_name']
	String fail_reason = "Unknown"
	println("Options: ${options}")
	
	try {
		// Send attempt number
		render_service_send_render_attempt(attemptNum, options.id, options.django_url)
		// Clean up work folder
		cleanWS(osName)

		// Download render service scripts
		try {
			render_service_send_render_status("Downloading scripts and install requirements", options.id, options.django_url)
			checkOutBranchOrScm(options['scripts_branch'], 'git@github.com:luxteam/render_service_scripts.git')
			dir("install"){
				bat '''
				install_pylibs.bat
				'''
			}

			bat """
				copy "render_service_scripts\\send_viewer_results.py" "."
			"""	
		} catch(FlowInterruptedException e) {
			throw e
		} catch(e) {
			fail_reason = "Downloading scripts failed"
			throw e
		}

		dir("viewer_dir") {
			try {
				render_service_send_render_status("Downloading viewer", options.id, options.django_url)
				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'jenkinsCredentials', usernameVariable: 'JENKINS_USERNAME', passwordVariable: 'JENKINS_PASSWORD']]) {
					bat """
						curl --retry 3 -L -O -J -u %JENKINS_USERNAME%:%JENKINS_PASSWORD% "${options.viewer_url}"
					"""
				}
			} catch(FlowInterruptedException e) {
				throw e
			} catch(e) {
				fail_reason = "Downloading viewer failed"
				throw e
			}

			try {
				render_service_send_render_status("Downloading scene", options.id, options.django_url)
				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
					bat """
						curl --retry 3 -o "${scene_name}" -u %DJANGO_USER%:%DJANGO_PASSWORD% "${options.scene_link}"
					"""
				}
			} catch(FlowInterruptedException e) {
				throw e
			} catch(e) {
				fail_reason = "Downloading scene failed"
				throw e
			}
		}

		try {
			bat """
				copy "render_service_scripts\\configure_viewer.py" "."
			"""		

			render_service_send_render_status("Building RPRViewer Package", options.id, options.django_url)
			withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
				python3("configure_viewer.py --version ${options.viewer_version} --id ${id} --django_ip \"${options.django_url}/\" --width ${options.width} --height ${options.height} --engine ${options.engine} --iterations ${options.iterations} --scene_name \"${options.scene_name}\" --login %DJANGO_USER% --password %DJANGO_PASSWORD% --timeout ${options.timeout} ").split('\r\n')[-1].trim()
				print("Preparing results")
			}
			render_service_send_render_status("Completed", options.id, options.django_url)

			withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
				print(python3("send_viewer_results.py --django_ip \"${options.django_url}\" --status \"Success\" --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD%"))
			}
		} catch(FlowInterruptedException e) {
			throw e
		} catch(e) {
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
		} else if (isLastAttempt) {
			withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'renderServiceCredentials', usernameVariable: 'DJANGO_USER', passwordVariable: 'DJANGO_PASSWORD']]) {
				print(python3("send_viewer_results.py --django_ip \"${options.django_url}\" --status \"Failure\" --id ${id} --login %DJANGO_USER% --password %DJANGO_PASSWORD%"))
			}
		}
		throw e
	}
}



def main(String platforms, Map options) {
	 
	timestamps {
		String PRJ_PATH="${options.PRJ_ROOT}/${options.PRJ_NAME}"
		String JOB_PATH="${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}".replace('%2F', '_')
		options['PRJ_PATH']="${PRJ_PATH}"
		options['JOB_PATH']="${JOB_PATH}"

		boolean PRODUCTION = true

		if (PRODUCTION) {
			withCredentials([string(credentialsId: 'prodRSURL', variable: 'PROD_RS_URL')])
			{
				options['django_url'] = "${PROD_RS_URL}/viewer/jenkins/"
				options['plugin_storage'] = "${PROD_RS_URL}/viewer/plugins/"
				options['scripts_branch'] = "master"
			}
		} else {
			withCredentials([string(credentialsId: 'devRSURL', variable: 'DEV_RS_URL')])
			{
				options['django_url'] = "${DEV_RS_URL}/viewer/jenkins/"
				options['plugin_storage'] = "${DEV_RS_URL}/viewer/plugins/"
				options['scripts_branch'] = "develop"
			}
		}

		List tokens = platforms.tokenize(':')
		String osName = tokens.get(0)
		String deviceName = tokens.get(1)

		String renderDevice = ""
		if (deviceName == "ANY") {
			renderDevice = "Viewer"
		} else {
			renderDevice = "gpu${deviceName}"
		}

		startRender(osName, deviceName, renderDevice, options)
	} 

}

def startRender(osName, deviceName, renderDevice, options) {
	node("RenderService || Windows && Builder") {
		stage("Send Build Number") {
			render_service_send_build_number(currentBuild.number, options.id, options.django_url)
		}
	}

	def labels = "${osName} && RenderService && ${renderDevice}"
	def nodesCount = getNodesCount(labels)
	boolean successfullyDone = false

	print("Max attempts: ${options.max_attempts}")
	def maxAttempts = "${options.max_attempts}".toInteger()
	def testTasks = [:]
	def currentLabels = labels
	for (int attemptNum = 1; attemptNum <= maxAttempts && attemptNum <= nodesCount; attemptNum++) {
		def currentNodeName = ""

		println("Scheduling Build Viewer ${osName}:${deviceName}. Attempt #${attemptNum}")
		testTasks["Test-${osName}-${deviceName}"] = {
			node(labels){
				stage("BuildViewer-${osName}-${deviceName}"){
					timeout(time: 60, unit: 'MINUTES'){
						ws("WS/${options.PRJ_NAME}") {
							currentNodeName =  "${env.NODE_NAME}"
							try {
								boolean isLastAttempt = attemptNum == maxAttempts || attemptNum == nodesCount
								executeBuildViewer(osName, deviceName, attemptNum, isLastAttempt, options)
								successfullyDone = true
							} catch(FlowInterruptedException e) {
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
	
def call(
	String scene_link = '',  
	String platforms = '',
	String viewer_url = '',
	String id = '',
	String width = '',
	String height = '',
	String engine = '',
	String iterations = '',
	String scene_name = '',
	String max_attempts = '',
	String timeout
	) {
	String PRJ_ROOT='RenderServiceViewerJob'
	String PRJ_NAME='RenderServiceViewerJob'  

	// protocol://domain/job/job_name/job/branch/version/artifacts/actifact_name
	def viewer_version = viewer_url.split('/')[5]

	main(platforms,[
		enableNotifications:false,
		PRJ_NAME:PRJ_NAME,
		PRJ_ROOT:PRJ_ROOT,
		scene_link:scene_link,
		viewer_url:viewer_url,
		viewer_version:viewer_version,
		id:id,
		width:width,
		height:height,
		engine:engine,
		iterations:iterations,
		scene_name:scene_name,
		max_attempts:max_attempts,
		timeout:timeout
		])
	}

