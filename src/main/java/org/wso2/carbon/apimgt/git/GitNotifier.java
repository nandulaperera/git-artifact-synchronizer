package org.wso2.carbon.apimgt.git;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.model.APIRevisionDeployment;
import org.wso2.carbon.apimgt.git.constants.GitConstants;
import org.wso2.carbon.apimgt.git.exception.GitException;
import org.wso2.carbon.apimgt.git.model.GitObject;
import org.wso2.carbon.apimgt.git.util.GitUtils;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.notifier.Notifier;
import org.wso2.carbon.apimgt.impl.notifier.events.DeployAPIInGatewayEvent;
import org.wso2.carbon.apimgt.impl.notifier.events.Event;
import org.wso2.carbon.apimgt.impl.notifier.exceptions.NotifierException;
import org.wso2.carbon.apimgt.rest.api.common.RestApiCommonUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class GitNotifier implements Notifier {
    private static final Log log = LogFactory.getLog(GitNotifier.class);
    @Override
    public boolean publishEvent(Event event) throws NotifierException {
        DeployAPIInGatewayEvent deployAPIInGatewayEvent = (DeployAPIInGatewayEvent) event;

        String eventType = deployAPIInGatewayEvent.getType();
        String apiUuid = deployAPIInGatewayEvent.getUuid();
        String apiId = apiUuid.split("-")[0];
        String apiName = deployAPIInGatewayEvent.getName();
        String apiVersion = deployAPIInGatewayEvent.getVersion();
        String tenant = deployAPIInGatewayEvent.getTenantDomain();
        Set<String> gatewayLabels = deployAPIInGatewayEvent.getGatewayLabels();

        GitObject gitObject = GitObject.getInstance();
        boolean pullSuccessful;

        try {
            pullSuccessful = GitUtils.pullChanges(gitObject);
        } catch (GitException e) {
            log.error(e);
            throw new NotifierException(e);
        }

        APIProvider apiProvider;

        if(pullSuccessful) {
            if(gatewayLabels.isEmpty()){
                return true;
            }

            try {
                apiProvider = RestApiCommonUtil.getLoggedInUserProvider();

                List<APIRevisionDeployment> apiRevisionDeploymentList = apiProvider.getAPIRevisionsDeploymentList(apiUuid);
                String revisionUuid = "";

                for(APIRevisionDeployment apiRevisionDeployment : apiRevisionDeploymentList) {
                    String gateway = apiRevisionDeployment.getDeployment();
                    if (!gatewayLabels.contains(gateway)) {
                        continue;
                    }

                    String apiRevisionUuid = apiRevisionDeployment.getRevisionUUID();
                    if(!apiRevisionUuid.isEmpty()) {
                        revisionUuid = apiRevisionUuid;
                        break;
                    }
                }

                String localRepositoryPath = gitObject.getLocalRepositoryPath().getAbsolutePath();

                String inactiveArtifactDirectory = GitUtils.getInactiveArtifactDirectory(apiName, apiVersion);

                String artifactName = GitUtils.getArtifactName(apiUuid, apiName, apiVersion, revisionUuid, tenant);

                String revisionId = revisionUuid.split("-")[0];

                String commitMessage = "";

                if(eventType.equals(APIConstants.EventType.DEPLOY_API_IN_GATEWAY.name())){
                    for(String gateway : gatewayLabels) {
                        String activeGatewayArtifacts = GitConstants.ACTIVE_DIR + "-" + gateway;
                        File activeDeploymentDirectory = new File(localRepositoryPath
                                + GitConstants.FILE_SEPARATOR + activeGatewayArtifacts);

                        // When a new revision is to be deployed, if the gatewayLabels in REMOVE_API_FROM_GATEWAY is empty
                        // and if a previously deployed revision exists, replace it with the new deployed revision
                        if (activeDeploymentDirectory.exists()) {
                            String[] activeDeployments = activeDeploymentDirectory.list();
                            for (String activeDeployment : activeDeployments) {
                                String apiPrefix = apiId + "-" + apiName + "-" + apiVersion;
                                if (activeDeployment.startsWith(apiPrefix) && activeDeployment.endsWith(GitConstants.ZIP)) {
                                    GitUtils.removeFile(gitObject, activeGatewayArtifacts
                                            + GitConstants.FILE_SEPARATOR + activeDeployment);
                                }
                            }
                        }

                        String inactiveArtifactPath = inactiveArtifactDirectory + GitConstants.FILE_SEPARATOR
                                + artifactName;
                        String deployedArtifactPath = activeGatewayArtifacts + GitConstants.FILE_SEPARATOR + artifactName;
                        File inactiveArtifact = new File(localRepositoryPath +
                                GitConstants.FILE_SEPARATOR + inactiveArtifactPath);
                        FileUtils.copyFile(inactiveArtifact,
                                new File(localRepositoryPath + GitConstants.FILE_SEPARATOR + deployedArtifactPath));
                        GitUtils.addFile(gitObject, deployedArtifactPath);
                    }

                    commitMessage = "Revision Deployed - " + apiName + ":" + apiVersion + ":" + tenant + " - " + revisionId;
                    GitUtils.pushToRepository(gitObject, commitMessage);
                } else if(eventType.equals(APIConstants.EventType.REMOVE_API_FROM_GATEWAY.name())){
                    for(String gateway : gatewayLabels){
                        String activeGatewayArtifacts = GitConstants.ACTIVE_DIR + "-" + gateway;
                        String undeployedArtifactPath = activeGatewayArtifacts + GitConstants.FILE_SEPARATOR + artifactName;
                        GitUtils.removeFile(gitObject, undeployedArtifactPath);
                    }

                    commitMessage = "Revision Undeployed - " + apiName + ":" + apiVersion + ":" + tenant + " - " + revisionId;
                    GitUtils.pushToRepository(gitObject, commitMessage);
                }
            } catch (APIManagementException | IOException e) {
                log.error(e);
                throw new NotifierException(e);
            } catch (GitException e) {
                log.error("Error while pushing changes to the remote repository.", e);
                throw new NotifierException("Error while pushing changes to the remote repository.", e);
            }
        }
        return true;
    }

    @Override
    public String getType() {
        return APIConstants.NotifierType.GATEWAY_PUBLISHED_API.name();
    }
}
