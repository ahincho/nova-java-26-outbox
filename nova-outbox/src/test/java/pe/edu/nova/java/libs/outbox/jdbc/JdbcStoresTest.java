package pe.edu.nova.java.libs.outbox.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.edu.nova.java.libs.outbox.DefaultOutbox;
import pe.edu.nova.java.libs.outbox.OutboxEvent;
import pe.edu.nova.java.libs.outbox.OutboxException;
import pe.edu.nova.java.libs.outbox.TraceContext;

/**
 * Los almacenes contra un PostgreSQL 17 de verdad, con las tablas de los scripts del módulo. Cada prueba abre su
 * transacción como lo haría el servicio: una conexión sin auto-commit, que el proveedor entrega y no cierra.
 */
class JdbcStoresTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T15:00:00Z"), ZoneOffset.UTC);

    private static PostgreSQLContainer postgres;
    private static PGSimpleDataSource dataSource;

    @BeforeAll
    static void startPostgres() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is needed to run PostgreSQL");
        postgres = new PostgreSQLContainer("postgres:17.11-alpine")
                .withDatabaseName("outbox")
                .withUsername("outbox")
                .withPassword("outbox-test");
        postgres.start();
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        execute(script("V1__create_outbox.sql"));
        execute(script("V1__create_inbox_message.sql"));
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void emptyTheTables() {
        execute("truncate table outbox; truncate table inbox_message");
    }

    @Test
    void aCommittedEventIsARowWithEveryColumnOfTheWire() throws SQLException {
        OutboxEvent event;
        try (Connection transaction = transaction()) {
            event = outbox(transaction, false).append("orders", "order-1", "type.v1", "{\"orderId\":\"order-1\"}");
            transaction.commit();
        }

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("select * from outbox")) {
            assertTrue(row.next());
            assertEquals(event.id(), row.getObject("id"));
            assertEquals("orders", row.getString("aggregate_type"));
            assertEquals("order-1", row.getString("aggregate_id"));
            assertEquals("type.v1", row.getString("type"));
            assertEquals("/plaza/orders", row.getString("source"));
            assertEquals(
                    Instant.parse("2026-10-02T15:00:00Z"),
                    row.getTimestamp("time").toInstant());
            assertEquals("{\"orderId\": \"order-1\"}", row.getString("payload"));
            assertEquals(TRACEPARENT, row.getString("traceparent"));
            assertNull(row.getString("tracestate"));
            assertFalse(row.next());
        }
    }

    @Test
    void aRolledBackTransactionLeavesNoEvent() throws SQLException {
        try (Connection transaction = transaction()) {
            outbox(transaction, false).append("orders", "order-1", "type.v1", "{}");
            transaction.rollback();
        }

        assertEquals(0, count("outbox"));
    }

    @Test
    void removingAfterInsertLeavesTheTableEmpty() throws SQLException {
        try (Connection transaction = transaction()) {
            outbox(transaction, true).append("orders", "order-1", "type.v1", "{}");
            transaction.commit();
        }

        assertEquals(0, count("outbox"));
    }

    @Test
    void aPayloadThatIsNotJsonFailsWithoutShowingIt() throws SQLException {
        try (Connection transaction = transaction()) {
            OutboxException error = assertThrows(
                    OutboxException.class,
                    () -> outbox(transaction, false).append("orders", "order-1", "type.v1", "card 4111"));
            transaction.rollback();

            assertTrue(error.getMessage().contains("22P02"));
            assertFalse(error.getMessage().contains("4111"));
            assertNull(error.getCause());
        }
    }

    @Test
    void theInboxProcessesEachEventOncePerConsumer() throws SQLException {
        try (Connection transaction = transaction()) {
            JdbcInbox inbox = inbox(transaction);

            assertTrue(inbox.register("ranking", "/plaza/orders", "event-1"));
            assertFalse(inbox.register("ranking", "/plaza/orders", "event-1"));
            assertTrue(inbox.register("audit", "/plaza/orders", "event-1"));
            transaction.commit();
        }

        assertEquals(2, count("inbox_message"));
    }

    @Test
    void aConcurrentDuplicateWaitsForTheFirstAndIsDiscardedWhenItCommits() throws Exception {
        assertFalse(registerWhileAnotherTransactionHoldsIt(true));
    }

    @Test
    void aConcurrentDuplicateProcessesTheEventWhenTheFirstRollsBack() throws Exception {
        assertTrue(registerWhileAnotherTransactionHoldsIt(false));
    }

    private boolean registerWhileAnotherTransactionHoldsIt(boolean commitTheFirst) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection first = transaction();
                Connection second = transaction()) {
            assertTrue(inbox(first).register("ranking", "/plaza/orders", "event-1"));
            CompletableFuture<Boolean> duplicate = CompletableFuture.supplyAsync(
                    () -> inbox(second).register("ranking", "/plaza/orders", "event-1"), executor);

            // El duplicado espera por la clave primaria mientras la primera transacción siga abierta.
            assertThrows(TimeoutException.class, () -> duplicate.get(500, TimeUnit.MILLISECONDS));
            if (commitTheFirst) {
                first.commit();
            } else {
                first.rollback();
            }
            boolean processed = duplicate.get(10, TimeUnit.SECONDS);
            second.commit();
            return processed;
        } finally {
            executor.shutdownNow();
        }
    }

    private static DefaultOutbox outbox(Connection transaction, boolean removeAfterInsert) {
        return new DefaultOutbox(
                new JdbcOutboxStore(inTransaction(transaction)),
                () -> true,
                () -> Optional.of(new TraceContext.Trace(TRACEPARENT, null)),
                CLOCK,
                "/plaza/orders",
                removeAfterInsert);
    }

    private static JdbcInbox inbox(Connection transaction) {
        return new JdbcInbox(inTransaction(transaction), "inbox_message", Duration.ofSeconds(30), CLOCK);
    }

    // La conexión es de la transacción: el proveedor la entrega y no la cierra.
    private static ConnectionProvider inTransaction(Connection transaction) {
        return new ConnectionProvider() {
            @Override
            public Connection acquire() {
                return transaction;
            }

            @Override
            public void release(Connection connection) {
                // La cierra quien abrió la transacción.
            }
        };
    }

    private static Connection transaction() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        return connection;
    }

    private static int count(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("select count(*) from " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void execute(String sql) {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not run the SQL: " + e.getMessage(), e);
        }
    }

    private static String script(String name) {
        try (InputStream script = JdbcStoresTest.class.getResourceAsStream("/db/nova/outbox/postgresql/" + name)) {
            return new String(script.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
