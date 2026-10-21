plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "vkurse-backend"

include("libs:core")
include("services:platform")
include("services:monitoring")
