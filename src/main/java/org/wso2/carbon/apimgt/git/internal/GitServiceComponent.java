package org.wso2.carbon.apimgt.git.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.wso2.carbon.apimgt.git.GitNotifier;
import org.wso2.carbon.apimgt.git.GitSaver;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.ArtifactSaver;
import org.wso2.carbon.apimgt.impl.notifier.Notifier;

@Component(
        name = "org.wso2.carbon.apimgt.git.internal.GitServiceComponent",
        immediate = true
)
public class GitServiceComponent {

    private static final Log logger = LogFactory.getLog(GitServiceComponent.class);

    @Activate
    protected void activate(BundleContext bundleContext){
        try {
            GitSaver gitSaver = new GitSaver();
            GitNotifier gitNotifier = new GitNotifier();
            bundleContext.registerService(ArtifactSaver.class, gitSaver, null);
            bundleContext.registerService(Notifier.class, gitNotifier, null);
            logger.info("Activating GitServiceComponent");
        } catch (Throwable throwable){
            logger.error(throwable.getMessage());
            throwable.printStackTrace();
        }
    }

    @Deactivate
    protected void deactivate(BundleContext bundleContext){
        logger.info("Deactivating GitServiceComponent");
    }

}
