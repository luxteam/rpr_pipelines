def executeRender(osName, Map options)
{ 
            bat '''
            DEL /F /S /Q *
            '''
            String tool = options['Tool'].split(':')[0].trim()
            String version = options['Tool'].split(':')[1].trim()
            echo "${options}"
            switch(tool) 
              {
              case 'Blender':
                      bat """ 
                      "C:\\JN\\cis_tools\\RenderSceneJob\\download.bat" "${options.Scene}"
                      """
                      bat """
                      "C:\\JN\\cis_tools\\7-Zip\\7z.exe" x "scene.zip"
                      """
                      bat """
                      copy "..\\..\\cis_tools\\RenderSceneJob\\find_scene_blender.py" "."
                      copy "..\\..\\cis_tools\\RenderSceneJob\\blender_render.py" "."
                      """
                      String scene=python3("find_scene_blender.py --folder .").split('\r\n')[2].trim()
                      echo "Find scene: ${scene}"
                      echo "Launch App"
                      bat """
                      "C:\\Program Files\\Blender Foundation\\Blender\\blender.exe" -b "${scene}" -P "blender_render.py"
                      """
                      break;
              case 'Max':
                      bat """ 
                      "C:\\JN\\cis_tools\\RenderSceneJob\\download.bat" "${options.Scene}"
                      """
                      bat """
                      "C:\\JN\\cis_tools\\7-Zip\\7z.exe" x "scene.zip"
                      """
                      bat """
                      copy "..\\..\\cis_tools\\RenderSceneJob\\find_scene_max.py" "."
                      copy "..\\..\\cis_tools\\RenderSceneJob\\generate_script_max.py" "."
                      copy "..\\..\\cis_tools\\RenderSceneJob\\max_render.ms" "."
                      """
                      String scene=python3("find_scene_max.py --folder . ").split('\r\n')[2].trim()
                      echo "Find scene: ${scene}"
                      echo "Generating script..."
                      python3("generate_script_max.py --folder . --scene ${scene}")
                      echo "Done."
                      echo "Launch App"
                      bat """
                      "C:\\Program Files\\Autodesk\\3ds Max ${version}\\3dsmax.exe" -U MAXScript "max_render.ms" -silent
                      """
                      break;
              case 'Maya':
                      bat """ 
                      "C:\\JN\\cis_tools\\RenderSceneJob\\download.bat" "${options.Scene}"
                      """
                      bat """
                      "C:\\JN\\cis_tools\\7-Zip\\7z.exe" x "scene.zip"
                      """
                      bat """
                      copy "..\\..\\cis_tools\\RenderSceneJob\\find_scene_maya.py" "."
                      copy "..\\..\\cis_tools\\RenderSceneJob\\generate_script_maya.py" "."
                      copy "..\\..\\cis_tools\\RenderSceneJob\\maya_render.mel" "."
                      """
                      String scene=python3("find_scene_maya.py --folder . ").split('\r\n')[2].trim()
                      echo "Find scene: ${scene}"
                      echo "Generating script..."
                      python3("generate_script_maya.py --folder . --scene ${scene}")
                      echo "Done."
                      echo "Launch App"
                      bat """
                      set MAYA_SCRIPT_PATH=%cd%;%MAYA_SCRIPT_PATH%
                      "C:\\Program Files\\Autodesk\\Maya${version}\\bin\\maya.exe" -command "source maya_render.mel; evalDeferred -lp (rpr_render());"
                      """
                      break;
                  }    

  archiveArtifacts "Output/*"
}

def executePlatform(String osName, String gpuNames, Map options)
{
    def retNode =  
    {   
        try {
            
            if(gpuNames)
            {
                def testTasks = [:]
                gpuNames.split(',').each()
                {
                    String asicName = it
                    echo "Scheduling Test ${osName}:${asicName}"

                    testTasks["Test-${it}-${osName}"] = {
                        node("${osName} && Tester && OpenCL && gpu${asicName}")
                        {
                            stage("Test-${asicName}-${osName}")
                            {
                                ws("WS/${options.PRJ_NAME}_Test") {
                                    Map newOptions = options.clone()
                                    newOptions['testResultsName'] = "testResult-${asicName}-${osName}"
                                    executeRender(osName, newOptions)
                                }
                            }
                        }
                    }
                }
                parallel testTasks
            }
        }
        catch (e) {
            println(e.toString());
            println(e.getMessage());
            println(e.getStackTrace());        
            currentBuild.result = "FAILED"
            echo "FAILED by executePlatform"
            throw e
        }
    }
    return retNode
}

def main(String platforms, Map options) {
    
    try {
        properties([[$class: 'BuildDiscarderProperty', strategy: 
                     [$class: 'LogRotator', artifactDaysToKeepStr: '', 
                      artifactNumToKeepStr: '50', daysToKeepStr: '', numToKeepStr: '']]]);
        
        timestamps {
            String PRJ_PATH="${options.PRJ_ROOT}/${options.PRJ_NAME}"
            String JOB_PATH="${PRJ_PATH}/${JOB_NAME}/Build-${BUILD_ID}".replace('%2F', '_')
            options['PRJ_PATH']="${PRJ_PATH}"
            options['JOB_PATH']="${JOB_PATH}"

            def platformList = [];
            def testResultList = [];

            def tasks = [:]

            platforms.split(';').each()
            {

                List tokens = it.tokenize(':')
                String osName = tokens.get(0)
                String gpuNames = ""
                if (tokens.size() > 1)
                {
                    gpuNames = tokens.get(1)
                }

                platformList << osName
                if(gpuNames)
                {
                    gpuNames.split(',').each()
                    {
                        String asicName = it
                        testResultList << "testResult-${asicName}-${osName}"
                    }
                }

                tasks[osName]=executePlatform(osName, gpuNames, options)
            }
            parallel tasks
        }     
      }
    catch (e) {
        println(e.toString());
        println(e.getMessage());
        println(e.getStackTrace());
        currentBuild.result = "FAILED"
        throw e
    }
    finally {

        echo "enableNotifications = ${options.enableNotifications}"
        if("${options.enableNotifications}" == "true")
        {
            sendBuildStatusNotification(currentBuild.result, 
                                        options.get('slackChannel', ''), 
                                        options.get('slackBaseUrl', ''),
                                        options.get('slackTocken', ''),
                                        options.CBR)
        }
    }
}
  
def call(String Tool = '',
         String Scene = '',	
         String platforms = '',
         String RenderDevice = 'gpu',
         String PassLimit = ''
        ) {
  
    String PRJ_ROOT='Render_Scene'
    String PRJ_NAME='Render_Scene'
      
    main(platforms,
                   [
                    enableNotifications:false,
                    PRJ_NAME:PRJ_NAME,
                    PRJ_ROOT:PRJ_ROOT,
                    Tool:Tool,
                    Scene:Scene,
                    RenderDevice:RenderDevice,
                    PassLimit:PassLimit])
}
