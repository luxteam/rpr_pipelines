import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;

def killProcesses(String osName) {
    try {
        if (osName == "Windows") {
            bat '''
                taskkill /f /im "blender.exe"
                taskkill /f /im "maya.exe"
                taskkill /f /im "3dsmax.exe"
            '''
        } else if (osName == "Darwin") {
            sh '''
                killall "Maya"
                killall "Maya"
                
            '''
            sh '''
                killall "Blender"
            '''
        } else {
            sh '''
                killall "Blender"
            '''
        }
    } catch (FlowInterruptedException e2) {
        throw e2
    } catch (Exception e2) {
        // ignore errors if processes don't exist
    } 
}

def call(String osName = "Windows") {

    killProcesses(osName)

    try {
        println("[INFO] Try to clean WS via cleanWs command")
        cleanWs(deleteDirs: true, disableDeferredWipeout: true)
        println("[INFO] WS was successfully cleaned via cleanWs command")
    } catch (FlowInterruptedException e1) {
        throw e1
    } catch (Exception e1) {

        try {
            println("[INFO] Try to clean WS via cleanWs command again.")
            cleanWs(deleteDirs: true, disableDeferredWipeout: true)
            println("[INFO] WS was successfully cleaned via cleanWs command")
        } catch (FlowInterruptedException e2) {
            throw e2
        } catch (Exception e2) {
            try {
                println("[ERROR] WS cleaning via cleanWs command failed. Try to do it via deleteDir command")
                deleteDir()
                println("[INFO] WS was successfully cleaned via deleteDir command")
            } catch (FlowInterruptedException e3) {
                throw e3
            } catch (Exception e3) {
                println("[ERROR] WS cleaning via deleteDir command failed. Try to do it via OS commands")
                switch(osName) {
                    case 'Windows':
                        bat '''
                            @echo off
                            del /q *
                            for /d %%x in (*) do @rd /s /q "%%x"
                        '''
                        break
                    default:
                        sh '''
                            rm -rf *
                        '''
                }

                println("[INFO] WS was successfully cleaned via OS commands")
            }
        }
    }
}
