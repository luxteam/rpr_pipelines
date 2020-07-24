def call(String osName) {
	switch(osName) {
		case 'Windows':
			powershell """
				if([System.IO.File]::Exists("${CIS_TOOLS}\\..\\RenderServiceStorage")){
					forfiles /p "${CIS_TOOLS}\\..\\RenderServiceStorage" /s /d -3 /c "cmd /c del @file"
					\$folderSize = (Get-ChildItem -Recurse \"${CIS_TOOLS}\\..\\RenderServiceStorage\" | Measure-Object -Property Length -Sum).Sum / 1GB
					if (\$folderSize -ge 30) {
						Remove-Item -Recurse -Force \"${CIS_TOOLS}\\..\\RenderServiceStorage\"
					}
				}
			"""
	}
}