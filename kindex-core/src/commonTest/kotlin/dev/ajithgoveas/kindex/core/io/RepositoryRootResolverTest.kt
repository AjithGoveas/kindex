package dev.ajithgoveas.kindex.core.io

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepositoryRootResolverTest {

    private fun newDir(prefix: String): MPFile {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kindex-root" / "$prefix-${Random.nextLong()}"
        FileSystem.SYSTEM.createDirectories(dir)
        return MPFile(dir.toString())
    }

    private fun isAncestorOrSelf(ancestor: MPFile, descendant: MPFile): Boolean {
        val a = ancestor.absolutePath.replace('\\', '/').trimEnd('/')
        val d = descendant.absolutePath.replace('\\', '/').trimEnd('/')
        return a.equals(d, ignoreCase = true) ||
                d.startsWith("$a/", ignoreCase = true)
    }

    @Test
    fun gitMarkerDetectedFromDeepNestedDirectory() {
        val repo = newDir("repo")
        MPFile("${repo.path}/.git").mkdirs()
        val deep = MPFile("${repo.path}/src/main/kotlin/dev/app")
        deep.mkdirs()

        val found = RepositoryRootResolver.findRepositoryRoot(deep)

        assertTrue(
            isAncestorOrSelf(found, repo),
            "Resolved root '${found.absolutePath}' must be an ancestor of the marker dir '${repo.absolutePath}'"
        )
    }

    @Test
    fun gradleSettingsMarkerDetected() {
        val repo = newDir("gradle-repo")
        MPFile("${repo.path}/settings.gradle.kts").writeText("rootProject.name = \"x\"")
        val nested = MPFile("${repo.path}/module-a/src")
        nested.mkdirs()

        val found = RepositoryRootResolver.findRepositoryRoot(nested)

        assertTrue(isAncestorOrSelf(found, nested))
    }

    @Test
    fun kindexFallbackUsedWhenNoPrimaryMarkersAbove() {
        val base = newDir("fallback-base")
        val probe = MPFile("${base.path}/probe-${Random.nextLong()}")
        probe.mkdirs()

        val found = RepositoryRootResolver.findRepositoryRoot(probe)

        assertTrue(
            isAncestorOrSelf(found, probe),
            "Without markers the resolver must return the start directory or an ancestor"
        )
        assertTrue(found.exists)
    }

    @Test
    fun fileInputResolvesViaItsParentDirectory() {
        val repo = newDir("file-repo")
        MPFile("${repo.path}/.git").mkdirs()
        val file = MPFile("${repo.path}/src/App.kt")
        file.writeText("class App")

        val found = RepositoryRootResolver.findRepositoryRoot(file)

        assertTrue(isAncestorOrSelf(found, repo))
    }
}
