def call() {
    try {
        powershell"""
            if (Test-Path "${CIS_TOOLS}\\..\\PluginsBinaries") {
                Get-ChildItem "${CIS_TOOLS}\\..\\PluginsBinaries" -Recurse -File | Where CreationTime -lt  (Get-Date).AddDays(-2)  | Remove-Item -Force
                \$folderSize = (Get-ChildItem -Recurse \"${CIS_TOOLS}\\..\\PluginsBinaries\" | Measure-Object -Property Length -Sum).Sum / 1GB
                if (\$folderSize -ge 10) {
                    Remove-Item -Recurse -Force \"${CIS_TOOLS}\\..\\PluginsBinaries\"
                }
            }
        """
    } catch (e) {
        println("[ERROR] Can't clear Win binaries")
        println(e.toString())
        println(e.getMessage())
    }

}