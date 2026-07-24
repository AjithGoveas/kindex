plugins {
    kotlin("jvm")
    application
}

application {
    // Updated to match your new package structure
    mainClass.set("dev.ajithgoveas.kindex.cli.MainKt")
}

dependencies {
    implementation(project(":kindex-core"))
    implementation(project(":kindex-parser"))
    implementation(project(":kindex-storage"))

    implementation("com.github.ajalt.clikt:clikt:4.3.0")
    implementation("com.github.ajalt.mordant:mordant:2.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}