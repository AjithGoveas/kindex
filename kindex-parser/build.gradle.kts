plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":kindex-core"))

    implementation(libs.jtreesitter)
}
