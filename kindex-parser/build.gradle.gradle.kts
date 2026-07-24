plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":kindex-core"))

    // Java/Kotlin Tree-sitter bindings
    implementation("io.github.treesitter:jtreesitter:0.22.0")
    // Java and Kotlin grammar loaders
    implementation("io.github.treesitter:jtreesitter-java:0.22.0")
    implementation("io.github.treesitter:jtreesitter-kotlin:0.22.0")
}