plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

description = "VKurse core service: users and authentication"

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${libs.versions.spring.modulith.get()}")
    }
}

dependencies {
    implementation(project(":libs:common"))

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.security.oauth2.client)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.modulith.starter.jdbc)

    runtimeOnly(libs.postgresql)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.spring.boot.configuration.processor)
}
