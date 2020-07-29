import java.text.SimpleDateFormat;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import RBSInstance



class RBSProduction {
    def instances = []
    def context
    def tool
    def branchTag
    def buildName
    def buildID
    def rbsLogin
    def rbsPassword
    def instancesConfig = [
            [
                "url": "https://umsapi.cis.luxoft.com",
                "credentialId": 'universeMonitoringSystem'
            ]
    ]

    // context from perent pipeline
    RBSProduction(context, tool, name, env) {
        this.context = context
        this.tool = tool

        // take build name
        this.buildName = env.BUILD_NUMBER
        if (env.BUILD_DISPLAY_NAME != null && env.BUILD_DISPLAY_NAME != "#${this.buildName}") {
            this.buildName += " " + env.BUILD_DISPLAY_NAME
        }

        if (name.contains("Weekly")) {
            this.branchTag = "weekly"
        } else if (name.contains("Auto")) {
            this.branchTag = "master"
        } else {
            this.branchTag = "manual"
        }

        for (iConfig in this.instancesConfig) {
            this.instances += [new RBSInstance(iConfig, context)]
        }
    }

    def retryWrapper(func) {
        def attempt = 0
        def attempts = 5
        def timeout = 30 // seconds

        this.context.waitUntil {
            if (attempt == attempts) {
                println("Attempts: 0. Exit.")
                return true
            }

            attempt++
            this.context.println("Attempt: ${attempt}")

            try {
                func.call()
                return true
            } catch (FlowInterruptedException error) {
                println "INFO[job was aborted]"
                println "[INFO] Task was aborted during assets downloading"
                throw error
            } catch(error) {
                this.context.println(error)
                this.context.sleep(timeout)
                // timeout = timeout + 30
                return false
            }
        }
    }


    def startBuild(options) {
        // get tokens for all instances
        try {
            for (i in this.instances) {
                def request = {
                    i.tokenSetup()

                    def tests = "[]"

                    if (options.groupsRBS != null) {
                        tests = """["${options.groupsRBS.join('","')}"]"""
                    } else if (options.testsList != null) {
                        tests = """["${options.testsList.join('","')}"]"""
                    }
                    else if (options.tests.getClass() == java.util.ArrayList) {
                        tests = """["${options.tests.join('","')}"]"""
                    } else {
                        tests = """["${options.tests.replace(' ', '","')}"]"""
                    }

                    String requestData = """
                        {"name": "${this.buildName}",
                        "primary_time": "${options.JOB_STARTED_TIME}",
                        "branch": "${this.branchTag}",
                        "tool": "${this.tool}",
                        "groups": ${tests},
                        "count_test_machine" : ${options.gpusCount}}
                    """.replaceAll("\n", "")

                    def res = this.context.httpRequest(
                        acceptType: 'APPLICATION_JSON',
                        consoleLogResponseBody: true,
                        contentType: 'APPLICATION_JSON',
                        customHeaders: [
                                [name: 'Authorization', value: "Token ${i.token}"]
                        ],
                        httpMode: 'POST',
                        ignoreSslErrors: true,
                        url: "${i.url}/report/job?data=${java.net.URLEncoder.encode(requestData, 'UTF-8')}",
                        validResponseCodes: '200'
                    )

                    this.context.echo requestData
                    res = this.context.readJSON text:"${res.content}"
                    this.buildID = "${res.res.build_id}"
                    this.context.echo "Status: ${res.status}\nContent: ${res.content}"
                }

                retryWrapper(request)
            }
        } catch (e) {
            this.context.echo e.toString()
            this.context.echo "RBS: can't create build."
        }
    }


    def setTester(options) {
        try {
            for (i in this.instances) {
                def request = {
                    String tests = (options.tests != "") ? """--tests ${options.tests}""" : ""
                    String testsPackage = (options.testsPackage != "none") ? """--tests_package ${options.testsPackage}""" : ""
                    this.context.python3("""jobs_launcher/rbs_development.py --tool ${this.tool} --branch ${this.branchTag} --build ${this.buildID} ${tests} ${testsPackage} --token ${i.token} --link ${i.url}""")
                }

                retryWrapper(request)
            }
        } catch (e) {
            this.context.echo e.toString()
            this.context.echo "RBS: can't set tester."
        }
    }


    def setFailureStatus() {
        for (i in this.instances) {
            def request = {
                def response = this.context.httpRequest(

                        consoleLogResponseBody: true,
                        customHeaders: [
                                [name: 'Authorization', value: "Token ${i.token}"]
                        ],
                        httpMode: 'POST',
                        ignoreSslErrors: true,
                        url: "${i.url}/report/jobStatus?build_id=${this.buildID}&status=FAILURE",
                        validResponseCodes: '200'

                )

                this.context.echo "Status: ${response.status}\nContent: ${response.content}"
            }

            retryWrapper(request)
        }
    }

    def sendSuiteResult(sessionReport, options) {
        try {

            for (i in this.instances) {
                def request = {
                    def response = this.context.httpRequest(
                            acceptType: 'APPLICATION_JSON',
                            customHeaders: [
                                    [name: 'Authorization', value: "Token ${i.token}"]
                            ],
                            httpMode: 'POST',
                            ignoreSslErrors: true,
                            multipartName: 'file',
                            timeout: 900,
                            responseHandle: 'NONE',
                            validResponseCodes: '200',
                            uploadFile: "Results/${this.tool}/session_report.json",
                            url: "${i.url}/report/sessionReport?build_id=${this.buildID}"
                    )
                }
                retryWrapper(request)
            }

        } catch (e) {
            this.context.echo e.toString()
            this.context.echo "RBS: can't send group result."
        }
    }


    def finishBuild(options, status) {
        try {
            String requestData = """
                {
                    "build_id" : "${this.buildID}",
                    "branch": "${this.branchTag}",
                    "tool": "${this.tool}",
                    "status": "${status}",
                    "end_time": "${getTime()}"
                }
            """
            for (i in this.instances) {
                def request = {
                    def response = this.context.httpRequest(

                            acceptType: 'APPLICATION_JSON',
                            consoleLogResponseBody: true,
                            contentType: 'APPLICATION_JSON',
                            customHeaders: [
                                    [name: 'Authorization', value: "Token ${i.token}"]
                            ],
                            httpMode: 'POST',
                            ignoreSslErrors: true,
                            url: "${i.url}/report/end?data=${java.net.URLEncoder.encode(requestData, 'UTF-8')}",
                            validResponseCodes: '200'
                    )


                    this.context.echo "Status: ${response.status}\nContent: ${response.content}"
                }

                retryWrapper(request)
            }
        } catch (e) {
            this.context.echo e.toString()
            this.context.echo "RBS: can't finish build."
        }
    }

    def getTime() {
        def date = new Date()
        def dateFormatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
        return dateFormatter.format(date)
    }
}
