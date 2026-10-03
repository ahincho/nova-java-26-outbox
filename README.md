# nova-java-26-outbox

La salida de eventos de Nova Platform. Un servicio escribe el evento **en la misma transacción** que su
cambio de negocio, y el evento sale si y solo si esa transacción se confirmó. Quien lo publica en Kafka es
Debezium, leyendo el log de PostgreSQL: el servicio no conoce Kafka, ni su dirección, ni su cliente. Del otro
lado, el inbox deduplica a quien consume, porque un evento llega al menos una vez.

Las decisiones están en [ADR-048](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/shared/ADR-048-outbox-transaccional-detras-de-un-contrato.md),
y la forma del repositorio en [ADR-041](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-041-un-repositorio-por-capacidad.md).

## Módulos

| Módulo | `groupId` | Qué es |
|---|---|---|
| `nova-outbox` | `pe.edu.nova.java.libs` | el contrato, el núcleo, los almacenes de PostgreSQL con JDBC puro y los de memoria; Java puro, sin framework |
| `nova-outbox-spring-boot-starter` | `pe.edu.nova.java.starters` | conecta la capacidad con la transacción de Spring y con la traza de Micrometer |

Los dos se publican en `https://maven.pkg.github.com/ahincho/nova-java-26-outbox` con la misma versión. La
primera es la 0.1.0, y la 1.0.0 llega cuando Plaza la valide de punta a punta.

## Escribir un evento, con Spring Boot

```kotlin
implementation("pe.edu.nova.java.starters:nova-outbox-spring-boot-starter:0.1.0")
```

```java
@Transactional
public void confirm(UUID id) {
    Order order = orders.findById(id).orElseThrow();
    if (order.confirm()) {
        outbox.append("orders", order.getId().toString(), "pe.edu.nova.plaza.order.confirmed.v1", json(order));
    }
}
```

`append` falla si no hay una transacción activa: escribir en autocommit es justo la doble escritura que el
outbox quita. El payload es JSON que el servicio ya serializó; Nova no conoce sus clases.

| Propiedad | Por defecto | Qué decide |
|---|---|---|
| `nova.outbox.store` | `jdbc` | `memory` solo para desarrollo y pruebas, y elegido a propósito: sin `DataSource`, el servicio no arranca |
| `nova.outbox.source` | `/` y `spring.application.name` | el `ce_source` de los eventos, como `/plaza/orders` |
| `nova.outbox.remove-after-insert` | `true` | borrar la fila en la misma transacción: el log conserva la inserción y la tabla no crece |
| `nova.outbox.table` | `outbox` | la tabla del outbox |
| `nova.outbox.inbox-table` | `inbox_message` | la tabla del inbox |
| `nova.outbox.statement-timeout` | `5s` | cuánto puede tardar cada sentencia |

Si el servicio tiene Micrometer Tracing, el evento guarda el `traceparent` y el `tracestate` del momento en
que se agregó, y el consumidor continúa la misma traza.

## Las tablas

Las crea el servicio con su migración, copiando los scripts que vienen dentro de `nova-outbox`:

- [`V1__create_outbox.sql`](nova-outbox/src/main/resources/db/nova/outbox/postgresql/V1__create_outbox.sql)
- [`V1__create_inbox_message.sql`](nova-outbox/src/main/resources/db/nova/outbox/postgresql/V1__create_inbox_message.sql)

Las columnas del outbox son contrato: el Event Router de Debezium las lee por nombre.

## Publicar con Debezium

La configuración del conector de Plaza está en
[`nova-plaza-01-shared-platform`](https://github.com/ahincho/nova-plaza-01-shared-platform/blob/main/debezium/orders-outbox.json).
Cada fila sale como un CloudEvent en modo binario:

| En Kafka | De la columna |
|---|---|
| el tópico | `plaza.` y `aggregate_type` |
| la clave | `aggregate_id` |
| el valor | `payload` |
| `ce_id`, `ce_type`, `ce_source`, `ce_subject`, `ce_time` | `id`, `type`, `source`, `aggregate_id`, `time` |
| `ce_traceparent`, `ce_tracestate` y `traceparent` | `traceparent` y `tracestate` |
| `ce_specversion` | `specversion`, que la tabla llena con `1.0` |
| `content-type` | una constante, con `InsertHeader` |

Un registro que no se puede publicar deja la tarea del conector en `FAILED` y nada se adelanta: se vigila
el estado del conector y el retraso del slot en `pg_replication_slots`.

## Consumir sin duplicados

```java
@Transactional
void on(String source, String id, OrderConfirmed event) {
    if (inbox.register("catalog-best-sellers", source, id)) {
        bestSellers.add(event.items());
    }
}
```

El registro y el efecto se confirman juntos. Un duplicado concurrente espera a que el primero termine: si
confirma, recibe `false`; si se revierte, procesa él. Fuera de Spring, `JdbcInbox` recibe un
`ConnectionProvider`; en Quarkus es `dataSource::getConnection`, que Agroal enlista en la transacción JTA.

## Licencia

[Eclipse Public License 2.0](LICENSE).
