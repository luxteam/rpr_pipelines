def call(List nodeRetryList, String engine = ""){
    nodeRetryList.each{ gpu ->
        try
        {
            gpu['Tries'].each{ group ->
                group.each{ groupKey, retries ->
                    if (!engine || groupKey.split("-")[-1] == engine) {
                        retries.each{ retry ->
                            unstash "${retry['link']}"
                        }
                    }
                }
            }
        }catch(e)
        {
            echo "[ERROR] Failed to unstash crash log"
            println(e.toString());
            println(e.getMessage());
        }
    }
}