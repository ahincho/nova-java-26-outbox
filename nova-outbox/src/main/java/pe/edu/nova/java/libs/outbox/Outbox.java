package pe.edu.nova.java.libs.outbox;

/**
 * Recibe un evento dentro de la transacción del negocio (ADR-048). El servicio nunca sabe cómo ni cuándo sale:
 * en Nova lo publica Debezium leyendo el log de la base.
 *
 * <p>Las reglas que ninguna implementación cambia: sin transacción activa, {@link #append} falla; el evento se
 * confirma o se deshace con el cambio del negocio; y su payload es JSON que el servicio ya serializó.
 */
public interface Outbox {

    /**
     * Agrega un evento en la transacción vigente.
     *
     * @param aggregateType el tipo de agregado, que elige el tópico, como {@code orders}
     * @param aggregateId   el id del agregado, que ordena sus eventos
     * @param type          el tipo versionado del evento, como {@code pe.edu.nova.plaza.order.confirmed.v1}
     * @param payload       el JSON del evento, ya serializado
     * @return el evento, con su id
     * @throws OutboxException si no hay transacción activa o el almacén falló
     */
    OutboxEvent append(String aggregateType, String aggregateId, String type, String payload);
}
