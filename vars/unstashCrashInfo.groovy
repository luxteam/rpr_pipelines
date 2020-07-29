def call(nodeRetryList){ //FIXME: add type of nodeRetryList
    nodeRetryList.each{ gpu ->
        try
        {
            gpu['Tries'].each{ group ->
                group.each{ groupKey, retries ->
                    retries.each{ retry ->
                        unstash "${retry['link']}"
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