package org.wso2.carbon.apimgt.git.auth;

import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class GitAuth {

    private static String username;
    private static String accessToken;

    private UsernamePasswordCredentialsProvider credentialsProvider;

    private static GitAuth gitAuth;

    private GitAuth() {
        credentialsProvider = new UsernamePasswordCredentialsProvider(username, accessToken);
    }

    public static void init(String gitUsername, String gitAccessToken){
        if(gitAuth != null){
            throw new AssertionError("GitAuth already initialized");
        }
        username = gitUsername;
        accessToken = gitAccessToken;
    }

    public static GitAuth getInstance(){
        if(gitAuth == null){
            gitAuth = new GitAuth();
        }
        return gitAuth;
    }

    public UsernamePasswordCredentialsProvider getAuth(){
        if(credentialsProvider == null){
            gitAuth = null;
            gitAuth = new GitAuth();
        }
        return credentialsProvider;
    }
}
