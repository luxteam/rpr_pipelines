def test_create_build() {
    withCredentials([
        string(credentialsId: 'testing2UniverseURL', variable: 'TEST2_UMS_URL'),
        string(credentialsId: 'imageServiceURL', variable: 'IS_URL')
    ]) {
        String UMS_URL  = "${TEST2_UMS_URL}"
        String IS_URL = "${IS_URL}"

        String product_name = "AMD%20Radeon™%20ProRender%20for%20Maya"

        parent = new UniverseClient(this, UMS_URL, env, product_name)
        parent.tokenSetup()
        parent.createBuild('', '', false, ["projectRepo":"https://github.com"])

        child = new UniverseClient(this, UMS_URL, env, IS_URL, product_name, 'NorthStar', parent)
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
