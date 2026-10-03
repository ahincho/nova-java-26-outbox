package pe.edu.nova.java.starters.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La configuración de la salida de eventos, bajo {@code nova.outbox} (ADR-048).
 *
 * @param store             dónde se escriben los eventos: {@code jdbc}, el valor por defecto, o {@code memory}, que
 *                          solo sirve para desarrollo y pruebas y hay que elegir a propósito
 * @param source            la identidad del servicio en {@code ce_source}; por defecto, {@code /} y el nombre de la
 *                          aplicación
 * @param removeAfterInsert borrar cada evento en la misma transacción en que se insertó, como pide Debezium; por
 *                          defecto, {@code true}
 * @param table             la tabla del outbox; por defecto, {@code outbox}
 * @param inboxTable        la tabla del inbox; por defecto, {@code inbox_message}
 * @param statementTimeout  cuánto puede tardar cada sentencia; por defecto, cinco segundos
 */
@ConfigurationProperties("nova.outbox")
public record NovaOutboxProperties(
        Store store,
        String source,
        Boolean removeAfterInsert,
        String table,
        String inboxTable,
        Duration statementTimeout) {

    /** Completa los valores por defecto. */
    public NovaOutboxProperties {
        store = store == null ? Store.JDBC : store;
        removeAfterInsert = removeAfterInsert == null ? Boolean.TRUE : removeAfterInsert;
        table = table == null ? "outbox" : table;
        inboxTable = inboxTable == null ? "inbox_message" : inboxTable;
        statementTimeout = statementTimeout == null ? Duration.ofSeconds(5) : statementTimeout;
    }

    /** Dónde se escriben los eventos. */
    public enum Store {
        /** En PostgreSQL, en la transacción del servicio. */
        JDBC,
        /** En memoria: un rollback no quita el evento, así que nunca en producción. */
        MEMORY
    }
}
