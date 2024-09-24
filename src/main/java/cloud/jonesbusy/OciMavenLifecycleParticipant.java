package cloud.jonesbusy;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named("oci")
@Singleton
public class OciMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    @Inject
    private Logger log;

    @Override
    public void afterSessionStart(MavenSession session)throws MavenExecutionException {
        log.info("afterSessionStart");
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        log.info("afterProjectsRead");
    }

    @Override
    public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
        log.info("afterSessionEnd");
    }
}

