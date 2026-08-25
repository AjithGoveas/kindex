plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    val buildNative = project.hasProperty("native")
    val hostOs = System.getProperty("os.name")

    if (buildNative) {
        if (hostOs.startsWith("Windows")) {
            mingwX64 {
                compilations.getByName("main") {
                    cinterops {
                        val conio by creating {
                            defFile(project.file("src/nativeInterop/cinterop/conio.def"))
                        }
                    }
                }
            }
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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
