plugins {
    id("pe.edu.nova.java.library")
}

description = "Conector de la salida de eventos con Spring Boot: el outbox en la transacción de Spring, con su traza, y el inbox."

val springBootVersion = "4.0.8"

dependencies {
    api(project(":nova-outbox"))

    // Spring Boot, Spring JDBC y Micrometer Tracing los trae el servicio; el starter solo compila contra ellos, con
    // las versiones de su BOM. La traza se captura solo si el servicio tiene Micrometer Tracing.
    compileOnly(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework:spring-jdbc")
    compileOnly("io.micrometer:micrometer-tracing")
    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("io.micrometer:micrometer-tracing-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.postgresql:postgresql")
}
