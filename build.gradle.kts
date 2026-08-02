plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

group = "dev.ajithgoveas"
version = "1.0.0"

allprojects {
    group = "dev.ajithgoveas"
    version = "1.0.0"
    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension> {
            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }
}

tasks.register("buildWindowsExecutable") {
    group = "build"
    description = "Compile standalone zero-dependency Windows native executable (kindex.exe) and SHA-256 checksum"
    dependsOn(":kindex-cli:linkReleaseExecutableMingwX64")

    doLast {
        val exeFile = file("kindex-cli/build/bin/mingwX64/releaseExecutable/kindex-cli.exe")
        val altExeFile = file("kindex-cli/build/bin/mingwX64/releaseExecutable/kindex.exe")
        val targetExe = if (exeFile.exists()) exeFile else altExeFile

        if (targetExe.exists()) {
            val distDir = file("dist")
            if (!distDir.exists()) distDir.mkdirs()

            val outputExe = file("dist/kindex.exe")
            targetExe.copyTo(outputExe, overwrite = true)

            val konanDll = file("C:/Users/ajith/.konan/dependencies/lldb-2-windows/bin/sqlite3.dll")
            if (konanDll.exists()) {
                konanDll.copyTo(file("dist/sqlite3.dll"), overwrite = true)
            }

            val sha256 = java.security.MessageDigest.getInstance("SHA-256")
                .digest(outputExe.readBytes())
                .joinToString("") { "%02x".format(it) }

            val checksumFile = file("dist/kindex.exe.sha256")
            checksumFile.writeText("$sha256  kindex.exe\n")

            println("✓ Successfully compiled Windows Standalone Executable Package:")
            println("  Binary:   ${outputExe.absolutePath}")
            println("  Checksum: ${checksumFile.absolutePath} ($sha256)")
        } else {
            println("⚠️ Native executable not found at expected build output path.")
        }
    }
}