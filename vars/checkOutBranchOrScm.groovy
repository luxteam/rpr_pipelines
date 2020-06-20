import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import hudson.plugins.git.GitException
import hudson.AbortException

def call(String branchName, String repoName, Boolean disableSubmodules=false, Boolean polling=false, Boolean changelog=true, \
    String credId='radeonprorender', Boolean useLFS=false, Boolean wipeWorkspace=false) {
    
    try {
        executeCheckout(branchName, repoName, disableSubmodules, polling, changelog, credId, useLFS)
    } 
    catch (FlowInterruptedException e) 
    {
        println "[INFO] Task was aborted during checkout"
        throw e
    }
    catch (e) 
    {
        println(e.toString())
        println(e.getMessage())
        println "[ERROR] Failed to checkout git on ${env.NODE_NAME}. Cleaning workspace and try again."
        cleanWS()
        executeCheckout(branchName, repoName, disableSubmodules, polling, changelog, credId, useLFS, true)
    }
}


def executeCheckout(String branchName, String repoName, Boolean disableSubmodules=false, Boolean polling=false, Boolean changelog=true, \
    String credId='radeonprorender', Boolean useLFS=false, Boolean wipeWorkspace=false) {

    def repoBranch = branchName ? [[name: branchName]] : scm.branches

    echo "checkout branch: ${repoBranch}; repo: ${repoName}"
    echo "Submodules processing: ${!disableSubmodules}"
    echo "Include in polling: ${polling}; Include in changelog: ${changelog}"

    def checkoutExtensions = [
            [$class: 'PruneStaleBranch'],
            [$class: 'CleanBeforeCheckout'],
            [$class: 'CleanCheckout', deleteUntrackedNestedRepositories: true],
            [$class: 'CheckoutOption', timeout: 30],
            [$class: 'AuthorInChangelog'],
            [$class: 'CloneOption', timeout: 60, noTags: false],
            [$class: 'SubmoduleOption', disableSubmodules: disableSubmodules,
             parentCredentials: true, recursiveSubmodules: true, shallow: true, depth: 2,
             timeout: 60, reference: '', trackingSubmodules: false]
    ]

    if (useLFS) checkoutExtensions.add([$class: 'GitLFSPull'])
    if (wipeWorkspace) checkoutExtensions.add([$class: 'WipeWorkspace'])

    // !branchName need for ignore merging testing repos (jobs_test_*) 
    if (env.CHANGE_ID) {

        // TODO: adapt scm options for PR
        checkout scm

    } else {
        checkout changelog: changelog, poll: polling,
            scm: [$class: 'GitSCM', branches: repoBranch, doGenerateSubmoduleConfigurations: false,
                    extensions: checkoutExtensions,
                    submoduleCfg: [],
                    userRemoteConfigs: [[credentialsId: "${credId}", url: "${repoName}"]]
                ]
    }
    
}