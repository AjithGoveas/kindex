plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
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
            implementation(libs.sqldelight.runtime)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.sqlite)
        }
        nativeMain.dependencies {
            implementation(libs.sqldelight.driver.native)
        }
    }
}

sqldelight {
    databases {
        create("KIndexDatabase") {
            packageName.set("dev.ajithgoveas.kindex.storage.db")
        }
    }
}
