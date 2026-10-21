plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

description = "Vkurse monitoring service: collects telemetry from the services and keeps it for Grafana"

// Makes `BuildProperties`, the version telemetry reports with every event.
springBoot {
    buildInfo()
}

dependencies {
    implementation(project(":libs:core"))

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    annotationProcessor(libs.spring.boot.configuration.processor)
}
