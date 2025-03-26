pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "vikbot"
include(":bot")
include(":api")


enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
