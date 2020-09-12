def call(String type, String main_branch){
    node('UMS') {
        dir("${type}/universe") {
            stage('preBuild') {
                sh "sudo docker-compose -f docker-compose.${type}.yml stop"
                checkOutBranchOrScm(main_branch, 'https://gitlab.cts.luxoft.com/dm1tryG/universe.git', false, false, true, 'radeonprorender-gitlab', false)
            }
            stage('Build') {
                sh "sudo docker-compose -f docker-compose.${type}.yml build"
            }
            stage('Up') {
                sh "sudo docker-compose -f docker-compose.${type}.yml up -d"
            }
        }
    }
}
