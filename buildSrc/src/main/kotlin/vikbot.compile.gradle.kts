import org.gradle.kotlin.dsl.kotlin

val targetJavaVersion = 17

plugins {
    java
    kotlin("jvm")
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    compileJava {
        options.release = targetJavaVersion // If it breaks (I don't think it will), use options.release.set(targetJavaVersion)
    }


    withType<Javadoc>().configureEach {
        options {
            encoding = "UTF-8"
        }
    }

    withType<Jar>().configureEach {
        from(rootProject.file("LICENSE"))

        doLast {
            manifest {
                attributes(
                    "Implementation-Title" to project.fullName,
                    "Implementation-Version" to project.version.toString(),
                )
                if (ext.has("mainClass")) {
                    attributes(
                        "Main-Class" to ext["mainClass"],
                    )
                }
            }
        }
    }
}
