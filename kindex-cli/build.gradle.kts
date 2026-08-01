plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        mainRun {
            mainClass.set("dev.ajithgoveas.kindex.cli.MainKt")
        }
    }

    mingwX64 {
        binaries {
            executable {
                entryPoint = "dev.ajithgoveas.kindex.cli.main"
            }
        }
    }
    linuxX64 {
        binaries {
            executable {
                entryPoint = "dev.ajithgoveas.kindex.cli.main"
            }
        }
    }
    macosArm64 {
        binaries {
            executable {
                entryPoint = "dev.ajithgoveas.kindex.cli.main"
            }
        }
    }
    macosX64 {
        binaries {
            executable {
                entryPoint = "dev.ajithgoveas.kindex.cli.main"
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":kindex-core"))
            implementation(project(":kindex-parser"))
            implementation(project(":kindex-storage"))
            implementation(libs.clikt)
            implementation(libs.mordant)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
        }
        jvmMain.dependencies {
            implementation(libs.jline)
            implementation(libs.jansi)
        }
    }
}
