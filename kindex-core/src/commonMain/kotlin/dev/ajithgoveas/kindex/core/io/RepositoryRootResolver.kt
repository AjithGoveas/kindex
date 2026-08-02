package dev.ajithgoveas.kindex.core.io

object RepositoryRootResolver {
    fun findRepositoryRoot(startDir: MPFile): MPFile {
        var current: MPFile? = if (startDir.isDirectory) startDir else startDir.parentFile
        var highestRoot: MPFile? = null
        val primaryMarkers = listOf(
            ".git",
            "settings.gradle.kts",
            "settings.gradle",
            "package.json",
            "Cargo.toml",
            "go.mod",
            "CMakeLists.txt"
        )

        // Walk up all parent directories to find the highest primary repository root (.git, settings.gradle.kts, etc.)
        while (current != null) {
            for (marker in primaryMarkers) {
                val markerFile = MPFile("${current.path}/$marker")
                if (markerFile.exists) {
                    highestRoot = current
                    break
                }
            }

            val parent = current.parentFile
            if (parent == null || parent.path == current.path) break
            current = parent
        }

        if (highestRoot != null) {
            return highestRoot
        }

        // Fallback: check for .kindex folder if no primary markers exist
        current = if (startDir.isDirectory) startDir else startDir.parentFile
        while (current != null) {
            val kindexDir = MPFile("${current.path}/.kindex")
            if (kindexDir.exists) {
                return current
            }
            val parent = current.parentFile
            if (parent == null || parent.path == current.path) break
            current = parent
        }

        return startDir
    }
}
