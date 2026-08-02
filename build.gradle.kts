plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

group = "dev.ajithgoveas"
version = "1.0-SNAPSHOT"

allprojects {
    group = "dev.ajithgoveas"
    version = "1.0-SNAPSHOT"
    repositories {
        mavenCentral()
    }
}

tasks.register("installLauncher") {
    group = "application"
    description = "Assemble standalone root and .kindex/ launcher executables (./kindex & kindex.bat)"
    dependsOn(":kindex-cli:jvmJar")

    doLast {
        val kindexDir = file(".kindex")
        if (!kindexDir.exists()) kindexDir.mkdirs()

        val posixScriptContent = """
            #!/bin/sh
            SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
            ROOT_DIR="${'$'}SCRIPT_DIR"
            if [ ! -f "${'$'}ROOT_DIR/gradlew" ]; then
                ROOT_DIR="${'$'}SCRIPT_DIR/.."
            fi

            exec "${'$'}ROOT_DIR/gradlew" :kindex-cli:jvmRun --args="${'$'}*" -q
        """.trimIndent() + "\n"

        val windowsScriptContent = """
            @echo off
            set SCRIPT_DIR=%~dp0
            set ROOT_DIR=%SCRIPT_DIR%
            if not exist "%ROOT_DIR%gradlew.bat" (
                set ROOT_DIR=%SCRIPT_DIR%..\
            )

            call "%ROOT_DIR%gradlew.bat" :kindex-cli:jvmRun --args="%*" -q
        """.trimIndent() + "\n"

        // Root executables
        val rootPosix = file("kindex")
        val rootWin = file("kindex.bat")
        rootPosix.writeText(posixScriptContent)
        rootWin.writeText(windowsScriptContent)
        rootPosix.setExecutable(true, false)

        // .kindex executables
        val kindexPosix = file(".kindex/kindex")
        val kindexWin = file(".kindex/kindex.bat")
        kindexPosix.writeText(posixScriptContent)
        kindexWin.writeText(windowsScriptContent)
        kindexPosix.setExecutable(true, false)

        println("✓ Successfully installed root executable launchers: ./kindex & kindex.bat (and inside .kindex/)")
    }
}