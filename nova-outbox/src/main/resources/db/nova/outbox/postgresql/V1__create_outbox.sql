-- La tabla del outbox de Nova (ADR-048). Sus columnas son contrato: el Event Router de Debezium las lee por
-- nombre y las convierte en el registro de Kafka y sus cabeceras de CloudEvents.
create table outbox (
    id             uuid         primary key,
    aggregate_type varchar(255) not null,
    aggregate_id   varchar(255) not null,
    type           varchar(255) not null,
    source         varchar(255) not null,
    time           timestamptz  not null,
    payload        jsonb        not null,
    traceparent    varchar(55),
    tracestate     varchar(512),
    -- La versión de CloudEvents sale como constante de la tabla: Kafka Connect no tiene cómo insertar el texto 1.0
    -- como cabecera sin convertirlo en número.
    specversion    varchar(10)  not null default '1.0'
);
