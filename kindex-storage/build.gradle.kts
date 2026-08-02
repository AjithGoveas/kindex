plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm()

    val buildNative = project.hasProperty("native")
    val hostOs = System.getProperty("os.name")

    if (buildNative) {
        if (hostOs.startsWith("Windows")) {
            mingwX64()
        }
        if (hostOs.startsWith("Linux")) {
            linuxX64()
        }
        if (hostOs.startsWith("Mac OS X")) {
            macosArm64()
            macosX64()
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kindex-core"))
            implementation(libs.sqldelight.runtime)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.sqlite)
        }
        findByName("nativeMain")?.dependencies {
            implementation(libs.sqldelight.driver.native)
        }
    }
}

sqldelight {
    databases {
        create("KIndexDatabase") {
            packageName.set("dev.ajithgoveas.kindex.storage.db")
            verifyMigrations.set(false)
        }
    }
}

tasks.matching { it.name.startsWith("verify") && it.name.contains("Migration") }.configureEach {
    enabled = false
}
