package pe.edu.nova.java.starters.outbox;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Clock;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.edu.nova.java.libs.outbox.DefaultOutbox;
import pe.edu.nova.java.libs.outbox.Inbox;
import pe.edu.nova.java.libs.outbox.Outbox;
import pe.edu.nova.java.libs.outbox.OutboxStore;
import pe.edu.nova.java.libs.outbox.TraceContext;
import pe.edu.nova.java.libs.outbox.Transactions;
import pe.edu.nova.java.libs.outbox.jdbc.JdbcInbox;
import pe.edu.nova.java.libs.outbox.jdbc.JdbcOutboxStore;
import pe.edu.nova.java.libs.outbox.memory.InMemoryInbox;
import pe.edu.nova.java.libs.outbox.memory.InMemoryOutboxStore;

/**
 * Conecta la salida de eventos con Spring Boot (ADR-048): un {@link Outbox} que escribe en la transacción de
 * Spring, con la traza de Micrometer, y un {@link Inbox} en la misma base. Cada pieza es un bean que el servicio
 * puede reemplazar con el suyo.
 *
 * <p>Sin {@code DataSource} el servicio no arranca, salvo que elija {@code nova.outbox.store=memory} a propósito
 * (regla 7).
 */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration",
            "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration"
        })
@ConditionalOnClass(TransactionSynchronizationManager.class)
@EnableConfigurationProperties(NovaOutboxProperties.class)
public class NovaOutboxAutoConfiguration {

    /** Crea la autoconfiguración; la instancia Spring. */
    public NovaOutboxAutoConfiguration() {}

    @Bean
    @ConditionalOnMissingBean
    Transactions novaOutboxTransactions() {
        return TransactionSynchronizationManager::isActualTransactionActive;
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxStore novaOutboxStore(NovaOutboxProperties properties, ObjectProvider<DataSource> dataSource) {
        if (properties.store() == NovaOutboxProperties.Store.MEMORY) {
            return new InMemoryOutboxStore();
        }
        return new JdbcOutboxStore(
                new SpringConnectionProvider(requireDataSource(dataSource)),
                properties.table(),
                properties.statementTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    Inbox novaInbox(NovaOutboxProperties properties, ObjectProvider<DataSource> dataSource) {
        if (properties.store() == NovaOutboxProperties.Store.MEMORY) {
            return new InMemoryInbox();
        }
        return new JdbcInbox(
                new SpringConnectionProvider(requireDataSource(dataSource)),
                properties.inboxTable(),
                properties.statementTimeout(),
                Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    Outbox novaOutbox(
            OutboxStore store,
            Transactions transactions,
            ObjectProvider<TraceContext> traceContext,
            ObjectProvider<Clock> clock,
            NovaOutboxProperties properties,
            Environment environment) {
        return new DefaultOutbox(
                store,
                transactions,
                traceContext.getIfAvailable(TraceContext::none),
                clock.getIfAvailable(Clock::systemUTC),
                source(properties, environment),
                properties.removeAfterInsert());
    }

    private static String source(NovaOutboxProperties properties, Environment environment) {
        if (properties.source() != null && !properties.source().isBlank()) {
            return properties.source();
        }
        String application = environment.getProperty("spring.application.name");
        if (application == null || application.isBlank()) {
            throw new IllegalStateException("The outbox needs the source of its events: set nova.outbox.source, like "
                    + "/plaza/orders, or spring.application.name");
        }
        return "/" + application;
    }

    private static DataSource requireDataSource(ObjectProvider<DataSource> dataSource) {
        DataSource available = dataSource.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException("The outbox writes its events in the service database, and there is no "
                    + "DataSource. Add one, or set nova.outbox.store=memory on purpose for development and tests");
        }
        return available;
    }

    /**
     * La traza de Micrometer, si el servicio la tiene. El tracer y el propagador se buscan al agregar cada evento,
     * no al arrancar: así no importa qué autoconfiguración los registra ni en qué orden.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Tracer.class)
    static class Tracing {

        @Bean
        @ConditionalOnMissingBean
        TraceContext novaOutboxTraceContext(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
            return () -> {
                Tracer available = tracer.getIfUnique();
                Propagator format = propagator.getIfUnique();
                if (available == null || format == null) {
                    return Optional.empty();
                }
                return new MicrometerTraceContext(available, format).current();
            };
        }
    }
}
