plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.0.0" apply false
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

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}