plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.graalvm.native)
    application
}

application {
    mainClass.set("dev.ajithgoveas.kindex.cli.MainKt")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("kindex")
            mainClass.set("dev.ajithgoveas.kindex.cli.MainKt")
        }
    }
}

dependencies {
    implementation(project(":kindex-core"))
    implementation(project(":kindex-parser"))
    implementation(project(":kindex-storage"))

    implementation(libs.clikt)
    implementation(libs.mordant)
    implementation(libs.kotlinx.coroutines.core)
}
