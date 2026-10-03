package pe.edu.nova.java.starters.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pe.edu.nova.java.libs.outbox.DefaultOutbox;
import pe.edu.nova.java.libs.outbox.Inbox;
import pe.edu.nova.java.libs.outbox.Outbox;
import pe.edu.nova.java.libs.outbox.TraceContext;
import pe.edu.nova.java.libs.outbox.jdbc.JdbcInbox;
import pe.edu.nova.java.libs.outbox.jdbc.JdbcOutboxStore;

/**
 * El evento y el cambio del negocio en la misma transacción de Spring, contra un PostgreSQL de verdad: se
 * confirman juntos o no se confirma ninguno.
 */
class SpringTransactionTest {

    private static PostgreSQLContainer postgres;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transaction;
    private static Outbox outbox;
    private static Inbox inbox;

    @BeforeAll
    static void startPostgres() throws IOException {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is needed to run PostgreSQL");
        postgres = new PostgreSQLContainer("postgres:17.11-alpine");
        postgres.start();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute(script("V1__create_outbox.sql"));
        jdbc.execute(script("V1__create_inbox_message.sql"));
        jdbc.execute("create table orders (id varchar(36) primary key, status varchar(20) not null)");
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        SpringConnectionProvider connections = new SpringConnectionProvider(dataSource);
        outbox = new DefaultOutbox(
                new JdbcOutboxStore(connections),
                new NovaOutboxAutoConfiguration().novaOutboxTransactions(),
                TraceContext.none(),
                Clock.systemUTC(),
                "/plaza/orders",
                false);
        inbox = new JdbcInbox(connections);
    }

    @AfterAll
    static void stopPostgres() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void emptyTheTables() {
        jdbc.execute("truncate table outbox; truncate table inbox_message; truncate table orders");
    }

    @Test
    void theEventIsCommittedWithTheBusinessChange() {
        transaction.executeWithoutResult(status -> {
            jdbc.update("insert into orders values ('order-1', 'CONFIRMED')");
            outbox.append("orders", "order-1", "pe.edu.nova.plaza.order.confirmed.v1", "{}");
        });

        assertThat(count("orders")).isEqualTo(1);
        assertThat(count("outbox")).isEqualTo(1);
    }

    @Test
    void aRolledBackChangeTakesItsEventWithIt() {
        transaction.executeWithoutResult(status -> {
            jdbc.update("insert into orders values ('order-1', 'CONFIRMED')");
            outbox.append("orders", "order-1", "pe.edu.nova.plaza.order.confirmed.v1", "{}");
            status.setRollbackOnly();
        });

        assertThat(count("orders")).isZero();
        assertThat(count("outbox")).isZero();
    }

    @Test
    void aRolledBackEffectForgetsTheEventSoItIsProcessedAgain() {
        transaction.executeWithoutResult(status -> {
            assertThat(inbox.register("ranking", "/plaza/orders", "event-1")).isTrue();
            status.setRollbackOnly();
        });

        Boolean again = transaction.execute(status -> inbox.register("ranking", "/plaza/orders", "event-1"));

        assertThat(again).isTrue();
        Boolean third = transaction.execute(status -> inbox.register("ranking", "/plaza/orders", "event-1"));
        assertThat(third).isFalse();
    }

    private static int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private static String script(String name) throws IOException {
        try (InputStream script =
                SpringTransactionTest.class.getResourceAsStream("/db/nova/outbox/postgresql/" + name)) {
            return new String(script.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
