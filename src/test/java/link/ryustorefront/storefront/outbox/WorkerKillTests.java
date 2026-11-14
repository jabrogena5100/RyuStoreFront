package link.ryustorefront.storefront.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * "Kill the worker mid-job; the job completes after restart", with a real worker and a real kill.
 *
 * <p>The worker is the application itself, started as a separate JVM with the {@code worker} profile
 * against this test's Postgres. Its test handler writes a result row inside the worker's transaction,
 * announces it has started, and sleeps. The test then kills the process with SIGKILL (no shutdown hooks,
 * no cleanup, as when a container is OOM-killed), checks that the job's database work vanished and the
 * event is still pending, starts a second worker, and checks the job completes exactly once.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class WorkerKillTests {

    private static final long START_TIMEOUT_SECONDS = 180;

    @Autowired
    JdbcClient jdbc;
    @Autowired
    PostgreSQLContainer postgres;

    @Test
    void aJobKilledMidwayIsLostToNobodyAndCompletesExactlyOnceAfterRestart() throws Exception {
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("CREATE TABLE IF NOT EXISTS worker_kill_results (event_id bigint PRIMARY KEY)").update();
        jdbc.sql("DELETE FROM worker_kill_results").update();
        long eventId = jdbc.sql("INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload) "
                        + "VALUES ('test', 'kill-test', 'test.kill', '{}'::jsonb) RETURNING id")
                .query(Long.class).single();
        Path dir = Files.createTempDirectory("worker-kill");
        Process first = null;
        Process second = null;
        try {
            // 1. A worker picks the job up and is in the middle of it.
            first = startWorker(dir, "block", dir.resolve("first.log"));
            Process running = first;
            assertThat(waitFor(() -> Files.exists(dir.resolve("started")) || !running.isAlive()))
                    .as("the first worker started the job within %d s; log tail:%n%s", START_TIMEOUT_SECONDS,
                            tail(dir.resolve("first.log"))).isTrue();
            assertThat(first.isAlive()).as("the first worker is alive mid-job; log tail:%n%s", tail(dir.resolve("first.log")))
                    .isTrue();

            // 2. It is killed with no warning.
            first.destroyForcibly();
            assertThat(first.waitFor(30, TimeUnit.SECONDS)).isTrue();

            // 3. Nothing the job did survived, and the event still waits to be done.
            assertThat(waitFor(() -> resultRows() == 0)).as("the dead worker's write was rolled back").isTrue();
            assertThat(isProcessed(eventId)).as("the event is still pending").isFalse();
            assertThat(attempts(eventId)).as("a kill is not a counted failure").isZero();

            // 4. A new worker takes it and finishes it.
            second = startWorker(dir, "complete", dir.resolve("second.log"));
            Process restarted = second;
            assertThat(waitFor(() -> isProcessed(eventId) || !restarted.isAlive()))
                    .as("the second worker finished the job within %d s; log tail:%n%s", START_TIMEOUT_SECONDS,
                            tail(dir.resolve("second.log"))).isTrue();
            assertThat(isProcessed(eventId)).as("log tail:%n%s", tail(dir.resolve("second.log"))).isTrue();

            // 5. The job's effect exists exactly once.
            assertThat(resultRows()).isEqualTo(1);
            assertThat(attempts(eventId)).isZero();
        } finally {
            kill(first);
            kill(second);
            jdbc.sql("DROP TABLE IF EXISTS worker_kill_results").update();
            jdbc.sql("DELETE FROM outbox WHERE aggregate_id = 'kill-test'").update();
        }
    }

    private Process startWorker(Path dir, String mode, Path log) throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = List.of(java, "-cp", System.getProperty("java.class.path"),
                "link.ryustorefront.storefront.StorefrontApplication",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--server.port=0",
                "--spring.profiles.active=worker",
                "--storefront.worker.poll-interval-ms=200",
                "--storefront.test.kill-handler=true",
                "--storefront.test.dir=" + dir,
                "--storefront.test.mode=" + mode);
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }

    private long resultRows() {
        return jdbc.sql("SELECT count(*) FROM worker_kill_results").query(Long.class).single();
    }

    private boolean isProcessed(long id) {
        return jdbc.sql("SELECT processed_at IS NOT NULL FROM outbox WHERE id = :id").param("id", id)
                .query(Boolean.class).single();
    }

    private int attempts(long id) {
        return jdbc.sql("SELECT attempts FROM outbox WHERE id = :id").param("id", id).query(Integer.class).single();
    }

    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(START_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(250);
        }
        return condition.getAsBoolean();
    }

    private static void kill(Process process) {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private static String tail(Path log) {
        try {
            List<String> lines = Files.readAllLines(log);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 25), lines.size()));
        } catch (IOException e) {
            return "(no log: " + e.getMessage() + ")";
        }
    }
}
