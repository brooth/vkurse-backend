plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

description = "Vkurse platform service: users and authentication"

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${libs.versions.spring.modulith.get()}")
    }
}

dependencies {
    implementation(project(":libs:core"))

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.security.oauth2.client)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.jdbc)

    // Not runtime-only: the chat module reads Postgres notifications through the driver's own API.
    implementation(libs.postgresql)

    annotationProcessor(libs.spring.boot.configuration.processor)
}
