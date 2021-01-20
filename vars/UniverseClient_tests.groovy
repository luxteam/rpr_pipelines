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

        sh """
            ./run_ums_tests.sh >> ../tests.log 2>&1
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
