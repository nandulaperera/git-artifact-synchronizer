package org.wso2.carbon.apimgt.git.model;

public class PushStatus {

    private String commitHash;
    private boolean pushSuccessful;

    public PushStatus(String commitHash, boolean pushSuccessful) {
        this.commitHash = commitHash;
        this.pushSuccessful = pushSuccessful;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public boolean isPushSuccessful() {
        return pushSuccessful;
    }
}
