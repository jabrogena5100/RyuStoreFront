package link.ryustorefront.storefront.notifications;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** A real Mailpit (the same image as compose.yaml): SMTP on 1025, REST API and inbox on 8025. */
class MailpitContainer extends GenericContainer<MailpitContainer> {

    static final int SMTP_PORT = 1025;
    static final int HTTP_PORT = 8025;

    MailpitContainer() {
        super(DockerImageName.parse("axllent/mailpit:v1.33.1"));
        withExposedPorts(SMTP_PORT, HTTP_PORT);
        waitingFor(Wait.forListeningPorts(SMTP_PORT, HTTP_PORT));
    }

    String apiBase() {
        return "http://" + getHost() + ":" + getMappedPort(HTTP_PORT);
    }
}
