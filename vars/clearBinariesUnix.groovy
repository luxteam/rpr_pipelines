def call()
{
    sh """
        if [ -d "${CIS_TOOLS}/../PluginsBinaries" ]; then
            find "${CIS_TOOLS}/../PluginsBinaries" -mindepth 1 -mtime +2 -delete
            find "${CIS_TOOLS}/../PluginsBinaries" -mindepth 1 -size +50G -delete
        fi
    """
}