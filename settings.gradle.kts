plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "vkurse-backend"

include("libs:common")
include("services:core")
