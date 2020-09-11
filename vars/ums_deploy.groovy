def call(String type, String main_branch){
    node('UMS') {
        stage('build') {
                dir("${type}/universe") {
                    checkOutBranchOrScm(main_branch, 'https://gitlab.cts.luxoft.com/dm1tryG/universe.git', false, false, true, 'radeonprorender-gitlab', false)
                    sh "docker-compose -f docker-compose.${type}.yml build"
                    sh "docker-compose -f docker-compose.${type}.yml up -d"
                }
        }
    }
}
