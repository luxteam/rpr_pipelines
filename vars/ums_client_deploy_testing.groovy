def call(
    String type,
    String branch
) {
    String folder = 'universe-client'
    String repoName = 'https://gitlab.cts.luxoft.com/dm1tryG/universe-client.git'
    node('UMS') {
        // stop container
        dir("${type}/${folder}") {
            try {
                sh "sudo docker-compose -f docker-compose.${type}.yml stop";
            } catch(Exception ex) {
                println("Catching the exception");
            }
        }

        dir("../") {
            sh "sudo rm -rf ${folder}/"        
        }

        
        checkOutBranchOrScm(branch, repoName, false, false, true, 'radeonprorender-gitlab', false)

        stage('Build') {
            sh "sudo docker-compose -f docker-compose${type}.yml build"
        }
        stage('Up') {
            sh "sudo docker-compose -f docker-compose${type}.yml up -d"
        }
    }
}