package org.wso2.carbon.apimgt.git.model;

import org.wso2.carbon.apimgt.git.auth.GitAuth;

import java.io.File;

public class GitObject {

    private String repositoryURL;
    private File localRepositoryPath;
    private GitAuth gitAuth;
    private String branch;

    private static GitObject gitObject;

    private GitObject(String repositoryURL, File localRepositoryPath, GitAuth gitAuth, String branch) {
        this.repositoryURL = repositoryURL;
        this.localRepositoryPath = localRepositoryPath;
        this.gitAuth = gitAuth;
        this.branch = branch;
    }

    public static void init(String repositoryURL, File localRepositoryPath, GitAuth gitAuth, String branch){
        if(gitObject != null){
            throw new AssertionError("GitObject already initialized");
        }
        gitObject = new GitObject(repositoryURL, localRepositoryPath, gitAuth, branch);
    }

    public static GitObject getInstance(){
        if(gitObject == null){
            throw new AssertionError("GitObject not initialized");
        }
        return gitObject;
    }

    public String getRepositoryURL() {
        return repositoryURL;
    }

    public File getLocalRepositoryPath() {
        return localRepositoryPath;
    }

    public GitAuth getGitAuth() {
        return gitAuth;
    }

    public String getBranch() {
        return branch;
    }
}
