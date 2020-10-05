def call(
    String type,
    String branch
) {
    String folder = 'universe-client'
    String repoName = 'https://gitlab.cts.luxoft.com/dm1tryG/universe-client.git'
    String compose = "docker-compose${type}.yml"
    node('UMS') {
        // stop container
        dir("${type}/${folder}") {
            try {
                sh "sudo docker-compose -f ${compose} stop";
            } catch(Exception ex) {
                println("Catching the exception");
            }

            dir("../") {
                sh "sudo rm -rf ${folder}/"        
            }

            
            checkOutBranchOrScm(branch, repoName, false, false, true, 'radeonprorender-gitlab', false)

            stage('Build') {
                sh "sudo docker-compose -f ${compose} build"
            }
            stage('Up') {
                sh "sudo docker-compose -f ${compose} up -d"
            }
        }
    }
}