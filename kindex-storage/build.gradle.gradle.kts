plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":kindex-core"))

    // Exposed ORM + SQLite
    implementation("org.jetbrains.exposed:exposed-core:0.50.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
}