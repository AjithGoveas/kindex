package dev.ajithgoveas.kindex.core.io

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryGuardrailTest {

    private fun newDir(prefix: String): MPFile {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "kindex-guard" / "$prefix-${Random.nextLong()}"
        FileSystem.SYSTEM.createDirectories(dir)
        return MPFile(dir.toString())
    }

    @Test
    fun rootItselfIsWithin() {
        val root = newDir("root")
        assertTrue(RepositoryGuardrail.isWithinRepository(root, root))
    }

    @Test
    fun nestedFileIsWithin() {
        val root = newDir("root")
        val nested = MPFile("${root.path}/src/main/kotlin/App.kt")
        nested.writeText("fun main() {}")
        assertTrue(RepositoryGuardrail.isWithinRepository(nested, root))
    }

    @Test
    fun siblingDirectoryIsOutside() {
        val root = newDir("root")
        val sibling = newDir("sibling")
        assertFalse(RepositoryGuardrail.isWithinRepository(sibling, root))
    }

    @Test
    fun parentOfRootIsOutside() {
        val parent = newDir("parent")
        val root = MPFile("${parent.path}/repo")
        FileSystem.SYSTEM.createDirectories(root.path.toPath())
        assertFalse(RepositoryGuardrail.isWithinRepository(parent, root))
    }

    @Test
    fun similarlyNamedSiblingIsOutside() {
        val root = newDir("repo")
        val evil = MPFile("${root.path}-evil/file.txt")
        evil.writeText("x")
        assertFalse(RepositoryGuardrail.isWithinRepository(evil, root))
    }

    @Test
    fun caseDifferenceIsAcceptedOnWindowsStyleComparison() {
        val root = newDir("CaseRepo")
        val upper = MPFile("${root.absolutePath.uppercase()}/File.txt")
        assertTrue(RepositoryGuardrail.isWithinRepository(upper, root))
    }

    @Test
    fun assertWithinRepositoryPassesInsideAndThrowsOutside() {
        val root = newDir("root")
        val inside = MPFile("${root.path}/a.txt")
        inside.writeText("x")
        RepositoryGuardrail.assertWithinRepository(inside, root)
        val outside = newDir("outside")
        assertFailsWith<RepositoryBoundaryException> {
            RepositoryGuardrail.assertWithinRepository(outside, root)
        }
    }
}
