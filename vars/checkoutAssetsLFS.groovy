def call(String osName, String repository, String branchName, String destinationPath) {
    ws("${destinationPath}") {
//        git config --global credential.username {GIT_USERNAME}
//        git config --global credential.helper "!echo password={GITPASSWORD}; echo"
        switch (osName) {
            case "Windows":
                bat """
                IF NOT EXIST .git (
                    echo "clear current dir"
                    del *
                    FOR /D %%p IN (".") DO rmdir "%%p" /s /q
                    echo "clone"
                    git clone -v -j 4 --branch ${branchName} ${repository} .

                )
                ELSE (
                    echo "git repo clean"
                    git clean -fdx
                    git reset --hard HEAD
                )
                echo "Checkout to ${branchName} with resetting current state"
                git checkout ${branchName}
                git pull -v --prune --force -j 4 origin ${branchName}                
                """
                break;
            default:
                sh """
                if [ ! -d ".git" ]
                then
                    echo "clear current dir"
                    rm -fdr *
                    echo "clone"
                    git clone -v -j 4 --branch ${branchName} ${repository} .
                else
                    echo "git repo clean"
                    git clean -fdx
                    git reset --hard HEAD
                fi
                echo "Checkout to ${branchName} with resetting current state"
                git checkout ${branchName}
                git pull -v --prune --force -j 4 origin ${branchName}                
                """
        }
    }
}