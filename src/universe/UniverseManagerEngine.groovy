package universe

/**
 * UniverseManager which works with builds with engines
 */
class UniverseManagerEngine extends UniverseManager  {

    def env

    UniverseClient universeClientParentProd
    UniverseClient universeClientParentDev

    Map universeClientsProd = [:]
    Map universeClientsDev = [:]

    UniverseManagerEngine(def context, def env, String productName) {
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
            universeClientParentProd = new UniverseClient(context, universeURLProd, env, productName)

            universeURLDev = "${context.DEV_UMS_URL}"
            universeClientParentDev = new UniverseClient(context, universeURLDev, env, productName)

            try {
                universeClientParentProd.tokenSetup()
            } catch (e) {
                context.println("[ERROR] Failed to setup token for Prod UMS.")
                universeClientParentProd = null
                context.println(e.toString());
                context.println(e.getMessage());
            }

            try {
                universeClientParentDev.tokenSetup()
            } catch (e) {
                context.println("[ERROR] Failed to setup token for Dev UMS.")
                universeClientParentDev = null
                context.println(e.toString());
                context.println(e.getMessage());
            }
        }
    }

    def createBuilds(Map options) {
        // create build ([OS-1:GPU-1, ... OS-N:GPU-N], ['Suite1', 'Suite2', ..., 'SuiteN'])
        context.withCredentials([context.string(credentialsId: "prodUniverseFrontURL", variable: "PROD_UMS_FRONT_URL"),
            context.string(credentialsId: "devUniverseProdURL", variable: "DEV_UMS_FRONT_URL")]) {
            if (universeClientParentProd) {
                universeClientParentProd.createBuild("", "", false, options, context.PROD_UMS_FRONT_URL, "prod")
            }
            if (universeClientParentDev) {
                universeClientParentDev.createBuild("", "", false, options, context.DEV_UMS_FRONT_URL, "dev")
            }
        }

        for (int i = 0; i < options.engines.size(); i++) {
            String engine = options.engines[i]
            String engineName = options.enginesNames[i]
            if (universeClientParentProd?.build) {
                universeClientsProd[engine] = new UniverseClient(context, universeURLProd, env, imageServiceURL, productName, engineName, universeClientParentProd)
                universeClientsProd[engine].createBuild(options.universePlatforms, options.groupsUMS, options.updateRefs)
            }
            if (universeClientParentDev?.build) {
                universeClientsDev[engine] = new UniverseClient(context, universeURLDev, env, imageServiceURL, productName, engineName, universeClientParentDev)
                universeClientsDev[engine].createBuild(options.universePlatforms, options.groupsUMS, options.updateRefs)
            }
        }

        updateIds(options)
    }

    def startBuildStage(String osName) {
        if (universeClientParentProd?.build) {
            universeClientParentProd.stage("Build-${osName}", "begin")
        }
        if (universeClientParentDev?.build) {
            universeClientParentDev.stage("Build-${osName}", "begin")
        }
    }

    def finishBuildStage(String osName) {
        if (universeClientParentProd?.build) {
            universeClientParentProd.stage("Build-${osName}", "end")
        }
        if (universeClientParentDev?.build) {
            universeClientParentDev.stage("Build-${osName}", "end")
        }
    }

    def startTestsStage(String osName, String asicName, Map options) {
        // TODO: improve envs, now working on Windows testers only
        if (universeClientsProd[options.engine]?.build) {
            universeClientsProd[options.engine].stage("Tests-${osName}-${asicName}", "begin")
        }
        if (universeClientsDev[options.engine]?.build) {
            universeClientsDev[options.engine].stage("Tests-${osName}-${asicName}", "begin")
        }

        updateIds(options)
    }

    def finishTestsStage(String osName, String asicName, Map options) {
        if (universeClientsProd[options.engine]?.build) {
            universeClientsProd[options.engine].stage("Tests-${osName}-${asicName}", "end")
        }
        if (universeClientsDev[options.engine]?.build) {
            universeClientsDev[options.engine].stage("Tests-${osName}-${asicName}", "end")
        }
    }

    def closeBuild(String problemMessage, Map options) {
        try {
            String status = options.buildWasAborted ? "ABORTED" : context.currentBuild.result

            if (universeClientParentProd?.build) {
                universeClientParentProd.problemMessage(problemMessage)
                universeClientParentProd.changeStatus(status)
                for (client in universeClientsProd) {
                    client.value.changeStatus(status)
                }
            }
            if (universeClientParentDev?.build) {
                universeClientParentDev.problemMessage(problemMessage)
                universeClientParentDev.changeStatus(status)
                for (client in universeClientsDev) {
                    client.value.changeStatus(status)
                }
            }
        } catch (e){
            context.println("[ERROR] Failed to close UMS build")
            context.println(e.toString())
            context.println(e.getMessage())
        }
    }

    def updateIds(Map options) {
        if (options.containsKey("engine")) {
            if (universeClientsProd[options.engine]?.build){
                options.buildIdProd = universeClientsProd[options.engine].build["id"]
                options.jobIdProd = universeClientsProd[options.engine].build["job_id"]
            }
            if (universeClientsDev[options.engine]?.build){
                options.buildIdDev = universeClientsDev[options.engine].build["id"]
                options.jobIdDev = universeClientsDev[options.engine].build["job_id"]
            }
        } else {
            if (universeClientParentProd?.build){
                options.buildIdProd = universeClientParentProd.build["id"]
                options.jobIdProd = universeClientParentProd.build["job_id"]
            } 
            if (universeClientParentDev?.build) {
                options.buildIdDev = universeClientParentDev.build["id"]
                options.jobIdDev = universeClientParentDev.build["job_id"]
            }
        }
    }

}