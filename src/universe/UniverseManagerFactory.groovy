package universe

/**
 * Factory for produce suitable UniverseManager implementation
 */
class UniverseManagerFactory {

    /**
     * Method which creates and returns suitable implementation of UniverseManager for current pipeline
     *
     * @param context this of Class of current pipeline
     * @param options Options map
     * @param env env of current pipeline
     * @param productName Name of product in UMS
     */
    static UniverseManager get(def context, Map options, def env, String productName) {
        if (options.containsKey("engines")) {
            return new UniverseManagerEngine(context, env, productName)
        } else {
            return new UniverseManagerDefault(context, env, productName)
        }
    }

}