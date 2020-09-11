def call(String type, String main_branch){
    println(type)
    println(main_branch)
    node('UMS') {
        dir("${type}/universe") {
            checkOutBranchOrScm(main_branch, 'https://gitlab.cts.luxoft.com/dm1tryG/universe.git', , false, false, true, 'radeonprorender-gitlab', false)
        }
    }
}