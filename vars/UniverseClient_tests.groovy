def test_create_build() {
    withCredentials([
        string(credentialsId: 'testing2UniverseURL', variable: 'TEST2_UMS_URL'),
        string(credentialsId: 'imageServiceURL', variable: 'IS_URL')
    ]) {
        println('Wrapper testing')

        // configuration
        String umsURL  = "${TEST2_UMS_URL}"
        String isURL = "${IS_URL}"
        String productName = "AMD%20Radeon™%20ProRender%20for%20Maya"

        
        parent = new UniverseClient(this, umsURL, env, productName)
        parent.tokenSetup()
        parent.createBuild('', '', false, ["projectRepo":"https://github.com"])

        child1 = new UniverseClient(this, umsURL, env, isURL, productName, 'Northstar', parent)
        child1.tokenSetup()
        child1.createBuild(["Windows-AMD", "OSX-AMD_7100"], ["Smoke", "Sanity"], false)
        child1.changeStatus("SUCCESS")
        
        cleanWS("Ubuntu")
        checkOutBranchOrScm('ums_tests', 'https://github.com/luxteam/jobs_launcher.git')

        def UMS_URL='https://umsapi2.cistest.luxoft.com'
        def UMS_JOB_ID=child1.build.job_id
        def UMS_ENV_LABEL='Windows-AMD'
        def UMS_LOGIN='dm1tryG'
        def UMS_PASSWORD='root'
        def UMS_BUILD_ID=child1.build.id

        sh """
            sudo sh run_ums_tests.sh ${UMS_URL} ${UMS_JOB_ID} ${UMS_ENV_LABEL} ${UMS_LOGIN} ${UMS_PASSWORD} ${UMS_BUILD_ID}>> ../tests.log 2>&1
        """

        child2 = new UniverseClient(this, umsURL, env, isURL, productName, 'Tahoe', parent)
        child2.tokenSetup()
        child2.createBuild(["Windows-AMD", "OSX-AMD_7100"], ["Smoke", "Sanity"], false)
        child2.changeStatus("SUCCESS")

        parent.changeStatus("SUCCESS")
    }
}

def call() {
    node("UMS") {
        test_create_build()
    }
}
