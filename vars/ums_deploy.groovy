def call(String type, String main_branch){
    node('UMS') {
        dir("${type}/universe") {
            stage('preBuild') {
                sh "docker-compose -f docker-compose.${type}.yml stop"
                checkOutBranchOrScm(main_branch, 'https://gitlab.cts.luxoft.com/dm1tryG/universe.git', false, false, true, 'radeonprorender-gitlab', false)
            }
            stage('Build') {
                sh "docker-compose -f docker-compose.${type}.yml build"
            }
            stage('Up') {
                sh "docker-compose -f docker-compose.${type}.yml up -d"
            }
        }
    }
}
