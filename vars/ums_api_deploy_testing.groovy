def call(
    String type,
    String branch
) {
    String folder = 'universe-api'
    String repoName = 'ssh://git@gitlab.cts.luxoft.com:30122/dm1tryG/universe-api.git'
    String compose = "${folder}/deploy/${type}/docker-compose.yml"
    node('UMS') {
        // stop container
        dir("${type}") {
            try {
                sh "sudo docker-compose -f ${compose} stop";
                sh "sudo docker-compose -f ${compose} rm --force"
            } catch(Exception ex) {
                println("Catching the exception");
            }

            sh "sudo rm -rf ${folder}"        
            
            dir ("${folder}") {
                checkOutBranchOrScm(
                    branchName: branch,
                    repoName:repoName
                )
            }

            stage('Build') {
                sh "sudo docker-compose -f ${compose} build"
            }
            stage('Up') {
                sh "sudo docker-compose -f ${compose} up -d"
            }
        }
    }
}