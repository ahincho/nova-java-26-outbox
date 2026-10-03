-- El inbox de Nova (ADR-048): un consumidor registra cada evento que procesó, en la misma transacción que su
-- efecto. La clave primaria es la que descarta un duplicado.
create table inbox_message (
    consumer     varchar(255) not null,
    source       varchar(255) not null,
    id           varchar(255) not null,
    processed_at timestamptz  not null,
    primary key (consumer, source, id)
);
