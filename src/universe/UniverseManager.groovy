package universe

/**
 * Class which simplify work with multiple UniverseClients
 */
abstract class UniverseManager {
    
    def context
    String productName
    String universeURLProd
    String universeURLDev
    String imageServiceURL

    /**
     * Main constructor
     */
    UniverseManager(def context) {
        this.context = context
    }

    /**
     * Method which initialize main UMS Clients, tokens and urls.
     * Must be called after creation of constructor
     */
    abstract def init()

    /**
     * Created UMS builds.
     * Must be called after main part of PreBuild stage
     *
     * @param options Options map
     */
    abstract def createBuilds(Map options)

    /**
     * Notify all related UMS builds that Build stage started
     *
     * @param osName OS name on which Build stage run
     */
    abstract def startBuildStage(String osName)

    /**
     * Notify all related UMS builds that Build stage finished
     *
     * @param osName OS name on which Build stage finish
     */
    abstract def finishBuildStage(String osName)

    /**
     * Notify all related UMS builds that Tests stage started
     *
     * @param osName OS name on which Tests stage run
     * @param asicName GPU name on which Tests stage run
     * @param options Options map
     */
    abstract def startTestsStage(String osName, String asicName, Map options)

    /**
     * Method which wraps execution of tests if it's necessary
     *
     * @param osName OS name on which tests run
     * @param asicName GPU name on which tests run
     * @param options Options map
     * @param code Block of code which is executed
     */
    static def executeTests(String osName, String asicName, Map options, Closure code) {
        if (options.universeManager) {
            options.universeManager.wrapTestsExecution(osName, asicName, options, code)
        } else {
            code()
        }
    }

    /**
     * Method which wraps execution of tests. It adds necessary environment variables
     *
     * @param osName OS name on which tests run
     * @param asicName GPU name on which tests run
     * @param options Options map
     * @param code Block of code which is executed
     */
    def wrapTestsExecution(String osName, String asicName, Map options, Closure code) {
        context.withCredentials([context.usernamePassword(credentialsId: 'image_service', usernameVariable: 'IS_USER', passwordVariable: 'IS_PASSWORD'),
            context.usernamePassword(credentialsId: 'universeMonitoringSystem', usernameVariable: 'UMS_USER', passwordVariable: 'UMS_PASSWORD'),
            context.string(credentialsId: 'minioEndpoint', variable: 'MINIO_ENDPOINT'),
            context.usernamePassword(credentialsId: 'minioService', usernameVariable: 'MINIO_ACCESS_KEY', passwordVariable: 'MINIO_SECRET_KEY')]) {

            context.withEnv(["UMS_USE=${options.sendToUMS}", "UMS_ENV_LABEL=${osName}-${asicName}",
                "UMS_BUILD_ID_PROD=${options.buildIdProd}", "UMS_JOB_ID_PROD=${options.jobIdProd}", "UMS_URL_PROD=${universeURLProd}", 
                "UMS_LOGIN_PROD=" + context.UMS_USER, "UMS_PASSWORD_PROD=" + context.UMS_PASSWORD,
                "UMS_BUILD_ID_DEV=${options.buildIdDev}", "UMS_JOB_ID_DEV=${options.jobIdDev}", "UMS_URL_DEV=${universeURLDev}",
                "UMS_LOGIN_DEV=" + context.UMS_USER, "UMS_PASSWORD_DEV=" + context.UMS_PASSWORD,
                "IS_LOGIN=" + context.IS_USER, "IS_PASSWORD=" + context.IS_PASSWORD, "IS_URL=${imageServiceURL}",
                "MINIO_ENDPOINT=" + context.MINIO_ENDPOINT, "MINIO_ACCESS_KEY=" + context.MINIO_ACCESS_KEY, "MINIO_SECRET_KEY=" + context.MINIO_SECRET_KEY]) {

                code()

            }
        }
    }

    /**
     * Notify all related UMS builds that Tests stage finished
     *
     * @param osName OS name on which Tests stage finish
     * @param asicName GPU name on which Tests stage finish
     * @param options Options map
     */
    abstract def finishTestsStage(String osName, String asicName, Map options)

    /**
     * Method which sets final result for all related UMS builds and sends message with problems in current Jenkins build
     *
     * @param problemMessage String with problems in current Jenkins build
     * @param options Options map
     */
    abstract def closeBuild(String problemMessage, Map options)

    /**
     * Utility method which update ids of related UMS builds in options Map
     *
     * @param options Options map
     */
    abstract def updateIds(Map options)

    /**
     * Clone or pull jobs_launcher in jobs_launcher directory
     *
     * @param osName OS name on which jobs_launcher downloading
     * @param options Options map
     */
    def downloadJobsLauncher(String osName, Map options) {
        context.timeout(time: "5", unit: 'MINUTES') {
            context.dir("jobs_launcher") {
                context.withNotifications(title: osName, printMessage: true, options: options, configuration: NotificationConfiguration.DOWNLOAD_JOBS_LAUNCHER) {
                    context.checkOutBranchOrScm(options["jobsLauncherBranch"], "git@github.com:luxteam/jobs_launcher.git")
                }
            }
        }
    }

    /**
     * Method which sends stubs for skipped and errored test cases in all related UMS builds
     *
     * @param options Options map
     * @param lostTestsPath Path to json with information about lost tests
     * @param skippedTestsPath Path to json with information about skipped tests
     * @param retryInfoPath Path to json with information about retries
     */
    def sendStubs(Map options, String lostTestsPath, String skippedTestsPath, String retryInfoPath) {
        context.timeout(time: "10", unit: "MINUTES") {
            try {
                context.withCredentials([context.usernamePassword(credentialsId: 'image_service', usernameVariable: 'IS_USER', passwordVariable: 'IS_PASSWORD'),
                    context.usernamePassword(credentialsId: 'universeMonitoringSystem', usernameVariable: 'UMS_USER', passwordVariable: 'UMS_PASSWORD'),
                    context.string(credentialsId: 'prodUniverseURL', variable: 'PROD_UMS_URL'),
                    context.string(credentialsId: 'devUniverseURL', variable: 'DEV_UMS_URL'),
                    context.string(credentialsId: 'imageServiceURL', variable: 'IS_URL')]) {

                    updateIds(options)

                    context.withEnv(["UMS_BUILD_ID_PROD=${options.buildIdProd}", "UMS_JOB_ID_PROD=${options.jobIdProd}", "UMS_URL_PROD=" + context.PROD_UMS_URL, 
                        "UMS_LOGIN_PROD=" + context.UMS_USER, "UMS_PASSWORD_PROD=" + context.UMS_PASSWORD,
                        "UMS_BUILD_ID_DEV=${options.buildIdDev}", "UMS_JOB_ID_DEV=${options.jobIdDev}", "UMS_URL_DEV=" + context.DEV_UMS_URL,
                        "UMS_LOGIN_DEV=" + context.UMS_USER, "UMS_PASSWORD_DEV=" + context.UMS_PASSWORD,
                        "IS_LOGIN=" + context.IS_USER, "IS_PASSWORD=" + context.IS_PASSWORD, "IS_URL=" + context.IS_URL]) {
                        context.bat """
                            send_stubs_to_ums.bat \"${skippedTestsPath}\" \"${lostTestsPath}\" \"${retryInfoPath}\"
                        """
                    }
                }
            } catch (e) {
                if (context.utils.isTimeoutExceeded(e)) {
                    context.println("[WARNING] Failed to send stubs to UMS due to timeout")
                } else {
                    context.println("[WARNING] Failed to send stubs to UMS")
                }
                context.println(e.toString())
                context.println(e.getMessage())
            }
        }
    }

    /**
     * Method which sends specified files to MINIO
     *
     * @param options Options map
     * @param osName OS name of machine on which files are sent
     * @param filesPath Path to directory with files
     * @param pattern Pattern of target files
     * @param saveLog Save log about sending files to MINIO in Jenkins artifacts of current Jenkins build or not
     */
    def sendToMINIO(Map options, String osName, String filesPath, String pattern, Boolean saveLog = true) {
        downloadJobsLauncher(osName, options)

        context.dir("jobs_launcher") {
            context.timeout(time: "3", unit: 'MINUTES') {
                try {
                    context.withCredentials([context.string(credentialsId: 'minioEndpoint', variable: 'MINIO_ENDPOINT'),
                        context.usernamePassword(credentialsId: 'minioService', usernameVariable: 'MINIO_ACCESS_KEY', passwordVariable: 'MINIO_SECRET_KEY')]) {
                        context.withEnv(["UMS_BUILD_ID_PROD=${options.buildIdProd}", "UMS_JOB_ID_PROD=${options.jobIdProd}",
                            "UMS_BUILD_ID_DEV=${options.buildIdDev}", "UMS_JOB_ID_DEV=${options.jobIdDev}",
                            "MINIO_ENDPOINT=" + context.MINIO_ENDPOINT, "MINIO_ACCESS_KEY=" + context.MINIO_ACCESS_KEY,
                            "MINIO_SECRET_KEY=" + context.MINIO_SECRET_KEY]) {
                            switch(osName) {
                                case "Windows":
                                    filesPath = filesPath.replace('/', '\\\\')
                                    context.bat """
                                        send_to_minio.bat \"${filesPath}\" \"${pattern}\"
                                    """
                                    break
                                default:
                                    context.sh """
                                        chmod u+x send_to_minio.sh
                                        ./send_to_minio.sh ${filesPath} \"${pattern}\"
                                    """
                            }
                        }
                    }
                    if (saveLog) {
                        String stageName = options.stageName ?: "${context.STAGE_NAME}"
                        String fileName = options.currentTry ? "${stageName}_minio_${options.currentTry}.log" : "${stageName}_minio.log"
                        context.dir("${stageName}") {
                            context.utils.moveFiles(context, osName, "../*.log", ".")
                            context.utils.renameFile(context, osName, "launcher.engine.log", fileName)
                        }
                        context.archiveArtifacts(artifacts: "${stageName}/${fileName}", allowEmptyArchive: true)
                    }
                } catch (e) {
                    if (context.utils.isTimeoutExceeded(e)) {
                        context.println("[WARNING] Failed to send files to MINIO due to timeout")
                    } else {
                        context.println("[WARNING] Failed to send files to MINIO")
                    }
                    context.println(e.toString())
                    context.println(e.getMessage())
                    context.println(e.getStackTrace())
                }
            }
        }
    }

    String toString() {
        return """
            productName: ${productName}
            universeURLProd: ${universeURLProd}
            universeURLDev: ${universeClientDev}
            imageServiceURL: ${imageServiceURL}
        """
    }
}