import utils

def call(String project = "")
{
    if (!project) {
        project = getProjectName()
    }

    if (isCisDevelopJob()) {
        println("BuildDiscarderProperty will use settings for Dev CIS job.")
        if (isMasterBranch()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '10']]])

        } else if (isDevelopBranch()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '10']]])

        } else if (isPR()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '3']]])

        } else if (isCustomBranch()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '3']]])

        } else if (isWeeklyJob()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '10']]])

        } else {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '10']]])
        }

    } else  if (project in ["Blender", "Maya", "Max", "Core"]) {
        println("BuildDiscarderProperty will use settings for plugins job.")
        if (isMasterBranch()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '180', numToKeepStr: '10']]])

        } else if (isDevelopBranch()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '90', numToKeepStr: '20']]])

        } else if (isPR()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '90', numToKeepStr: '3']]])

        } else if (isCustomBranch()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '90', numToKeepStr: '3']]])

        } else if (isWeeklyJob()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '180', numToKeepStr: '20']]])

        } else {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '90', numToKeepStr: '20']]])
        }
    
    } else if (project == "MaterialLibrary") {

        properties([[$class: 'BuildDiscarderProperty', strategy: 
            [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5']]])

    } else {
        println("BuildDiscarderProperty will use default settings.")
        if (isMasterBranch()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '180', numToKeepStr: '10']]])

        } else if (isDevelopBranch()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '90', numToKeepStr: '10']]])

        } else if (isPR()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '90', numToKeepStr: '3']]])

        } else if (isCustomBranch()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '90', numToKeepStr: '3']]])

        } else if (isWeeklyJob()) {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '90', numToKeepStr: '10']]])

        } else {
            properties([[$class: 'BuildDiscarderProperty', strategy:
                [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '90', numToKeepStr: '10']]])
        }

    }

    
}


def getProjectName(){
    if (env.JOB_NAME.contains("Maya")) {
        return "Maya"
    } else if (env.JOB_NAME.contains("Blender2.8")) {
        return "Blender2.8"
    } else if (env.JOB_NAME.contains("Max")) {
        return "Max"
    } else if (env.JOB_NAME.contains("USDViewer")) {
        return "USDViewer"
    } else if (env.JOB_NAME.contains("Core")) {
        return "Core"
    } else if (env.JOB_NAME.contains("MaterialLibrary")) {
        return "MaterialLibrary"
    } else {
        return "Default"
    }
}


def isWeeklyJob(){
    return env.JOB_NAME.contains("Weekly") ? true : false
}


def isMasterBranch(){
    return env.BRANCH_NAME && env.BRANCH_NAME == "master"
}


def isDevelopBranch(){
    return env.BRANCH_NAME && (env.BRANCH_NAME == "develop" || env.BRANCH_NAME == "1.xx")
}


def isCustomBranch(){
    return env.BRANCH_NAME && env.BRANCH_NAME != "master" && env.BRANCH_NAME != "develop"
}


def isPR(){
    return env.CHANGE_URL
}


def isCisDevelopJob(){
    return env.JOB_NAME.startsWith("Dev")
}