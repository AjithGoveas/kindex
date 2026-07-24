plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("dev.ajithgoveas.kindex.cli.MainKt")
}

dependencies {
    implementation(project(":kindex-core"))
    implementation(project(":kindex-parser"))
    implementation(project(":kindex-storage"))

    implementation(libs.clikt)
    implementation(libs.mordant)
    implementation(libs.kotlinx.coroutines.core)
}
