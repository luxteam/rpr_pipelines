package universe

/**
 * Default implementation of UniverseClient
 */
class UniverseManagerDefault extends UniverseManager {

    def env
    String productName
    String universeURLProd
    String universeURLDev
    String imageServiceURL

    UniverseClient universeClientProd
    UniverseClient universeClientDev

    UniverseManagerDefault(def context, def env, String productName) {
        super(context)

        this.env = env
        this.productName = productName
    }

    def init() {
        context.withCredentials([context.string(credentialsId: "prodUniverseURL", variable: "PROD_UMS_URL"),
            context.string(credentialsId: "devUniverseURL", variable: "DEV_UMS_URL"),
            context.string(credentialsId: "imageServiceURL", variable: "IS_URL")]) {

            universeURLProd = "${context.PROD_UMS_URL}"
            imageServiceURL = "${context.IS_URL}"
            universeClientProd = new UniverseClient(context, universeURLProd, env, imageServiceURL, productName)

            universeURLDev = "${context.DEV_UMS_URL}"
            universeClientDev = new UniverseClient(context, universeURLDev, env, imageServiceURL, productName)

            try {
                universeClientProd.tokenSetup()
            } catch (e) {
                context.println("[ERROR] Failed to setup token for Prod UMS.")
                universeClientProd = null
                context.println(e.toString());
                context.println(e.getMessage());
            }

            try {
                universeClientDev.tokenSetup()
            } catch (e) {
                context.println("[ERROR] Failed to setup token for Dev UMS.")
                universeClientDev = null
                context.println(e.toString());
                context.println(e.getMessage());
            }
        }
    }

    def createBuilds(Map options) {
        // create build ([OS-1:GPU-1, ... OS-N:GPU-N], ['Suite1', 'Suite2', ..., 'SuiteN'])
        context.withCredentials([context.string(credentialsId: "prodUniverseFrontURL", variable: "PROD_UMS_FRONT_URL"),
            context.string(credentialsId: "devUniverseProdURL", variable: "DEV_UMS_FRONT_URL")]) {
            if (universeClientProd) {
                universeClientProd.createBuild(options.universePlatforms, options.groupsUMS, options.updateRefs, options,
                        context.PROD_UMS_FRONT_URL, "prod")
            }
            if (universeClientDev) {
                universeClientDev.createBuild(options.universePlatforms, options.groupsUMS, options.updateRefs, options,
                        context.DEV_UMS_FRONT_URL, "dev")
            }
        }

        updateIds(options)
    }

    def startBuildStage(String osName) {
        if (universeClientProd?.build) {
            universeClientProd.stage("Build-${osName}", "begin")
        }
        if (universeClientDev?.build) {
            universeClientDev.stage("Build-${osName}", "begin")
        }
    }

    def finishBuildStage(String osName) {
        if (universeClientProd?.build) {
            universeClientProd.stage("Build-${osName}", "end")
        }
        if (universeClientDev?.build) {
            universeClientDev.stage("Build-${osName}", "end")
        }
    }

    def startTestsStage(String osName, String asicName, Map options) {
        if (universeClientProd?.build) {
            universeClientProd.stage("Tests-${osName}-${asicName}", "begin")
        }
        if (universeClientDev?.build) {
            universeClientDev.stage("Tests-${osName}-${asicName}", "begin")
        }
    }

    def finishTestsStage(String osName, String asicName, Map options) {
        if (universeClientProd?.build) {
            universeClientProd.stage("Tests-${osName}-${asicName}", "end")
        }
        if (universeClientDev?.build) {
            universeClientDev.stage("Tests-${osName}-${asicName}", "end")
        }
    }

    def closeBuild(String problemMessage, Map options) {
        try {
            String status = options.buildWasAborted ? "ABORTED" : context.currentBuild.result
            
            if (universeClientProd?.build) {
                universeClientProd.problemMessage(problemMessage)
                universeClientProd.changeStatus(status)
            }
            if (universeClientDev?.build) {
                universeClientDev.problemMessage(problemMessage)
                universeClientDev.changeStatus(status)
            }
        } catch (e){
            context.println("[ERROR] Failed to close UMS build")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    def updateIds(Map options) {
        if (universeClientProd?.build) {
            options.buildIdProd = universeClientProd.build["id"]
            options.jobIdProd = universeClientProd.build["job_id"]
        }
        if (universeClientDev?.build) {
            options.buildIdDev = universeClientDev.build["id"]
            options.jobIdDev = universeClientDev.build["job_id"]
        }
    }
}