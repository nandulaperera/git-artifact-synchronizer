package org.wso2.carbon.apimgt.git;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.git.auth.GitAuth;
import org.wso2.carbon.apimgt.git.constants.GitConstants;
import org.wso2.carbon.apimgt.git.exception.GitException;
import org.wso2.carbon.apimgt.git.model.GitObject;
import org.wso2.carbon.apimgt.git.util.GitUtils;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.ArtifactSaver;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.exception.ArtifactSynchronizerException;

import java.io.File;
import java.io.IOException;

public class GitSaver implements ArtifactSaver {
    private static GitObject gitObject;
    private static final Log log = LogFactory.getLog(GitSaver.class);

    public void init() throws ArtifactSynchronizerException {
        String repositoryURL = System.getProperty("GitRepoURL");
        String username = System.getProperty("GitUsername");
        String accessToken = System.getProperty("GitAccessToken");
        String branch = System.getProperty("GitRepoBranch");

        GitAuth.init(username, accessToken);
        GitAuth gitAuth = GitAuth.getInstance();

        try {
            GitUtils.cloneRepository(repositoryURL, gitAuth, branch);
        } catch (GitException e) {
            log.error("Error while cloning the repository. ", e);
            throw new ArtifactSynchronizerException("Error while cloning the repository. ", e);
        }
        gitObject = GitObject.getInstance();

        log.info("Remote repository cloned from " + gitObject.getRepositoryURL());
    }

    public void saveArtifact(String apiId, String name, String version, String revision,
                             String organization, File artifact) throws ArtifactSynchronizerException {
        boolean pullSuccessful;
        try {
            pullSuccessful = GitUtils.pullChanges(gitObject);
        } catch (GitException e) {
            log.error(e);
            throw new ArtifactSynchronizerException(e);
        }

        if(pullSuccessful){
            String artifactPath;
            try {
                String artifactName = GitUtils.getArtifactName(apiId, name, version, revision, organization);
                String inactiveArtifactDirectory = GitUtils.getInactiveArtifactDirectory(name, version);
                artifactPath = inactiveArtifactDirectory + GitConstants.FILE_SEPARATOR + artifactName;

                File localRepositoryPath = gitObject.getLocalRepositoryPath();
                File newArtifact = new File(localRepositoryPath + GitConstants.FILE_SEPARATOR + artifactPath);

                FileUtils.copyFile(artifact, newArtifact);
            } catch (IOException e) {
                log.error("Error while copying the artifact to the local repository.", e);
                throw new ArtifactSynchronizerException("Error while copying the artifact to the local repository.", e);
            }

            try {
                GitUtils.addFile(gitObject, artifactPath);
            } catch (GitException e) {
                log.error("Error while adding changes to the staging area.", e);
                throw new ArtifactSynchronizerException("Error while adding changes to the staging area.", e);
            }

            String revisionId = revision.split("-")[0];
            String commitMessage = "Revision Created - " + name + ":" + version + ":" + organization + " - " + revisionId;

            try {
                GitUtils.pushToRepository(gitObject, commitMessage);
            } catch (GitException e) {
                log.error("Error while pushing changes to the remote repository.", e);
                throw new ArtifactSynchronizerException("Error while pushing changes to the remote repository.", e);
            }
        }
    }

    public void removeArtifact(String apiId, String name, String version, String revision, String organization) throws ArtifactSynchronizerException {
        boolean pullSuccessful;
        try {
            pullSuccessful = GitUtils.pullChanges(gitObject);
        } catch (GitException e) {
            log.error(e);
            throw new ArtifactSynchronizerException(e);
        }

        if(pullSuccessful){
            boolean artifactExists;
            String artifactName = GitUtils.getArtifactName(apiId, name, version, revision, organization);
            String inactiveArtifactDirectory = GitUtils.getInactiveArtifactDirectory(name, version);
            String artifactPath = inactiveArtifactDirectory + GitConstants.FILE_SEPARATOR + artifactName;

            try {
                artifactExists = GitUtils.removeFile(gitObject, artifactPath);
            } catch (GitException e) {
                log.error("Error while adding changes to the staging area.", e);
                throw new ArtifactSynchronizerException("Error while adding changes to the staging area.", e);
            }

            if(artifactExists) {
                String revisionId = revision.split("-")[0];
                String commitMessage = "Revision Deleted - " + name + ":" + version + ":" + organization + " - " + revisionId;

                try {
                    GitUtils.pushToRepository(gitObject, commitMessage);
                } catch (GitException e) {
                    log.error("Error while pushing changes to the remote repository.", e);
                    throw new ArtifactSynchronizerException("Error while pushing changes to the remote repository.", e);
                }
            } else {
                log.error("Artifact " + artifactPath + " doesn't exist.");
            }
        }
    }

    public void removeArtifact(String apiId, String name, String version, String organization)
            throws ArtifactSynchronizerException {}

    public void disconnect() {}

    @Override
    public String getName() {
        return GitConstants.GIT_ARTIFACT_SAVER;
    }

}
