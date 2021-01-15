def test_create_build() {
    withCredentials([
        string(credentialsId: 'testing2UniverseURL', variable: 'TEST2_UMS_URL'),
        string(credentialsId: 'imageServiceURL', variable: 'IS_URL')
    ]) {
        
        String umsURL  = "${TEST2_UMS_URL}"
        String isURL = "${IS_URL}"
        String productName = "AMD%20Radeon™%20ProRender%20for%20Maya"

        parent = new UniverseClient(this, umsURL, env, productName)
        parent.tokenSetup()
        parent.createBuild('', '', false, ["projectRepo":"https://github.com"])

        child = new UniverseClient(this, umsURL, env, isURL, productName, 'NorthStar', parent)
        child.tokenSetup()
        child.createBuild(["Windows-AMD", "Windows-OSX"], ["Smoke", "Sanity"], false)
        child.changeStatus("SUCCESS")
        
        parent.changeStatus("SUCCESS")
    }
}

def call() {
    node("UMS") {
        test_create_build()
    }
}
