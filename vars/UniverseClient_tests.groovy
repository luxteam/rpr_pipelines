def test_create_build() {
    withCredentials([
        string(credentialsId: 'testing2UniverseURL', variable: 'TEST2_UMS_URL'),
        string(credentialsId: 'imageServiceURL', variable: 'IS_URL')
    ]) {
        // configuration
        String umsURL  = "${TEST2_UMS_URL}"
        String isURL = "${IS_URL}"
        String productName = "AMD%20Radeon™%20ProRender%20for%20Maya"
        String UMS_URL='https://umsapi2.cistest.luxoft.com'
        String UMS_JOB_ID=child1.build.job_id
        String UMS_ENV_LABEL='Windows-AMD'
        String UMS_LOGIN='dm1tryG'
        String UMS_PASSWORD='root'
        String UMS_BUILD_ID=child1.build.id
        def TEST_FILTER=['Smoke', 'Sanity']
        def ENVS=["Windows-AMD", "OSX-AMD"]
        
        parent = new UniverseClient(this, umsURL, env, productName)
        parent.tokenSetup()
        parent.createBuild('', '', false, ["projectRepo":"https://github.com"])

        child1 = new UniverseClient(this, umsURL, env, isURL, productName, 'Northstar', parent)
        child1.tokenSetup()
        child1.createBuild(ENVS, TEST_FILTER, false)

        // child2 = new UniverseClient(this, umsURL, env, isURL, productName, 'Tahoe', parent)
        // child2.tokenSetup()
        // child2.createBuild(["Windows-AMD"], ["Smoke"], false)
        
        cleanWS("Ubuntu")
        checkOutBranchOrScm('ums_tests', 'https://github.com/luxteam/jobs_launcher.git')

        ENVS.each { env_label ->
            sh """
                sudo sh run_ums_tests.sh ${UMS_URL} ${UMS_JOB_ID} ${env_label} ${UMS_LOGIN} ${UMS_PASSWORD} ${UMS_BUILD_ID} ${TEST_FILTER.join(',')}>> ../tests.log 2>&1
            """
        }
        
        
        // child2.changeStatus("SUCCESS")
        child1.changeStatus("SUCCESS")
        parent.changeStatus("SUCCESS")
    }
}

def call() {
    node("UMS") {
        test_create_build()
    }
}
