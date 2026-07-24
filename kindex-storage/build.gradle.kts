plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":kindex-core"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
}
