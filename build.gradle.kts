plugins {
    alias(libs.plugins.kotlin.jvm)
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

kotlin {
    jvmToolchain(21)
}