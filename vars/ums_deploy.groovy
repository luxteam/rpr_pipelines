def call(String type, String main_branch){
    node('UMS') {
        dir("${type}/universe") {
            stage('pull') {
                checkOutBranchOrScm(main_branch, 'https://gitlab.cts.luxoft.com/dm1tryG/universe.git', false, false, true, 'radeonprorender-gitlab', false)
            }
            stage('build') {
                sh "docker-compose -f docker-compose.${type}.yml build"
            }
            stage('up') {
                sh "docker-compose -f docker-compose.${type}.yml up -d"
            }
        }
    }
}
