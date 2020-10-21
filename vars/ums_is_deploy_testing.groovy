def call(
    String type,
    String branch
) {
    String folder = 'image-service'
    String repoName = 'ssh://git@gitlab.cts.luxoft.com:30122/dm1tryG/universe-api.git'
    String compose = "deploy/${type}/docker-compose.yml"
    node('UMS') {
        // stop container
        dir("${type}/${folder}") {
            try {
                sh "sudo docker-compose -f ${compose} stop";
                sh "sudo docker-compose -f ${compose} rm --force"
            } catch(Exception ex) {
                println("Catching the exception");
            }

            dir("../../../") {
                sh "sudo rm -rf ${folder}/"        
            }
            
            // clone
            checkOutBranchOrScm(
                branchName: branch,
                repoName:repoName
            )

            stage('Build') {
                sh "sudo docker-compose -f ${compose} build"
            }
            stage('Up') {
                sh "sudo docker-compose -f ${compose} up -d"
            }
        }
    }
}