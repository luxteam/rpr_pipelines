def main(Map options) {
	String version
	if (options.universeBranch == "origin/master") {
		version = "master"
	} else {
		version = "develop"
	}

	String dockerComposeFile
	String versionPostfix
	if (version == 'master') {
		dockerComposeFile = "docker-compose.yml"
		versionPostfix = ""
	} else {
		dockerComposeFile = "docker-compose.dev.yml"
		versionPostfix = "_dev"
	}

	node('Ubuntu18 && RBSBuilder') {
		cleanWS("Linux")
		try {
			println("[INFO] Try to stop old RBS compose stack")
			sshagent(credentials : ['FrontendMachineCredentials']) {
				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'FrontendMachineCredentials', usernameVariable: 'USER', passwordVariable: 'PASSWORD']]) {
					sh """
						ssh ${options.user}@${options.frontendIp} ${options.RBSServicesRootRelative}/${version}/universe${versionPostfix}/docker-management/stop_pipeline.sh ${options.RBSServicesRootRelative}/${version}/universe${versionPostfix}/${dockerComposeFile}
						ssh ${options.user}@${options.frontendIp} ${options.RBSServicesRootRelative}/${version}/universe${versionPostfix}/docker-management/remove_pipeline.sh ${options.RBSServicesRootRelative}/${version}/universe${versionPostfix}/${dockerComposeFile}
						ssh ${options.user}@${options.frontendIp} "echo ${PASSWORD} | sudo -S rm -rf ${options.RBSServicesRootRelative}/${version}"
					"""
				}
			}
			println("[INFO] Old RBS compose stack found and stopped")
		} catch (Exception e) {
			println("[INFO] Old RBS compose stack doesn't exist")
		}

		dir("universe${versionPostfix}") {
			checkOutBranchOrScm(options['universeBranch'], 'https://gitlab.cts.luxoft.com/dm1tryG/universe.git', false, null, null, false, true, 'radeonprorender-gitlab', false)
		}

		sshagent(credentials : ['FrontendMachineCredentials']) {
			sh """
				ssh ${options.user}@${options.frontendIp} mkdir -p ${options.RBSServicesRoot}/${version}
				scp -r ./universe${versionPostfix} ${options.user}@${options.frontendIp}:${options.RBSServicesRoot}/${version}

				ssh ${options.user}@${options.frontendIp} ${options.RBSServicesRoot}/${version}/universe${versionPostfix}/docker-management/build_pipeline.sh ${options.RBSServicesRoot}/${version}/universe${versionPostfix}/${dockerComposeFile}
				ssh ${options.user}@${options.frontendIp} ${options.RBSServicesRoot}/${version}/universe${versionPostfix}/docker-management/up_pipeline.sh ${options.RBSServicesRoot}/${version}/universe${versionPostfix}/${dockerComposeFile}
			"""

			// run tests
			if (version=="develop") {
    		    sh """
			        ssh ${options.user}@${options.frontendIp} 'chmod +x ${options.RBSServicesRoot}/${version}/universe${versionPostfix}/docker-management/run_tests.sh'
				    ssh ${options.user}@${options.frontendIp} ${options.RBSServicesRoot}/${version}/universe${versionPostfix}/docker-management/run_tests.sh	    
    			"""    
		    }
		}
	}
}

def call(
	String universeBranch = ''
	) {

	String RBSServicesRoot = "/home/admin/Server/RPRServers/rbs_auto_deploy"
	String RBSServicesRootRelative = "./Server/RPRServers/rbs_auto_deploy"
	String user = "admin"
	String frontendIp = "172.30.23.112"

	main([
		universeBranch:universeBranch.toLowerCase(),
		RBSServicesRoot:RBSServicesRoot,
		RBSServicesRootRelative:RBSServicesRootRelative,
		user:user,
		frontendIp:frontendIp
		])
	}