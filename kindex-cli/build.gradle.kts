import java.security.MessageDigest

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
                        val nativeLibDir = rootProject.file("third_party/dist/mingwX64").absolutePath.replace('\\', '/')
                        linkerOpts("-L$nativeLibDir", "-lsqlite3", "-ltreesitter", "-Wl,--whole-archive", "-lsqlite3", "-ltreesitter", "-Wl,--no-whole-archive")
                    }
                }
            }
        }
        if (hostOs.startsWith("Linux")) {
            linuxX64 {
                binaries {
                    executable {
                        entryPoint = "dev.ajithgoveas.kindex.cli.main"
                        val nativeLibDir = rootProject.file("third_party/dist/linuxX64").absolutePath.replace('\\', '/')
                        linkerOpts("-L$nativeLibDir", "-lsqlite3", "-ltreesitter")
                    }
                }
            }
        }
        if (hostOs.startsWith("Mac OS X")) {
            macosArm64 {
                binaries {
                    executable {
                        entryPoint = "dev.ajithgoveas.kindex.cli.main"
                        val nativeLibDir = rootProject.file("third_party/dist/macosArm64").absolutePath.replace('\\', '/')
                        linkerOpts("-L$nativeLibDir", "-lsqlite3", "-ltreesitter")
                    }
                }
            }
            @Suppress("DEPRECATION")
            macosX64 {
                binaries {
                    executable {
                        entryPoint = "dev.ajithgoveas.kindex.cli.main"
                        val nativeLibDir = rootProject.file("third_party/dist/macosX64").absolutePath.replace('\\', '/')
                        linkerOpts("-L$nativeLibDir", "-lsqlite3", "-ltreesitter")
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

val hostOsName = System.getProperty("os.name")
val (targetName, buildFolder, exeName, distName) = when {
    hostOsName.startsWith("Windows") -> Quadruple("MingwX64", "mingwX64", "kindex-cli.exe", "kindex-windows-x64.exe")
    hostOsName.startsWith("Linux") -> Quadruple("LinuxX64", "linuxX64", "kindex-cli.kexe", "kindex-linux-x64")
    hostOsName.startsWith("Mac OS X") -> Quadruple("MacosArm64", "macosArm64", "kindex-cli.kexe", "kindex-macos-arm64")
    else -> Quadruple("MingwX64", "mingwX64", "kindex-cli.exe", "kindex-windows-x64.exe")
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

tasks.register("packageNative") {
    group = "distribution"
    description = "Packages the standalone native binary to dist/ directory with SHA-256 checksum."
    dependsOn(":kindex-cli:linkReleaseExecutable$targetName")
    doLast {
        val distDir = rootProject.file("dist")
        distDir.mkdirs()
        val exeFile = file("build/bin/$buildFolder/releaseExecutable/$exeName")
        if (exeFile.exists()) {
            val targetExe = File(distDir, distName)
            exeFile.copyTo(targetExe, overwrite = true)
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(targetExe.readBytes())
            val sha256 = hashBytes.joinToString("") { b -> String.format("%02x", b) }
            File(distDir, "$distName.sha256").writeText("$sha256  $distName\n")
            println("✓ Packaged native release executable to: ${targetExe.absolutePath}")
            println("  SHA-256: $sha256")
        } else {
            println("⚠ Release executable not found at: ${exeFile.absolutePath}")
        }
    }
}
