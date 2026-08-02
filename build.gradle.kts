plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

group = "dev.ajithgoveas"
version = "1.0-SNAPSHOT"

allprojects {
    group = "dev.ajithgoveas"
    version = "1.0-SNAPSHOT"
    repositories {
        mavenCentral()
    }
}