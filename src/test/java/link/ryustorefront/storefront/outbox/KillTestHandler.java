package link.ryustorefront.storefront.outbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Used only by {@link WorkerKillTests}, inside child JVMs started with
 * {@code storefront.test.kill-handler=true}. It writes its result row (inside the worker's
 * transaction), and in {@code block} mode it then announces it has started and sleeps, so the test
 * can kill the whole process in the middle of the job.
 */
@Component
@ConditionalOnProperty(name = "storefront.test.kill-handler", havingValue = "true")
class KillTestHandler implements OutboxHandler {

    private final JdbcClient jdbc;
    private final Path dir;
    private final String mode;

    KillTestHandler(JdbcClient jdbc, @Value("${storefront.test.dir}") String dir,
                    @Value("${storefront.test.mode:complete}") String mode) {
        this.jdbc = jdbc;
        this.dir = Path.of(dir);
        this.mode = mode;
    }

    @Override
    public String eventType() {
        return "test.kill";
    }

    @Override
    public void handle(OutboxEvent event) {
        jdbc.sql("INSERT INTO worker_kill_results (event_id) VALUES (:id)").param("id", event.id()).update();
        if ("block".equals(mode)) {
            try {
                Files.writeString(dir.resolve("started"), "started");
                Thread.sleep(300_000); // killed long before this ends
            } catch (IOException e) {
                throw new IllegalStateException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
