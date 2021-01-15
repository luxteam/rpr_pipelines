import groovy.json.JsonOutput;
import groovy.json.JsonSlurperClassic;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
// imports for work with JSONs

/**
 * Client for Universal Monitoring System
 */
class UniverseClient {
    def context;
    def url;
    def product;
    def token;
    def build;
    def env;
    def is_url;
    def is_parent;
    def engine;
    def child_of;
    def url_map = [
        "http://172.26.157.233:5001": "https://ums.cistest.luxoft.com",
        "http://172.26.157.233:5002": "https://ums2.cistest.luxoft.com",
        "http://172.26.157.233:5003": "https://ums3.cistest.luxoft.com",
        "http://172.26.157.248:5000": "https://ums.stvcis.luxoft.com",
    ]
    
    def major_keys = [
        [
            "key": "projectRepo",
            "name": "project repository"
        ],
        [
            "key": "projectBranch",
            "name": "project branch"
        ],
        [
            "key": "testsBranch",
            "name": "tests branch"
        ],
        [
            "key": "platforms",
            "name": "gpu"
        ],
        [
            "key": "parallelExecutionTypeString",
            "name": "parallel execution type string"
        ],
        [
            "key": "customBuildLinkWindows",
            "name": "custom build link windows"
        ],
        [
            "key": "customBuildLinkLinux",
            "name": "custom build link linux"
        ],
        [
            "key": "customBuildLinkOSX",
            "name": "custom build link osx"
        ],
        [
            "key": "toolVersion",
            "name": "tool version"
        ],
        [
            "key": "updateRefs",
            "name": "update references"
        ],
        [
            "key": "renderDevice",
            "name": "render device"
        ],
        [
            "key": "enginesNames",
            "name": "render engines"
        ],
        [
            "key": "testsPackage",
            "name": "tests package"
        ],
        [
            "key": "tests",
            "name": "tests"
        ],
        [
            "key": "enableNotifications",
            "name": "enable notification"
        ],
        [
            "key": "resX",
            "name": "resolution x"
        ],
        [
            "key": "resY",
            "name": "resolution y"
        ],
        [
            "key": "SPU",
            "name": "SPU"
        ],
        [
            "key": "iter",
            "name": "iterations"
        ],
        [
            "key": "theshold",
            "name": "threshold"
        ],
        [
            "key": "TESTER_TAG",
            "name": "tester tag"
        ],
        [
            "key": "testCaseRetries",
            "name": "test case retries"
        ],
        [
            "key": "prRepoName",
            "name": "PR Repository Name"
        ],
        [
            "key": "prBranchName",
            "name": "PR Repository Name"
        ]
    ]

    def minor_keys = [
        [
            "key": "isPreBuilt",
            "name": "is prebuilt"
        ],
        [
            "key": "forceBuild",
            "name": "force build"
        ],
        [
            "key": "splitTestsExecution",
            "name": "split tests execution"
        ],
        [
            "key": "TEST_TIMEOUT",
            "name": "test timeout"
        ],
        [
            "key": "gpusCount",
            "name": "gpus count"
        ],
        [
            "key": "ADDITIONAL_XML_TIMEOUT",
            "name": "additional xml timeout"
        ],
        [
            "key": "NON_SPLITTED_PACKAGE_TIMEOUT",
            "name": "non splitted package timeout"
        ],
        [
            "key": "DEPLOY_TIMEOUT",
            "name": "deploy timeout"
        ],
        [
            "key": "incrementVersion",
            "name": "increment version",
        ],
        [
            "key": "BUILDER_TAG",
            "name": "builder tag"
        ]
    ]

    def info_keys = [
        "pluginVersion",
        "commitAuthor",
        "commitMessage",
        "commitSHA"
    ]
    

    /**
     * Main constructor for builds without engine
     *
     * @param context
     * @param url Universal Monitoring System API  url
     * @param env Jenkins environment variables object
     * @param is_url Image Service API url
     * @param product Name of product (example: "RPR_Maya")
     */
    UniverseClient(context, url, env, is_url, product) {
        this.url = url;
        this.context = context;
        this.env = env;
        this.is_url = is_url;
        this.product = product;
        this.child_of = false;
        this.is_parent = false;
    }

    /**
     * Constructor for creation of parent build
     *
     * @param context
     * @param url Universal Monitoring System API  url (example: "https://umsapi.cis.luxoft.com" )
     * @param env Jenkins environment variables object
     * @param product Name of product (example: "RPR_Maya")
     */
    UniverseClient(context, url, env, product) {
        this.url = url;
        this.context = context;
        this.env = env;
        this.product = product;
        this.is_parent = true;
    }

    /**
     * Constructor for creation of child build
     *
     * @param context
     * @param url Universal Monitoring System API  url (example: "https://umsapi.cis.luxoft.com" )
     * @param env Jenkins environment variables object
     * @param is_url Image Service API url (example: "https://imgs.cis.luxoft.com")
     * @param product Name of product (example: "RPR_Maya")
     * @param engine Is that build a parent of some other build or not
     * @param child_of UniverseClient which is a parent of creating build
     */
    UniverseClient(context, url, env, is_url, product, engine, child_of) {
        this(context, url, env, is_url, product)
        this.is_parent = false;
        this.engine = engine;
        this.child_of = child_of.build.id;
        this.token = child_of.token;
    }

    /**
     * function retry wrapper for request function (required to return response)
     *
     * @param func function to be retried
     * @param validResponseCodes response codes that will be indicated as OK
     */
    def retryWrapper(func, validResponseCodes = [200], allowAborting = true) {
        def attempt = 0
        def attempts = 5
        def timeout = 30 // seconds

        this.context.waitUntil {
            if (attempt == attempts) {
                this.context.println("End attemts")
                return true //break the loop
            }

            attempt++
            this.context.println("Attempt: ${attempt}")

            try {
                def response = func.call()
                if( validResponseCodes.contains(response.status)){
                    return true //break the loop
                }
                switch(response.status){
                    case 401:
                        this.tokenSetup()
                        break;
                }
                return false // continue loop
            } catch(FlowInterruptedException error) {
                this.context.println("[INFO] Detected aborting during sending of request to UMS")
                if (allowAborting) {
                    this.context.println("[INFO] Aborting sending of request to UMS...")
                    throw error
                } else {
                    this.context.println("[INFO] Aborting was ignored")
                    attempt--
                    return false // continue loop
                }
            } catch(Exception error) {
                this.context.println(error)
                this.context.sleep(timeout)
                return false // continue loop
            }
        }
    }

    /**
     * Setup UMS authorization token
     */
    def tokenSetup() {
        def response = this.context.httpRequest(
            consoleLogResponseBody: true,
            authentication: 'universeMonitoringSystem',
            httpMode: 'POST',
            url: "${this.url}/user/login",
            validResponseCodes: '200'
        )
        def token = this.context.readJSON text: "${response.content}"
        this.token = "${token['token']}"
    }

    /**
     * Create build in UMS API and set up build id variable in current client
     *
     * @param envs environment list in format: ["OS-1:GPU-1", ..."OS-N:GPU-N"]
     * @param suites suites names list ["Suite1", "Suite2", ..., "SuiteN"]
     * @param updRefs boolean value for update baselines on UMS side after build finish
     * @param parameters parameters map: [
        "major": ["parameter1": "value1", ... , "parameterN": "valueN"],
        "minor": ["parameter1": "value1", ... , "parameterN": "valueN"]
     ]
     * @param info info map ["key1": "value1", ... , "keyN": "valueN"]
     */

    def createBuild(envs = '', suites = '', updRefs = false, options = null) {
        def request = {
            // prepare build parameters
            def parameters = [:]
            def info = [:]
            if (options) {
                for (pType in [this.minor_keys, this.major_keys]) {
                    for (p in pType) {
                        p['value'] = options[p['key']]
                    }
                }

                parameters = [
                    "minor": this.minor_keys,
                    "major": this.major_keys
                ]

                println(parameters)
                // prepare build info
                info = [:]
                for (key in this.info_keys) {info[key] = options[key]}
            }

            def splittedJobName = []
            splittedJobName = new ArrayList<>(Arrays.asList(env.JOB_NAME.split("/", 2)))
            this.context.echo "SPLITTED JOB NAME = ${splittedJobName}"
            this.context.echo "JOB_NAME = ${splittedJobName[0]}"

            def references = [
                "jenkins_report": "${env.JENKINS_URL}job/${env.JOB_NAME}/${env.BUILD_NUMBER}"
            ]

            def tags = []

            String tag = "Other"
            String jobName = splittedJobName[0].toLowerCase()
            def POSSIBLE_TAGS = ["Weekly", "Auto", "Manual"]
            for (tagName in POSSIBLE_TAGS) {
                if (jobName.contains(tagName.toLowerCase())) {
                    tag = tagName
                    break
                }
            }

            tags << tag
            splittedJobName.remove(0)
            splittedJobName.each {
                tags << "${it}"
            }

            def buildBody

            if (this.is_parent) {
                buildBody = [
                    'name': env.BUILD_NUMBER,
                    'parent': true,
                    'tags': tags
                ]
            } else {
                buildBody = [
                    'name': env.BUILD_NUMBER,
                    'envs': envs,
                    'tags': tags,
                    'suites': suites
                ]
                if (this.engine) {
                    buildBody['engine'] = this.engine
                    buildBody['child_of'] = this.child_of
                }
            }

            
            buildBody['upd_baselines'] = updRefs
            buildBody['parameters'] = parameters
            buildBody['references'] = references
            buildBody['info'] = info
            
            

            def res = this.context.httpRequest(
                consoleLogResponseBody: true,
                contentType: 'APPLICATION_JSON',
                customHeaders: [
                    [name: 'Authorization', value: "Token ${this.token}"]
                ],
                httpMode: 'POST',
                requestBody: JsonOutput.toJson(buildBody),
                ignoreSslErrors: true,
                url: "${this.url}/api/build?jobName=${this.product}",
                validResponseCodes: '0:599'
            )
            
            def jsonSlurper = new JsonSlurperClassic()
            def content = jsonSlurper.parseText(res.content);
            this.build = content["build"];
            this.context.echo content["msg"];
            
            if (this.is_parent || (!this.child_of && !this.is_parent)) {
                if (this.context.currentBuild.description == null) {
                    this.context.currentBuild.description = ""
                }
        
                this.context.currentBuild.description += "<br><a href='${this.url}/products/${this.build.job_id}/${this.build.id}/summary' target='_blank'>UMS Report (${this.url})</a>"
            }

            return res;
        }
        retryWrapper(request)
    }

    /**
     * Create stage in UMS API of current build
     *
     * @param name Stage name
     * @param action String type of action (only "begin" or "end" possible)
     */
    def stage(name, action) {
        def request = {
            def buildBody = [
                'stage': [
                    'name': name,
                    'action': action
                ]
            ]

            def res = this.context.httpRequest(
                consoleLogResponseBody: true,
                contentType: 'APPLICATION_JSON',
                customHeaders: [
                    [name: 'Authorization', value: "Token ${this.token}"]
                ],
                httpMode: 'PUT',
                requestBody: JsonOutput.toJson(buildBody),
                ignoreSslErrors: true,
                url: "${this.url}/api/build?id=${this.build["id"]}&jobId=${this.build["job_id"]}",
                validResponseCodes: '0:599'
            )
            return res;
        }
        retryWrapper(request)
    }

    /**
     * Change build status on UMS API
     *
     * @param status String Status name
     */
    def changeStatus(status) {
        this.context.println("[INFO] START TO CHANGE UMS BUILD STATUS. PLEASE, DO NOT ABORT JOB")
        def request = {
            
            status = status ?: "SUCCESS"
            
            // TODO: fix this statuses duct tape
            // or just move into seporated module/function
            
            def mapStatuses = [
                "FAILURE": "error",
                "FAILED": "error",
                "UNSTABLE": "failed",
                "ABORTED": "aborted",
                "SUCCESS": "passed",
                "SUCCESSFUL": "passed"
            ]
            
            this.context.println("[INFO] Sending build status - \"${mapStatuses[status]}\"")

            def buildBody = [
                'status': mapStatuses[status]
            ]

            def res = this.context.httpRequest(
                consoleLogResponseBody: true,
                contentType: 'APPLICATION_JSON',
                customHeaders: [
                    [name: 'Authorization', value: "Token ${this.token}"]
                ],
                httpMode: 'PUT',
                requestBody: JsonOutput.toJson(buildBody),
                ignoreSslErrors: true,
                url: "${this.url}/api/build?id=${this.build["id"]}&jobId=${this.build["job_id"]}",
                validResponseCodes: '0:599'
            )

            return res;
        }
        retryWrapper(request, [200], false)
    }


    def problemMessage(msg) {
        def request = {
            def buildBody = [
                'problem_message': msg
            ]

            def res = this.context.httpRequest(
                consoleLogResponseBody: true,
                contentType: 'APPLICATION_JSON',
                customHeaders: [
                    [name: 'Authorization', value: "Token ${this.token}"]
                ],
                httpMode: 'PUT',
                requestBody: JsonOutput.toJson(buildBody),
                ignoreSslErrors: true,
                url: "${this.url}/api/build?id=${this.build["id"]}&jobId=${this.build["job_id"]}",
                validResponseCodes: '0:599'
            )
            return res;
        }
        retryWrapper(request)
    }
}