def call(String osName, String asicName, Map targetDriverVersions, Map options)
{
    try {
        timeout(time: "20", unit: 'MINUTES') {
            currentDriverVersion = getDriverVersion(osName, asicName, options)
            targetDriverVersion = getDriverVersionFromMap(osName, asicName, targetDriverVersions)

            if (targetDriverVersion && currentDriverVersion != targetDriverVersion){
                installDriver(osName, asicName, targetDriverVersion, options)
                installedDriverVersion = getDriverVersion(osName, asicName, options)

                if (installedDriverVersion == targetDriverVersion){
                    println "[INFO] Driver successfully installed."
                } else {
                    println "[ERROR] Failed to install driver. Trying again after reboot."
                    rebootTesterNode(osName)
                    installDriver(osName, asicName, targetDriverVersion, options)
                    installedDriverVersion = getDriverVersion(osName, asicName, options)
                    if (installedDriverVersion == targetDriverVersion){
                        println "[INFO] Driver successfully installed."
                    } else {
                        println "[ERROR] Failed to install driver"
                        error "Failed to install driver."
                    }
                }
            } else {
                if (targetDriverVersion) {
                    println "[INFO] Current driver: ${currentDriverVersion} == ${targetDriverVersion}. Driver is already installed."
                } else {
                    println "[INFO] Driver install skipped. (-1)"
                }
            }
        }
    } catch (e) {
        println(e.toString())
        println(e.getMessage())
    }
}


def getDriverVersionFromMap(String osName, String asicName, Map targetDriverVersions){
    
    switch(osName) {
            case 'Windows':
                if (asicName.contains("AMD")){
                    println "[INFO] Detected AMD GPU on Windows"
                    try {
                        return targetDriverVersions['Windows-AMD']
                    } catch (e) {
                        return -1
                    }
                } else if (asicName.contains("NVIDIA")) {
                    println "[INFO] Detected NVIDIA GPU on Windows"
                    try {
                        return targetDriverVersions['Windows-NVIDIA']
                    } catch (e) {
                        return -1
                    }
                }
                break

            case 'OSX':
                println "[INFO] Driver install is unavailable for MacOS"
                return -1

            default:
                if (asicName.contains("AMD")){
                    println "[INFO] Detected AMD GPU on Ubuntu"
                    try {
                        return targetDriverVersions['Ubuntu-AMD']
                    } catch (e) {
                        return -1
                    }
                } else if (asicName.contains("NVIDIA")) {
                    println "[INFO] Detected NVIDIA GPU on Ubuntu"
                    try {
                        return targetDriverVersions['Ubuntu-NVIDIA']
                    } catch (e) {
                        return -1
                    }
                }
        }

}


def getDriverVersion(String osName, String asicName, Map options) 
{
    println "[INFO] Checking existence of the GPU driver."

    String version = "0"

    try {
        switch(osName) {
            case 'Windows':
                if (asicName.contains("AMD")){
                    println "[INFO] Searching AMD GPU driver on Windows"
                    // version = getWinAmdDriver()
                } else if (asicName.contains("NVIDIA")) {
                    println "[INFO] Searching NVIDIA GPU driver on Windows"
                    version = getWinNvidiaDriver()
                }
                break

            default:
                if (asicName.contains("AMD")){
                    println "[INFO] Searching AMD GPU driver on Ubuntu"
                    // version = getUbuntuAmdDriver()
                } else if (asicName.contains("NVIDIA")) {
                    println "[INFO] Searching NVIDIA GPU driver on Ubuntu"
                    version = getUbuntuNvidiaDriver()
                }
        }
        println "[INFO] Current driver version: ${version}"
    } catch (e) {
        println("[ERROR] Failed to get driver version. No driver found. Installing...")
        println(e.toString())
        println(e.getMessage())
    }
    return version
}


def getUbuntuNvidiaDriver(){
    stdout = sh (script: "nvidia-smi --query-gpu=driver_version --format=csv", returnStdout: true)
    print ("Nvidia driver stdout: ${stdout}")
    return (stdout =~ /\d+.\d+.\d+/).findAll()[0]
}


def getWinNvidiaDriver(){
    stdout = bat (script: "${CIS_TOOLS}\\..\\drivers\\nvidia-smi.exe --query-gpu=driver_version --format=csv", returnStdout: true)
    print ("Nvidia driver stdout: ${stdout}")
    return (stdout =~ /\d+.\d+.\d+/).findAll()[0]
}


def installDriver(String osName, String asicName, String driverVersion, Map options){

    println "[INFO] Installing the GPU driver."

    try {
        switch(osName) {
            case 'Windows':
                if (asicName.contains("AMD")){
                    println "[INFO] Install driver for AMD GPU on Windows"
                    // installWinAmdDriver(riverVersion, options)
                } else if (asicName.contains("NVIDIA")) {
                    println "[INFO] Install driver for NVIDIA GPU on Windows"
                    installWinNvidiaDriver(driverVersion, options)
                }
                break

            default:
                if (asicName.contains("AMD")){
                    println "[INFO] Install driver for AMD GPU on Ubuntu"
                    // installUbuntuAmdDriver(driverVersion, options)
                } else if (asicName.contains("NVIDIA")) {
                    println "[INFO] Install driver for NVIDIA GPU on Ubuntu"
                    installUbuntuNvidiaDriver(driverVersion, options)
                }
        }

    } catch (e) {
        println("[ERROR] Failed to install driver.")
        println(e.toString())
        println(e.getMessage())
    }
    
}


def installUbuntuNvidiaDriver(String driverVersion, Map options){
    sh """
        sudo ${CIS_TOOLS}/installNvidiaDriverUbuntu.sh ${CIS_TOOLS}/../drivers/NVIDIA-Linux-x86_64-${driverVersion}.run ${WORKSPACE}/${STAGE_NAME}.driver.log
    """
    rebootTesterNode("Ubuntu")
}


def installWinNvidiaDriver(String driverVersion, Map options){
    bat """
        ${CIS_TOOLS}\\..\\drivers\\${driverVersion}_geforce_win10_64bit_dch_international.exe -s
    """
    rebootTesterNode("Windows")
}


def rebootTesterNode(String osName){
    
    switch(osName) {
        case 'Windows':
            bat """
                shutdown /r /f /t 0
            """
            break

        default:
            sh """
                echo "Restarting Unix Machine..."
                hostname
                (sleep 3; sudo shutdown -r now) &
            """
    }
    sleep(300)
}