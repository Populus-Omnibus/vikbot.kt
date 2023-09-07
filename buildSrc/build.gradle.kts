plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(gradlePlugin("org.jetbrains.kotlin.jvm", libs.versions.kotlin))
    implementation(gradlePlugin("com.github.johnrengelman.shadow", libs.versions.shadow))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

fun gradlePlugin(id: String, version: Provider<String>): String {
    return "$id:$id.gradle.plugin:${version.get()}"
}
