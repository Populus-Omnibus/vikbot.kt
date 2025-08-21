plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}


tasks {
    shadowJar {
        archiveClassifier.set("all")
    }

    jar {
        //archiveClassifier.set("slim")
    }
    build {
        dependsOn(shadowJar)
    }
}