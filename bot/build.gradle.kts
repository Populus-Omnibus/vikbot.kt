plugins {
    vikbot.compile
    vikbot.shadow
    alias(libs.plugins.kotlin.serialization)

    vikbot.repos
}

dependencies {
    //implementation(projects.api)
    implementation(libs.discord.jda)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.kotlin.reflect)

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlin.cli)

    // Logger stuff
    implementation(libs.slf4j)
    implementation(libs.slf4k)
    implementation(libs.logback)

}