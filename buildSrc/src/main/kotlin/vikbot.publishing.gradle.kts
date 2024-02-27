plugins {
    signing
    `maven-publish`
}

publishing {
    publications {
        val projectName = project.fullName.split("-").joinToString(separator = " ") { word -> word.capitalize() }
        val projectGroup = project.groupString
        val projectVersion = project.versionString
        val projectDescription = """
            Kotlin extensions for JDA Discord library
        """.trimIndent()
        val projectUrl = Repository.projectUrl
        val projectBaseUri = Repository.projectBaseUri

        val licenseName = "MIT"
        val licenseUrl = "https://mit-license.org/"


        create<MavenPublication>("maven") {
            groupId = projectGroup
            artifactId = project.fullName
            version = projectVersion
            from(components["java"])

            pom {
                name.set(projectName)
                description.set(projectDescription)
                url.set(projectUrl)

                inceptionYear.set("2024")

                licenses {
                    license {
                        name.set(licenseName)
                        url.set(licenseUrl)
                    }
                }
                developers {
                    developer {
                        id.set("kosmx")
                        name.set("Cynthia")
                        email.set("kosmx.mc@gmail.com")
                        url.set("https://kosmx.dev/")
                    }

                    developer {
                        id.set("voroscsoki")
                        name.set("Lili")
                        //email.set("")
                        //url.set("")
                    }
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("$projectUrl/issues")
                }
                scm {
                    connection.set("scm:git:$projectUrl.git")
                    developerConnection.set("scm:git:ssh://$projectBaseUri.git")
                    url.set(projectUrl)
                }
            }
        }
    }
    repositories {
        maven {
            name = "kosmx-dev"

            url = uri("https://maven.kosmx.dev/")

            credentials(PasswordCredentials::class)
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}
