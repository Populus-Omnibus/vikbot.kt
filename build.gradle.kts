plugins {
    alias(libs.plugins.axion.release)
}

allprojects {
    group = "io.github.populus-omnibus.vikbot"
    version = rootProject.scmVersion.version
}
