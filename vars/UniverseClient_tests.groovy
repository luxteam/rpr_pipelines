def test_create_build(parent, child) {
    parent.createBuild('', '', false, options)
    child.createBuild(["Windows-AMD", "Windows-OSX"], ["Smoke", "Sanity"], false)
}

def call() {
    node("UMS") {
        String UMS_URL = "http://172.26.157.233:5002"
        String IS_URL = "http://172.26.157.233:8001"
        String product_name = "AMD Radeon™ ProRender For Blender"
        parent_uc = new UniverseClient(this, UMS_URL, env, IS_URL, product_name)
        child_uc = new UniverseClient(this, UMS_URL, env, IS_URL, product_name, 'NorthStar ', parent_uc)
        test_create_build(parent_uc, child_uc)
    }
}
