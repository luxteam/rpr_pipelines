/**
 * Verify Radeon ProRender Material Library installation on machine for given tool
 * @param tool - a utility for which you need to check for the installed library
 * @param cacheImgPath - path to image which will be compare with baseline image
 * @param allowableDiff - percentage acceptable difference between images
 * @param osName - os on which the check will take place
 * @param options - extra info like Jenkins stage name or current try try of that stage
 */
def call(String tool, String cacheImgPath, Integer allowableDiff, String osName, Map options) {
    Double diff
    try {
        String baselineImgPath = "./jobs_launcher/common/img/matlib_baselines/"
        switch (tool) {
            case "Blender":
                baselineImgPath += "bl_material_baseline.jpg"
                break

            case "Maya":
                baselineImgPath += "maya_material_baseline.jpg"
                break

            default:
                println "Tool not supported"
                return
        }
        println "[INFO] Comparing material baseline and created image"
        diff = utils.compareImages(this, cacheImgPath, baselineImgPath)
        println "[INFO] Image difference = ${diff}%"
    } catch (e) {
        throw new ExpectedExceptionWrapper(NotificationConfiguration.FAILED_TO_VERIFY_MATLIB, e)
    }

    try {
        if (diff > allowableDiff) {
            println "[ERROR] build_cache and image with material library are differ considerably."
            println "[INFO] Trying to install matlib..."
            installMatlib(osName, options)
            String success = "[INFO] RadeonProMaterialLibrary installation verified"
            String failure = "[INFO] Can't verify matlib installation"
            switch (osName) {
                case "Windows":
                    if ((python3("./jobs_launcher/common/scripts/check_matlib_registry.py --tool ${tool}").split(" ").last()) as Integer == 0) {
                        println success
                    } else {
                        println failure
                    }
                    break

                case "OSX":
                    println "Matlib checking not supported on macOS"
                    break

                default:
                    sh """
                        if [ -d "home/\$USER/Documents/Radeon ProRender" ]; echo ${success}; else echo ${failure}; fi
                    """
            }
        } else {
            println "[INFO] build_cache and baseline image are equal: matlib already installed on this node."
        }
    } catch (e) {
        throw new ExpectedExceptionWrapper(NotificationConfiguration.FAILED_TO_INSTALL_MATLIB, e)
    }
}