def call(String type, String main_branch):
    node('UMS') {
        dir('/home/radmin/ums/${type}/universe') {
            checkOutBranchOrScm(main_branch, 'https://gitlab.cts.luxoft.com/dm1tryG/universe.git')
        }
    }
