plugins {
    vikbot.compile
    vikbot.shadow
    alias(libs.plugins.kotlin.serialization)

    vikbot.repos
}

dependencies {
    implementation(projects.api)


}