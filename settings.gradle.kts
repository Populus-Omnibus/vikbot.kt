pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

rootProject.name = "vikbot"
include(":bot")
include(":api")


enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
