plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        mainRun {
            mainClass.set("dev.ajithgoveas.kindex.cli.MainKt")
        }
    }

    tasks.withType<JavaExec>().configureEach {
        standardInput = System.`in`
    }

    val buildNative = project.hasProperty("native")
    val hostOs = System.getProperty("os.name")

    if (buildNative) {
        if (hostOs.startsWith("Windows")) {
            mingwX64 {
                binaries {
                    executable {
                        entryPoint = "dev.ajithgoveas.kindex.cli.main"
                        linkerOpts("-LC:/Users/ajith/.konan/dependencies/lldb-2-windows/bin", "-lsqlite3")
                    }
                }
            }
        }
        if (hostOs.startsWith("Linux")) {
            linuxX64 {
                binaries {
                    executable {
                        entryPoint = "dev.ajithgoveas.kindex.cli.main"
                    }
                }
            }
        }
        if (hostOs.startsWith("Mac OS X")) {
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(libs.jline)
            implementation(libs.jansi)
        }
    }
}
