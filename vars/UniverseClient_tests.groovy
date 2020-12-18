def test_create_build() {
    String UMS_URL = "http://172.26.157.233:5002"
    String IS_URL = "http://172.26.157.233:8001"
    String product_name = "AMD%20Radeon™%20ProRender%20for%20Maya"
    
    parent = new UniverseClient(this, UMS_URL, env, product_name)
    parent.createBuild('', '', false, ["projectRepo":"https://github.com"])
    
    child = new UniverseClient(this, UMS_URL, env, IS_URL, product_name, 'NorthStar', parent)
    child.createBuild(["Windows-AMD", "Windows-OSX"], ["Smoke", "Sanity"], false)
}

def call() {
    node("UMS") {
        test_create_build()
    }
}
