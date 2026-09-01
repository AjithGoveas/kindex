plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

group = "dev.ajithgoveas"
version = "1.0.0"

allprojects {
    group = "dev.ajithgoveas"
    version = "1.0.0"
    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }
}

tasks.register("buildWindowsExecutable") {
    group = "build"
    description = "Compile standalone zero-dependency Windows native executable and SHA-256 checksum"
    dependsOn(":kindex-cli:packageNative")
}