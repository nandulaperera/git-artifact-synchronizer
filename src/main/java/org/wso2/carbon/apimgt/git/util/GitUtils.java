package org.wso2.carbon.apimgt.git.util;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.wso2.carbon.apimgt.git.auth.GitAuth;
import org.wso2.carbon.apimgt.git.constants.GitConstants;
import org.wso2.carbon.apimgt.git.exception.GitException;
import org.wso2.carbon.apimgt.git.model.GitObject;
import org.wso2.carbon.apimgt.git.model.PushStatus;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class GitUtils {

    private static Git git;
    private static final Log log = LogFactory.getLog(GitUtils.class);

    /**
     * Checks whether the repository URL is valid and the credentials are valid
     *
     * @param repositoryURL     The URL of the remote repository
     * @param gitAuth           The Git authentication object
     * @return                  Returns true if the repository is valid and false otherwise
     * @throws GitAPIException
     */
    private static boolean isRepositoryValid(String repositoryURL, GitAuth gitAuth) throws GitAPIException {
        LsRemoteCommand lsRemoteCommand = new LsRemoteCommand(null);
        Collection<Ref> lsResult = lsRemoteCommand.setRemote(repositoryURL)
                .setCredentialsProvider(gitAuth.getAuth()).call();
        return lsResult != null;
    }

    /**
     * Clones the remote repository into a temporary directory.
     *
     * @param repositoryURL         The URL of the remote repository
     * @param gitAuth               The Git authentication object
     * @param branch                The branch name of the remote repository
     * @throws GitException
     */
    public static void cloneRepository(String repositoryURL, GitAuth gitAuth, String branch) throws GitException {
        boolean repositoryValid;
        try {
            repositoryValid = isRepositoryValid(repositoryURL, gitAuth);

            if(repositoryValid) {
                String tempDirectoryName = RandomStringUtils.randomAlphanumeric(5);
                Path localRepositoryDirectory = Files.createTempDirectory(tempDirectoryName);
                File localRepositoryPath = localRepositoryDirectory.toFile();

                if (log.isDebugEnabled()) {
                    log.debug("Cloning remote repository from " + repositoryURL + " into "
                            + localRepositoryPath.getAbsolutePath());
                }

                git = Git.cloneRepository().setURI(repositoryURL)
                        .setDirectory(localRepositoryPath)
                        .setCredentialsProvider(gitAuth.getAuth())
                        .call();
                branch = checkoutBranch(repositoryURL, gitAuth, branch);
                GitObject.init(repositoryURL, localRepositoryPath, gitAuth, branch);
            } else {
                throw new GitException("Invalid Git repository URL or invalid credentials.");
            }
        } catch (GitAPIException | IOException e) {
            throw new GitException(e);
        }
    }

    /**
     * Returns the default branch of the remote repository
     *
     * @param repositoryURL     The URL of the remote repository
     * @param gitAuth           The Git authentication object
     * @return                  The name of the default branch
     * @throws GitAPIException
     * @throws GitException
     */
    private static String getDefaultBranch(String repositoryURL, GitAuth gitAuth)
            throws GitAPIException, GitException {
        Ref head = Git.lsRemoteRepository().setRemote(repositoryURL)
                .setCredentialsProvider(gitAuth.getAuth())
                .callAsMap().get(GitConstants.HEAD);

        if(head == null) {
            throw new GitException("Couldn't find default branch");
        }

        String branchName = head.getTarget().getName();
        return branchName.replace(GitConstants.REF, "");
    }

    /**
     * Checks whether the given branch exists in the remote repository
     *
     * @param repositoryURL     The URL of the remote repository
     * @param gitAuth           The Git authentication object
     * @param branch            The branch name of the remote repository
     * @return                  Returns true if the branch exists and false otherwise
     * @throws GitAPIException
     */
    private static boolean isBranchPublished(String repositoryURL, GitAuth gitAuth, String branch)
            throws GitAPIException {
        Map<String, Ref> allBranches = git.lsRemote().setRemote(repositoryURL)
                .setCredentialsProvider(gitAuth.getAuth())
                .callAsMap();

        Set<String> branchNames = allBranches.keySet();

        boolean branchPublished = false;

        for(String branchName : branchNames){
            Ref ref = allBranches.get(branchName);

            if(!ref.isSymbolic()){
                if(branchName.replace(GitConstants.REF, "").equals(branch)){
                    branchPublished = true;
                    break;
                }
            }
        }
        return branchPublished;
    }

    /**
     * Switches to the given branch. If the branch name is empty, it switches to the default branch.
     *
     * @param repositoryURL     The URL of the remote repository
     * @param gitAuth           The Git authentication object
     * @param branch            The branch name of the remote repository
     * @return                  Returns the branch name of the remote repository.
     *                          If the branch name is empty, returns the name of the default branch
     * @throws GitException
     * @throws GitAPIException
     */
    private static String checkoutBranch(String repositoryURL, GitAuth gitAuth, String branch)
            throws GitException, GitAPIException {
        if(branch == null){
            branch = "";
        }

        boolean isDefaultBranch = false;
        String defaultBranch = getDefaultBranch(repositoryURL, gitAuth);

        if(StringUtils.isEmpty(branch)){
            branch = defaultBranch;
            isDefaultBranch = true;
        } else if(branch.equals(defaultBranch)){
            isDefaultBranch = true;
        }

        if (isBranchPublished(repositoryURL, gitAuth, branch)) {
            git.checkout().setName(branch)
                    // If the branch is not the default branch and the branch exists in the remote repository
                    // create the branch on the local repository
                    .setCreateBranch(!isDefaultBranch)
                    .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
                    .setStartPoint(GitConstants.ORIGIN + "/" + branch).call();
            return branch;
        } else {
            throw new GitException("Invalid branch name or branch doesn't exist.");
        }
    }

    /**
     * Adds the given artifact path to the staging area
     *
     * @param gitObject
     * @param artifactPath      The path of the artifact relative to the working directory (cloned repository path)
     * @throws GitException
     */
    public static void addFile(GitObject gitObject, String artifactPath) throws GitException {
        String localRepositoryPath = gitObject.getLocalRepositoryPath().getAbsolutePath();
        File artifact = new File(localRepositoryPath + GitConstants.FILE_SEPARATOR + artifactPath);

        if(artifact.exists()) {
            try {
                AddCommand addCommand = git.add().addFilepattern(".");
                //If setUpdate is set to true, only the tracked files are added to the staging area
                addCommand = addCommand.setUpdate(false);
                addCommand.call();
            } catch (GitAPIException e) {
                throw new GitException(e);
            }
        }
    }

    /**
     * Removes the given artifact path from the working directory (cloned repository path)
     *
     * @param gitObject
     * @param artifactPath      The path of the artifact relative to the working directory (cloned repository path)
     * @return
     * @throws GitException
     */
    public static boolean removeFile(GitObject gitObject, String artifactPath) throws GitException {
        String localRepositoryPath = gitObject.getLocalRepositoryPath().getAbsolutePath();
        File artifact = new File(localRepositoryPath + GitConstants.FILE_SEPARATOR + artifactPath);

        if(artifact.exists()){
            try {
                git.rm().addFilepattern(artifactPath).call();
                return true;
            } catch (GitAPIException e) {
                throw new GitException(e);
            }
        }
        return false;
    }

    /**
     * Commits all the changed files
     *
     * @param commitMessage
     * @throws GitAPIException
     */
    private static void commitChanges(String commitMessage) throws GitAPIException {
        CommitCommand commitCommand = git.commit().setMessage(commitMessage);
        //If setAll is set to true, it automatically stages files that have been modified and deleted.
        commitCommand = commitCommand.setAll(true);
        commitCommand.call();
    }

    /**
     * Adds a new remote
     *
     * @param gitObject
     * @throws URISyntaxException
     * @throws GitAPIException
     */
    private static void remoteAdd(GitObject gitObject) throws URISyntaxException, GitAPIException {
        RemoteAddCommand remoteAddCommand = git.remoteAdd().
                setUri(new URIish(gitObject.getRepositoryURL()));
        remoteAddCommand = remoteAddCommand.setName(GitConstants.ORIGIN);
        remoteAddCommand.call();
    }

    /**
     * Pull and rebase changes from the remote repository
     *
     * @param gitObject
     * @return                  Returns true if the pull is successful and false otherwise
     * @throws GitAPIException
     */
    public static boolean pullChanges(GitObject gitObject) throws GitException {
        boolean pullSuccessful = false;
        try {
            PullResult pullResult = git.pull()
                    .setCredentialsProvider(gitObject.getGitAuth().getAuth())
                    .setRebase(true).call();
            pullSuccessful = pullResult.isSuccessful();
        } catch (GitAPIException e) {
            throw new GitException("Error while pulling changes from the remote repository.", e);
        }
        return pullSuccessful;
    }

    /**
     * Push changes to the remote repository
     *
     * @param gitObject
     * @return
     * @throws GitAPIException
     */
    private static PushStatus pushChanges(GitObject gitObject) throws GitAPIException {
        Iterable<PushResult> pushResults = git.push().setRemote(gitObject.getRepositoryURL())
                .setCredentialsProvider(gitObject.getGitAuth().getAuth())
                .setPushAll().add(".").call();
        PushStatus pushStatus = null;
        if(pushResults.iterator().hasNext()){
            PushResult pushResult = pushResults.iterator().next();
            RemoteRefUpdate remoteUpdate = pushResult.getRemoteUpdate(GitConstants.REF + gitObject.getBranch());
            String newObjectHash = remoteUpdate.getNewObjectId().getName();
            boolean pushSuccessful = remoteUpdate.getStatus().toString().equals(RemoteRefUpdate.Status.OK.toString());
            pushStatus = new PushStatus(newObjectHash, pushSuccessful);
        }
        return pushStatus;
    }

    /**
     * Commits and pushes all the changes to the remote repository
     *
     * @param gitObject
     * @param commitMessage
     * @throws GitException
     */
    public static void pushToRepository(GitObject gitObject, String commitMessage) throws GitException{
        try {
            commitChanges(commitMessage);
        } catch (GitAPIException e) {
            throw new GitException("Error while committing changes to the repository.", e);
        }

        try {
            remoteAdd(gitObject);
        } catch (URISyntaxException | GitAPIException e) {
            throw new GitException("Error while adding a new remote.", e);
        }

        try {
            PushStatus pushStatus = pushChanges(gitObject);
            if(pushStatus != null){
                if(pushStatus.isPushSuccessful()){
                    log.info(commitMessage);
                    log.info("Artifact changes pushed to remote repository successfully with commit hash: "
                            + pushStatus.getCommitHash());
                } else {
                    log.error("Pushing artifact changes to remote repository failed");
                }
            } else {
                log.error("Pushing artifact changes to remote repository failed");
            }
        } catch (GitAPIException e) {
            throw new GitException("Error while pushing artifact changes to the remote repository.", e);
        }
    }

    /**
     * Generates the path of the inactive directory of the given artifact
     *
     * @param artifactName  Name of the artifact
     * @param version       Version of the artifact
     * @return              Returns the path of the inactive directory of the artifact
     */
    public static String getInactiveArtifactDirectory(String artifactName, String version) {
        return GitConstants.INACTIVE_DIR + GitConstants.FILE_SEPARATOR + artifactName + "-" + version;
    }

    /**
     * Generates the name of the ZIP artifact to be added/removed to/from the remote repository
     *
     * @param apiId
     * @param name
     * @param version
     * @param revision
     * @param organization
     * @return              The name of the ZIP artifact
     */
    public static String getArtifactName(String apiId, String name, String version, String revision,
                                         String organization){
        String newApiId = apiId.split("-")[0];
        String newRevision = revision.split("-")[0];
        return newApiId + "-" + name + "-" + version + "-" + newRevision + "-" + organization + GitConstants.ZIP;
    }
}
