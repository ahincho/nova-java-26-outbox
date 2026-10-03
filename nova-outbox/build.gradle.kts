plugins {
    id("pe.edu.nova.java.library")
}

description = "Contrato de la salida de eventos de Nova: el outbox, el inbox y sus almacenes en PostgreSQL y en memoria."

dependencies {
    // Sin dependencias: el núcleo es Java puro y los almacenes JDBC solo usan java.sql. El driver lo trae cada
    // servicio, y aquí solo lo usan las pruebas contra un PostgreSQL de verdad.
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.postgresql:postgresql:42.7.13")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
}
