def call(String type, String main_branch){
    node('UMS') {
        dir("${type}/universe") {
            stage('preBuild') {
                try {
                    sh "sudo docker-compose -f docker-compose.${type}.yml stop"
                    sh "sudo docker-compose -f docker-compose.${type}.yml rm --force"
                  } catch(Exception ex) {
                    println("Catching the exception");
                  }
                dir("../") {
                    sh "sudo rm -rf universe/"        
                }
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
