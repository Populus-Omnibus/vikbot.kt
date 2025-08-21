plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(gradlePlugin("org.jetbrains.kotlin.jvm", libs.versions.kotlin))
    implementation(gradlePlugin("com.gradleup.shadow", libs.versions.shadow))
}

tasks.compileJava {
    options.release = 17
}

kotlin {
    jvmToolchain(17)
}

fun gradlePlugin(id: String, version: Provider<String>): String {
    return "$id:$id.gradle.plugin:${version.get()}"
}
