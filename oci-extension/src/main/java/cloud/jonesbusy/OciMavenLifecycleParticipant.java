package cloud.jonesbusy;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.slf4j.Logger;

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
        throw new MavenExecutionException("This is a test exception", new IllegalArgumentException("This is a test cause"));
    }



}

