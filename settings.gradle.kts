pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
}

rootProject.name = "vikbot"
include(":api")
include(":bot")


enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
