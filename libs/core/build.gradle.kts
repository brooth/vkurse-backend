plugins {
    `java-library`
}

description = "Vkurse shared library: errors, validation, aggregate base, token verification, money, notifications"

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))

    api(libs.spring.boot.starter.data.jpa)
    api(libs.spring.boot.starter.security)
    api(libs.spring.boot.starter.security.oauth2.resource.server)
    api(libs.spring.boot.starter.validation)
    api(libs.spring.boot.starter.webmvc)
    api(libs.shedlock.spring)
    implementation(libs.shedlock.provider.jdbc.template)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.thymeleaf)

    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    annotationProcessor(libs.spring.boot.configuration.processor)
}
