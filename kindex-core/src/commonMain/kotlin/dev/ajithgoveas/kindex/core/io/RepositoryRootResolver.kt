package dev.ajithgoveas.kindex.core.io

object RepositoryRootResolver {
    fun findRepositoryRoot(startDir: MPFile): MPFile {
        var current: MPFile? = if (startDir.isDirectory) startDir else startDir.parentFile
        val primaryMarkers = listOf(
            ".kindex",
            ".git",
            "settings.gradle.kts",
            "settings.gradle",
            "package.json",
            "Cargo.toml",
            "go.mod",
            "CMakeLists.txt"
        )

        while (current != null) {
            for (marker in primaryMarkers) {
                val markerFile = MPFile("${current.path}/$marker")
                if (markerFile.exists) {
                    return current
                }
            }

            val parent = current.parentFile
            if (parent == null || parent.path == current.path) break
            current = parent
        }

        return startDir
    }
}
