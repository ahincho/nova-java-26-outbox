package pe.edu.nova.java.libs.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un evento listo para salir: una fila de la tabla {@code outbox} y, en Kafka, un CloudEvent en modo binario
 * (ADR-048).
 *
 * <p>{@link #toString()} no muestra el payload ni la traza: un evento puede terminar en un log, y su contenido
 * no (regla 5).
 *
 * @param id            el id del evento, estable entre reintentos; es {@code ce_id}
 * @param aggregateType el tipo de agregado, que elige el tópico: {@code orders} sale por {@code plaza.orders}
 * @param aggregateId   el id del agregado: la clave del registro, que ordena sus eventos, y {@code ce_subject}
 * @param type          el tipo versionado, como {@code pe.edu.nova.plaza.order.confirmed.v1}; es {@code ce_type}
 * @param source        la identidad del servicio, como {@code /plaza/orders}; es {@code ce_source}
 * @param time          el momento en que se agregó; es {@code ce_time}
 * @param payload       el JSON ya serializado por el servicio, opaco para Nova
 * @param traceparent   el contexto W3C en que se agregó, o {@code null} si no había traza
 * @param tracestate    el estado W3C de la traza, o {@code null}
 */
public record OutboxEvent(
        UUID id,
        String aggregateType,
        String aggregateId,
        String type,
        String source,
        Instant time,
        String payload,
        String traceparent,
        String tracestate) {

    /** Valida que estén los campos obligatorios. */
    public OutboxEvent {
        Objects.requireNonNull(id, "id");
        requireText(aggregateType, "aggregateType");
        requireText(aggregateId, "aggregateId");
        requireText(type, "type");
        requireText(source, "source");
        Objects.requireNonNull(time, "time");
        requireText(payload, "payload");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The outbox event needs a " + name);
        }
    }

    @Override
    public String toString() {
        return "OutboxEvent[id=" + id + ", aggregateType=" + aggregateType + ", type=" + type + "]";
    }
}
