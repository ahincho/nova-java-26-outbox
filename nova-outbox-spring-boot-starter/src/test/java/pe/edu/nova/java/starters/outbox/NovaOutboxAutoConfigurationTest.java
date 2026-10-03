package pe.edu.nova.java.starters.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pe.edu.nova.java.libs.outbox.Inbox;
import pe.edu.nova.java.libs.outbox.Outbox;
import pe.edu.nova.java.libs.outbox.OutboxEvent;
import pe.edu.nova.java.libs.outbox.OutboxException;
import pe.edu.nova.java.libs.outbox.OutboxStore;
import pe.edu.nova.java.libs.outbox.TraceContext;
import pe.edu.nova.java.libs.outbox.Transactions;
import pe.edu.nova.java.libs.outbox.jdbc.JdbcInbox;
import pe.edu.nova.java.libs.outbox.jdbc.JdbcOutboxStore;
import pe.edu.nova.java.libs.outbox.memory.InMemoryInbox;
import pe.edu.nova.java.libs.outbox.memory.InMemoryOutboxStore;

class NovaOutboxAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NovaOutboxAutoConfiguration.class))
            .withPropertyValues("spring.application.name=plaza-orders");

    @Test
    void withADataSourceTheStoresArePostgresInTheSpringTransaction() {
        runner.withBean(DataSource.class, () -> Mockito.mock(DataSource.class)).run(context -> {
            assertThat(context).hasSingleBean(Outbox.class);
            assertThat(context.getBean(OutboxStore.class)).isInstanceOf(JdbcOutboxStore.class);
            assertThat(context.getBean(Inbox.class)).isInstanceOf(JdbcInbox.class);
        });
    }

    @Test
    void withoutADataSourceTheServiceDoesNotStart() {
        runner.run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("nova.outbox.store=memory"));
    }

    @Test
    void theMemoryStoreIsAChoiceOnPurposeAndTheSourceIsTheApplicationName() {
        runner.withPropertyValues("nova.outbox.store=memory", "nova.outbox.remove-after-insert=false")
                .withBean(Transactions.class, () -> () -> true)
                .run(context -> {
                    InMemoryOutboxStore store = context.getBean(InMemoryOutboxStore.class);
                    assertThat(context.getBean(Inbox.class)).isInstanceOf(InMemoryInbox.class);

                    OutboxEvent event = context.getBean(Outbox.class).append("orders", "order-1", "type.v1", "{}");

                    assertThat(event.source()).isEqualTo("/plaza-orders");
                    assertThat(store.rows()).containsExactly(event);
                });
    }

    @Test
    void theSourceCanBeSetAndWithoutOneNorAnApplicationNameTheServiceDoesNotStart() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(NovaOutboxAutoConfiguration.class))
                .withPropertyValues("nova.outbox.store=memory", "nova.outbox.source=/plaza/orders")
                .withBean(Transactions.class, () -> () -> true)
                .run(context -> assertThat(context.getBean(Outbox.class)
                                .append("orders", "order-1", "type.v1", "{}")
                                .source())
                        .isEqualTo("/plaza/orders"));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(NovaOutboxAutoConfiguration.class))
                .withPropertyValues("nova.outbox.store=memory")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("nova.outbox.source"));
    }

    @Test
    void outsideASpringTransactionAppendingFails() {
        runner.withPropertyValues("nova.outbox.store=memory").run(context -> {
            assertThat(context.getBean(Transactions.class).active()).isFalse();
            assertThatThrownBy(() -> context.getBean(Outbox.class).append("orders", "order-1", "type.v1", "{}"))
                    .isInstanceOf(OutboxException.class);
        });
    }

    @Test
    void eachPieceIsABeanTheServiceCanReplace() {
        OutboxStore store = new InMemoryOutboxStore();
        Inbox inbox = new InMemoryInbox();
        runner.withBean(OutboxStore.class, () -> store)
                .withBean(Inbox.class, () -> inbox)
                .run(context -> {
                    assertThat(context.getBean(OutboxStore.class)).isSameAs(store);
                    assertThat(context.getBean(Inbox.class)).isSameAs(inbox);
                });
    }

    @Test
    void theTraceIsTheOneMicrometerHasWhenTheEventIsAppended() {
        SimpleTracer tracer = new SimpleTracer();
        runner.withPropertyValues("nova.outbox.store=memory")
                .withBean(Tracer.class, () -> tracer)
                .withBean(Propagator.class, W3c::new)
                .run(context -> {
                    TraceContext traceContext = context.getBean(TraceContext.class);
                    assertThat(traceContext.current()).isEmpty();

                    Span span = tracer.nextSpan().start();
                    try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
                        assertThat(scope).isNotNull();
                        Optional<TraceContext.Trace> trace = traceContext.current();

                        assertThat(trace).isPresent();
                        assertThat(trace.get().traceparent())
                                .isEqualTo("00-" + span.context().traceId() + "-"
                                        + span.context().spanId() + "-01");
                        assertThat(trace.get().tracestate()).isEqualTo("nova=1");
                    } finally {
                        span.end();
                    }
                });
    }

    @Test
    void withoutATracerThereIsNoTrace() {
        runner.withPropertyValues("nova.outbox.store=memory")
                .run(context -> assertThat(context.getBean(TraceContext.class).current())
                        .isEmpty());
    }

    @Test
    void theSpringProviderGivesBackTheConnectionWithoutClosingTheTransaction() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        Mockito.when(dataSource.getConnection()).thenReturn(connection);
        SpringConnectionProvider provider = new SpringConnectionProvider(dataSource);

        Connection acquired = provider.acquire();
        provider.release(acquired);

        assertThat(acquired).isSameAs(connection);
        // Fuera de una transacción, Spring la cierra al devolverla.
        Mockito.verify(connection).close();
    }

    /** Un propagador W3C mínimo, como el de OpenTelemetry. */
    private static final class W3c implements Propagator {

        @Override
        public List<String> fields() {
            return List.of("traceparent", "tracestate");
        }

        @Override
        public <C> void inject(io.micrometer.tracing.TraceContext context, C carrier, Setter<C> setter) {
            setter.set(carrier, "traceparent", "00-" + context.traceId() + "-" + context.spanId() + "-01");
            setter.set(carrier, "tracestate", "nova=1");
        }

        @Override
        public <C> Span.Builder extract(C carrier, Getter<C> getter) {
            throw new UnsupportedOperationException();
        }
    }
}
