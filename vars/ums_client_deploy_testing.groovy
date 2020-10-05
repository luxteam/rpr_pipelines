def call(
    String type,
    String branch
) {
    String folder = 'universe-client'
    String repoName = 'https://gitlab.cts.luxoft.com/dm1tryG/universe-client.git'

    node('UMS') {
        // stop container
        dir("${type}/${folder}/deploy/${type}") {
            try {
                sh "sudo docker-compose stop";
                sh "sudo docker-compose rm --force"
            } catch(Exception ex) {
                println("Catching the exception");
            }

            dir("../../../") {
                sh "sudo rm -rf ${folder}/"        
            }

            checkOutBranchOrScm(branch, repoName, false, false, true, 'radeonprorender-gitlab', false)

            stage('Build') {
                sh "sudo docker-compose build"
            }
            stage('Up') {
                sh "sudo docker-compose up -d"
            }
        }
    }
}