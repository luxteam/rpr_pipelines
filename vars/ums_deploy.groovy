def call(String type, String main_branch){
    String user = "admin"
    String frontendIp = "172.30.23.112"
    String productionContainerName = "universe_mongo_db_1"

    if (type == 'devrc') {
        node('Ubuntu18 && RBSBuilder') {
            try {
                println("[INFO] Try to dump production database")
                sshagent(credentials : ['FrontendMachineCredentials']) {
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'FrontendMachineCredentials', usernameVariable: 'USER', passwordVariable: 'PASSWORD']]) {
                        sh """
                            ssh ${user}@${frontendIp} sudo docker exec -it ${productionContainerName} mongodump --archive=dump_devrc
                            ssh ${user}@${frontendIp} sudo docker cp ${productionContainerName}:dump_devrc .
                            ssh ${user}@${frontendIp} sudo docker exec -it ${productionContainerName} rm dump_devrc
                        """
                    }
                }
            } catch (Exception e) {
                println("[INFO] can't dump")
            }
        }
    }
    
    
    node('UMS') {
        dir("${type}/universe/${type}") {
            stage('preBuild') {
                try {
                    sh "sudo docker-compose stop"
                    sh "sudo docker-compose rm --force"
                  } catch(Exception ex) {
                    println("Catching the exception");
                  }
                dir("../../") {
                    sh "sudo rm -rf universe/"        
                }

                dir("../") {
                    checkOutBranchOrScm(main_branch, 'https://gitlab.cts.luxoft.com/dm1tryG/universe.git', false, false, true, 'radeonprorender-gitlab', false)
                }
            }
            stage('Build') {
                sh "sudo docker-compose build"
            }
            stage('Up') {
                sh "sudo docker-compose up -d"
            }
            if (type == 'devrc') {
                stage('Restore database') {
                    try {
                        sh """
                            sudo scp ${user}@${frontendIp}:dump_devrc /home/radmin/
                            sudo docker cp dump_devrc ${productionContainerName}:dump_devrc
                            sudo docker exec -it ${productionContainerName} mongorestore --archive=dump_devrc
                            sudo docker exec -it ${productionContainerName} rm dump_devrc
                        """
                    } catch (Exception e) {
                        println('not restored')
                    }
                    

                }
            }
        }
    }

    if (type == 'devrc') {
        node('Ubuntu18 && RBSBuilder') {
            try {
                println("[INFO] Try to drop dump")
                sshagent(credentials : ['FrontendMachineCredentials']) {
                    withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'FrontendMachineCredentials', usernameVariable: 'USER', passwordVariable: 'PASSWORD']]) {
                        sh """
                            ssh ${user}@${frontendIp} sudo docker exec -it ${productionContainerName} rm dump_devrc
                        """
                    }
                }
            } catch (Exception e) {
                println("[INFO] can't drop dump")
            }
        }
    }
}
