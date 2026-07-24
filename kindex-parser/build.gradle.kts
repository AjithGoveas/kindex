plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":kindex-core"))

    implementation(libs.tree.sitter)
    implementation(libs.tree.sitter.java)
    implementation(libs.tree.sitter.kotlin)
}
