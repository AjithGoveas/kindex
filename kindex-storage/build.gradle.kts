plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    mingwX64()
    linuxX64()
    macosArm64()
    macosX64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kindex-core"))
        }
        jvmMain.dependencies {
            implementation(libs.exposed.core)
            implementation(libs.exposed.dao)
            implementation(libs.exposed.jdbc)
            implementation(libs.sqlite.jdbc)
        }
    }
}
