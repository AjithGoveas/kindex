package dev.ajithgoveas.kindex.cli

import dev.ajithgoveas.kindex.core.io.MPFile
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalkFilesTest {

    private fun newRoot(): MPFile {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kindex-walk" / "root-${Random.nextLong()}"
        FileSystem.SYSTEM.createDirectories(dir)
        return MPFile(dir.toString())
    }

    private fun write(root: MPFile, relative: String, content: String = "x") {
        MPFile("${root.path}/$relative").writeText(content)
    }

    @Test
    fun skipsGradleBuildAndKindexDirectories() {
        val root = newRoot()
        write(root, "src/Main.kt")
        write(root, "build/out/Generated.kt")
        write(root, ".gradle/cache.txt")
        write(root, "kindex-core/build/libs/core.jar.txt")
        write(root, "dist/kindex.exe.txt")

        val found = walkFiles(root).map { it.path.replace('\\', '/') }

        assertTrue(found.any { it.endsWith("/src/Main.kt") }, "source file must be walked: $found")
        assertFalse(found.any { it.contains("/build/") }, "build dirs must be skipped: $found")
        assertFalse(found.any { it.contains("/.gradle/") }, ".gradle must be skipped: $found")
        assertFalse(found.any { it.contains("/dist/") }, "dist must be skipped: $found")
    }

    @Test
    fun nestedSourceTreeIsFullyWalked() {
        val root = newRoot()
        write(root, "a/b/c/Deep.kt")
        write(root, "a/Sibling.kt")

        val found = walkFiles(root).map { it.path.replace('\\', '/') }

        assertEquals(setOf("/a/b/c/Deep.kt", "/a/Sibling.kt"), found.map { it.removePrefix(root.path.replace('\\', '/')) }.toSet())
    }

    @Test
    fun nonDirectoryReturnsEmpty() {
        val root = newRoot()
        write(root, "file.kt")
        val file = MPFile("${root.path}/file.kt")

        assertTrue(walkFiles(file).isEmpty())
    }
}
