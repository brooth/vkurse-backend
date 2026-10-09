import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.spotless) apply false
}

val palantirJavaFormat = libs.versions.palantir.java.format.get()

// Only projects with a build file (libs/core, services/platform), not the `libs` and `services` folders.
configure(subprojects.filter { it.buildFile.exists() }) {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    group = "vkurse"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
    }

    configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            palantirJavaFormat(palantirJavaFormat)
            removeUnusedImports()
            licenseHeaderFile(rootProject.file("config/license-header.txt"))
        }
    }
}
