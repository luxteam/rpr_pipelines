import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import hudson.plugins.git.GitException
import hudson.AbortException


def call(String branchName, String repoName, Boolean disableSubmodules=false, String prBranchName = '', String prRepoName = '', Boolean polling=false, Boolean changelog=true, \
    String credId='radeonprorender', Boolean useLFS=false, Boolean wipeWorkspace=false) {
    
    try {
        executeCheckout(branchName, repoName, disableSubmodules, prBranchName, prRepoName, polling, changelog, credId, useLFS)
    } catch (FlowInterruptedException e) {
        println "[INFO] Task was aborted during checkout"
        throw e
    } catch (e) {
        println(e.toString())
        println(e.getMessage())
        
        if (useLFS) {
            println "[ERROR] Failed to checkout git LFS on ${env.NODE_NAME}. Sleeping and trying again."
            sleep(30)
        } else {
            println "[ERROR] Failed to checkout git on ${env.NODE_NAME}. Cleaning workspace and try again."
            cleanWS()
            wipeWorkspace=true
        }
        
        executeCheckout(branchName, repoName, disableSubmodules, prBranchName, prRepoName, polling, changelog, credId, useLFS, wipeWorkspace)
    }
}


def executeCheckout(String branchName, String repoName, Boolean disableSubmodules=false, String prBranchName = '', String prRepoName = '', Boolean polling=false, Boolean changelog=true, \
    String credId='radeonprorender', Boolean useLFS=false, Boolean wipeWorkspace=false) {

    def repoBranch = branchName ? [[name: branchName]] : scm.branches

    println "Checkout branch: ${repoBranch}; repo: ${repoName}"
    println "Submodules processing: ${!disableSubmodules}"
    println "Include in polling: ${polling}; Include in changelog: ${changelog}"
    if (prBranchName) {
        println "PR commit: ${prBranchName}; PR repo: ${prRepoName}"
    }

    List configs = [[credentialsId: "${credId}", url: "${repoName}"]]
    def checkoutExtensions = [
            [$class: 'PruneStaleBranch'],
            [$class: 'CleanBeforeCheckout'],
            [$class: 'CleanCheckout', deleteUntrackedNestedRepositories: true],
            [$class: 'CheckoutOption', timeout: 30],
            [$class: 'AuthorInChangelog'],
            [$class: 'CloneOption', timeout: 60, noTags: false],
            [$class: 'SubmoduleOption', disableSubmodules: disableSubmodules,
             parentCredentials: true, recursiveSubmodules: true, shallow: true, depth: 2,
             timeout: 60, reference: '', trackingSubmodules: false],
    ]
    if (prBranchName) {
        if (prRepoName && repoName != prRepoName) {
            configs.add([credentialsId: "${credId}", url: "${prRepoName}", name: 'remoteRepo'])
            checkoutExtensions.add([$class: 'PreBuildMerge', options: [mergeTarget: prBranchName, mergeRemote: 'remoteRepo']])
        } else {
            checkoutExtensions.add([$class: 'PreBuildMerge', options: [mergeTarget: prBranchName, mergeRemote: 'origin']])
        }
    }

    if (useLFS) checkoutExtensions.add([$class: 'GitLFSPull'])
    if (wipeWorkspace) checkoutExtensions.add([$class: 'WipeWorkspace'])

    // !branchName need for ignore merging testing repos (jobs_test_*) 
    if (!branchName && env.BRANCH_NAME && env.BRANCH_NAME.startsWith("PR-")) {

        // TODO: adapt scm options for PR
        checkout scm

    } else {
        checkout changelog: changelog, poll: polling,
            scm: [$class: 'GitSCM', branches: repoBranch, doGenerateSubmoduleConfigurations: false,
                    extensions: checkoutExtensions,
                    submoduleCfg: [],
                    userRemoteConfigs: configs
                ]
    }
    
}