def call(String osName, String tool_version, Map options, Boolean matlib=false, Boolean deinstall=false)
{
    // deinstall plugin from tool pipeline
    if (deinstall) {
        println '[INFO] Uninstalling Blender addon'
        uninstallBlenderAddon(osName, tool_version, options)
        return
    }

    // temp code for deleting old plugin
    if (osName == 'Windows'){
        println '[INFO] Uninstalling old plugin'
        uninstallMSI("Radeon%Blender%", options.stageName, options.currentTry)
    }

    // Prebuilt plugin will be reinstalled in any cases
    if (options.isPreBuilt) { 
        reinstallBlenderAddon(osName, tool_version, options, matlib)
        return true

    // Check installed plugin and reinstall if needed
    } else {
        if (checkExistenceOfBlenderAddon(osName, tool_version, options)) {
            println '[INFO] Current plugin is already installed.'
            return false
        } else {
            reinstallBlenderAddon(osName, tool_version, options, matlib)
            return true
        }
    }
}


def checkExistenceOfBlenderAddon(String osName, String tool_version, Map options) 
{

    println "[INFO] Checking existence of the Blender Addon on test PC."
    println "[INFO] Installer name: ${options.commitSHA}_${osName}.zip"
    println "[INFO] Built Blender Addon commit hash: ${options.commitShortSHA}"

    try {

        String blenderAddonCommitHash = ""

        switch(osName)
        {
            case 'Windows':
                // Reading commit hash from installed addon
                bat """
                    echo import os >> getInstallerCommitHash.py
                    echo commit_hash = "unknown" >> getInstallerCommitHash.py
                    echo init_path = r"C:\\Users\\${env.USERNAME}\\AppData\\Roaming\\Blender Foundation\\Blender\\${tool_version}\\scripts\\addons\\rprblender\\__init__.py" >> getInstallerCommitHash.py
                    echo if os.path.exists(init_path): >> getInstallerCommitHash.py
                    echo     with open(init_path) as f: >> getInstallerCommitHash.py
                    echo         lines = f.readlines() >> getInstallerCommitHash.py
                    echo         for line in lines: >> getInstallerCommitHash.py
                    echo             if line.startswith("version_build"): >> getInstallerCommitHash.py 
                    echo                 commit_hash = line[17:24] >> getInstallerCommitHash.py
                    echo print(commit_hash) >> getInstallerCommitHash.py 
                """

                blenderAddonCommitHash = python3("getInstallerCommitHash.py").split('\r\n')[2].trim()
                break;

            case 'OSX':
                // Reading commit hash from installed addon
                sh """
                    echo import os >> getInstallerCommitHash.py
                    echo commit_hash = '"unknown"' >> getInstallerCommitHash.py
                    echo init_path = r'"/Users/${env.USERNAME}/Library/Application Support/Blender/${tool_version}/scripts/addons/rprblender/__init__.py"' >> getInstallerCommitHash.py
                    echo if os.path.exists'(init_path)': >> getInstallerCommitHash.py
                    echo '    'with open'(init_path)' as f: >> getInstallerCommitHash.py
                    echo '        'lines = f.readlines'()' >> getInstallerCommitHash.py
                    echo '        'for line in lines: >> getInstallerCommitHash.py
                    echo '            'if line.startswith'("version_build")': >> getInstallerCommitHash.py 
                    echo '                'commit_hash = line[17:24] >> getInstallerCommitHash.py
                    echo print'(commit_hash)' >> getInstallerCommitHash.py 
                """ 

                blenderAddonCommitHash = python3("getInstallerCommitHash.py").trim()
                break;

            default:
                // Reading commit hash from installed addon
                sh """
                    echo import os >> getInstallerCommitHash.py
                    echo commit_hash = '"unknown"' >> getInstallerCommitHash.py
                    echo init_path = r'"/home/${env.USERNAME}/.config/blender/${tool_version}/scripts/addons/rprblender/__init__.py"' >> getInstallerCommitHash.py
                    echo if os.path.exists'(init_path)': >> getInstallerCommitHash.py
                    echo '    'with open'(init_path)' as f: >> getInstallerCommitHash.py
                    echo '        'lines = f.readlines'()' >> getInstallerCommitHash.py
                    echo '        'for line in lines: >> getInstallerCommitHash.py
                    echo '            'if line.startswith'("version_build")': >> getInstallerCommitHash.py 
                    echo '                'commit_hash = line[17:24] >> getInstallerCommitHash.py
                    echo print'(commit_hash)' >> getInstallerCommitHash.py 
                """         
                blenderAddonCommitHash = python3("getInstallerCommitHash.py").trim()[1..-2]
        }

        println "[INFO] Built Blender Addon commit hash: ${blenderAddonCommitHash}"

        return options.commitShortSHA == blenderAddonCommitHash

    } catch (e) {
        echo "[ERROR] Failed to compare installed and built plugin. Reinstalling..."
        println(e.toString())
        println(e.getMessage())
    }
    
    return false
}


def reinstallBlenderAddon(String osName, String tool_version, Map options, Boolean matlib){
    
    println '[INFO] Uninstalling Blender addon'
    uninstallBlenderAddon(osName, tool_version, options)
    println '[INFO] Installing Blender addon'
    installBlenderAddon(osName, tool_version, options)
    if (matlib){
        installMatLib(osName, options)
    }
}



def uninstallBlenderAddon(String osName, String tool_version, Map options)
{
    // Remove RadeonProRender Addon from Blender  
    try {
        switch(osName)
        {
            case 'Windows':

                try 
                {
                    timeout(time: "5", unit: 'MINUTES') {
                        bat """
                            echo "Disabling RPR Addon for Blender." >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1
                            echo import bpy >> disableRPRAddon.py
                            echo bpy.ops.preferences.addon_disable(module="rprblender")  >> disableRPRAddon.py
                            echo bpy.ops.wm.save_userpref() >> disableRPRAddon.py
                            "C:\\Program Files\\Blender Foundation\\Blender ${tool_version}\\blender.exe" -b -P disableRPRAddon.py >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1

                            echo "Removing RPR Addon for Blender." >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1
                            echo import bpy >> removeRPRAddon.py
                            echo bpy.ops.preferences.addon_remove(module="rprblender") >> removeRPRAddon.py
                            echo bpy.ops.wm.save_userpref() >> removeRPRAddon.py
                            "C:\\Program Files\\Blender Foundation\\Blender ${tool_version}\\blender.exe" -b -P removeRPRAddon.py >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1
                        """
                    }
                } catch (e) {
                    println "[ERROR] Failed to delete Blender Addon via python script."
                } finally {

                    if (fileExists("C:\\Users\\${env.USERNAME}\\AppData\\Roaming\\Blender Foundation\\Blender\\${tool_version}\\scripts\\addons\\rprblender")) {
                        dir("C:\\Users\\${env.USERNAME}\\AppData\\Roaming\\Blender Foundation\\Blender\\${tool_version}\\scripts\\addons") {
                            bat """
                                rmdir /s/q rprblender
                            """
                        }
                        println "[INFO] Deleted using cmd command."
                    }
                }

                break;
            
            case 'OSX':
                try 
                {
                    timeout(time: "5", unit: 'MINUTES') {
                        sh """
                            echo "Disabling RPR Addon for Blender." >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1

                            echo import bpy >> disableRPRAddon.py
                            echo bpy.ops.preferences.addon_disable'(module="rprblender")'  >> disableRPRAddon.py
                            echo bpy.ops.wm.save_userpref'()' >> disableRPRAddon.py
                            blender${tool_version} -b -P disableRPRAddon.py >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1

                            echo "Removing RPR Addon for Blender." >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1

                            echo import bpy >> removeRPRAddon.py
                            echo bpy.ops.preferences.addon_remove'(module="rprblender")' >> removeRPRAddon.py
                            echo bpy.ops.wm.save_userpref'()' >> removeRPRAddon.py

                            blender${tool_version} -b -P removeRPRAddon.py >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1
                        """
                    }
                } catch (e) {
                    println "[ERROR] Failed to delete Blender Addon via python script."
                } finally {

                    if (fileExists("/Users/${env.USERNAME}/Library/Application Support/Blender/${tool_version}/scripts/addons/rprblender")) {
                        dir("/Users/${env.USERNAME}/Library/Application Support/Blender/${tool_version}/scripts/addons") {
                            sh """
                                rm -fdr rprblender
                            """
                            println "[INFO] Deleted using sh command."
                        }
                    }
                }

                break;

            default:
                try 
                {
                    timeout(time: "10", unit: 'MINUTES') {
                        sh """
                            echo "Disabling RPR Addon for Blender." >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1

                            echo import bpy >> disableRPRAddon.py
                            echo bpy.ops.preferences.addon_disable'(module="rprblender")'  >> disableRPRAddon.py
                            echo bpy.ops.wm.save_userpref'()' >> disableRPRAddon.py
                            blender${tool_version} -b -P disableRPRAddon.py >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1

                            echo "Removing RPR Addon for Blender." >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1

                            echo import bpy >> removeRPRAddon.py
                            echo bpy.ops.preferences.addon_remove'(module="rprblender")' >> removeRPRAddon.py
                            echo bpy.ops.wm.save_userpref'()' >> removeRPRAddon.py

                            blender${tool_version} -b -P removeRPRAddon.py >> \"${options.stageName}_${options.currentTry}.uninstall.log\" 2>&1
                        """
                    }
                } catch (e) {
                    println "[ERROR] Failed to delete Blender Addon via python script."
                } finally {
                    if (fileExists("/home/${env.USERNAME}/.config/blender/${tool_version}/scripts/addons/rprblender")) {

                        dir("/home/${env.USERNAME}/.config/blender/${tool_version}/scripts/addons") {
                            sh """
                                rm -fdr rprblender
                            """
                        }
                        println "[INFO] Deleted using sh command."
                    }
                }
        }
    }
    catch(e)
    {
        echo "[ERROR] Failed to delete RPR Addon from Blender"
        println(e.toString())
        println(e.getMessage())
    }
}
    


def installBlenderAddon(String osName, String tool_version, Map options)
{
    // Installing RPR Addon in Blender

    switch(osName)
    {
        case "Windows":
            if (options['isPreBuilt']) {
                addon_name = "${options.pluginWinSha}"
            } else {
                addon_name = "${options.commitSHA}_Windows"
            }
            bat """
                echo "Installing RPR Addon in Blender" >> \"${options.stageName}_${options.currentTry}.install.log\"
                echo import bpy >> registerRPRinBlender.py
                echo addon_path = "${CIS_TOOLS}\\..\\PluginsBinaries\\\\${addon_name}.zip" >> registerRPRinBlender.py
                echo bpy.ops.preferences.addon_install(filepath=addon_path) >> registerRPRinBlender.py
                echo bpy.ops.preferences.addon_enable(module="rprblender") >> registerRPRinBlender.py
                echo bpy.ops.wm.save_userpref() >> registerRPRinBlender.py

                "C:\\Program Files\\Blender Foundation\\Blender ${tool_version}\\blender.exe" -b -P registerRPRinBlender.py >> \"${options.stageName}_${options.currentTry}.install.log\" 2>&1
            """
            break;
      
        case "OSX":
            if (options['isPreBuilt']) {
                addon_name = "${options.pluginOSXSha}"
            } else {
                addon_name = "${options.commitSHA}_OSX"
            }
            sh """
                echo "Installing RPR Addon in Blender" >> \"${options.stageName}_${options.currentTry}.install.log\"
                echo import bpy >> registerRPRinBlender.py
                echo addon_path = '"${CIS_TOOLS}/../PluginsBinaries/${addon_name}.zip"' >> registerRPRinBlender.py
                echo bpy.ops.preferences.addon_install'(filepath=addon_path)' >> registerRPRinBlender.py
                echo bpy.ops.preferences.addon_enable'(module="rprblender")' >> registerRPRinBlender.py
                echo bpy.ops.wm.save_userpref'()' >> registerRPRinBlender.py

                blender${tool_version} -b -P registerRPRinBlender.py >> \"${options.stageName}_${options.currentTry}.install.log\" 2>&1
            """
            break;

        default:
            if (options['isPreBuilt']) {
                addon_name = "${options.pluginUbuntuSha}"
            } else {
                addon_name = "${options.commitSHA}_${osName}"
            }
            sh """
                echo "Installing RPR Addon in Blender" >> \"${options.stageName}_${options.currentTry}.install.log\"
                echo import bpy >> registerRPRinBlender.py
                echo addon_path = '"${CIS_TOOLS}/../PluginsBinaries/${addon_name}.zip"' >> registerRPRinBlender.py
                echo bpy.ops.preferences.addon_install'(filepath=addon_path)' >> registerRPRinBlender.py
                echo bpy.ops.preferences.addon_enable'(module="rprblender")' >> registerRPRinBlender.py
                echo bpy.ops.wm.save_userpref'()' >> registerRPRinBlender.py

                blender${tool_version} -b -P registerRPRinBlender.py >> \"${options.stageName}_${options.currentTry}.install.log\" 2>&1
            """
    }
}
