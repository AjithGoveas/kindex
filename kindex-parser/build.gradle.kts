plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":kindex-core"))

    implementation(libs.tree.sitter)
    implementation(libs.tree.sitter.java)
    implementation(libs.tree.sitter.kotlin)
    implementation(libs.tree.sitter.rust)
    implementation(libs.tree.sitter.typescript)
    implementation(libs.tree.sitter.go)
}
