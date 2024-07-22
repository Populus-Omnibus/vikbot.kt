plugins {
    vikbot.compile
    vikbot.shadow
    alias(libs.plugins.kotlin.serialization)

    vikbot.repos
}

dependencies {
    implementation(projects.api)
    implementation(libs.discord.jda)
    implementation(libs.bundles.kotlinx.serialization)
    implementation(libs.kotlin.reflect)
    implementation("commons-codec:commons-codec:1.17.1")
    implementation(libs.lavaplayer)

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlin.cli)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.bundles.exposed)

    implementation(libs.exposed.sqlite)
    implementation(libs.exposed.postgresql)

    implementation(libs.okio) // explicitly implement okio
    implementation(libs.json.json)
    implementation(libs.crypto.fernet)
    implementation(libs.rssParser)

    // Logger stuff
    implementation(libs.slf4j)
    implementation(libs.slf4k)
    implementation(libs.logback)
    implementation(libs.google.guava)

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    compileOnly(libs.google.findbugs) // Only used in library, use JetBrains nullability instead (in case something has to be written in java)
}

tasks {
    test {
        useJUnitPlatform()
    }

    jar{
        manifest {
            attributes (
                "Main-Class" to "io.github.populus_omnibus.vikbot.Launch"
            )
        }
    }

    shadowJar {
        archiveFileName = "bot-all.jar"
    }
}
