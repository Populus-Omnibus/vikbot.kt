plugins {
    vikbot.compile
    alias(libs.plugins.kotlin.serialization)

    vikbot.repos
    vikbot.publishing
}

dependencies {

    implementation(libs.discord.jda)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.kotlin.reflect)

    implementation(libs.kotlinx.datetime)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.okio) // explicitly implement okio

    implementation(libs.slf4j)
    implementation(libs.slf4k)

}
