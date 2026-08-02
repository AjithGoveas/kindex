package dev.ajithgoveas.kindex.core.io

class RepositoryBoundaryException(message: String) : Exception(message)

object RepositoryGuardrail {

    fun isWithinRepository(target: MPFile, repoRoot: MPFile): Boolean {
        val rootCanonical = repoRoot.absolutePath.replace('\\', '/').trimEnd('/')
        val targetCanonical = target.absolutePath.replace('\\', '/').trimEnd('/')

        return targetCanonical == rootCanonical || targetCanonical.startsWith("$rootCanonical/")
    }

    fun assertWithinRepository(target: MPFile, repoRoot: MPFile) {
        if (!isWithinRepository(target, repoRoot)) {
            val rootPath = repoRoot.absolutePath
            val targetPath = target.absolutePath
            throw RepositoryBoundaryException(
                "❌ Security Error: Target path '$targetPath' is outside local repository boundaries ('$rootPath'). KIndex is strictly locked to its local repository scope."
            )
        }
    }
}
