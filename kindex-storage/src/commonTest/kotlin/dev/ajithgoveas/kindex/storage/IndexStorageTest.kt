package dev.ajithgoveas.kindex.storage

import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.SourceFile
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexStorageTest {

    private fun result(path: String, symbols: List<Symbol>, edges: List<Edge>) = ParseResult(
        sourceFile = SourceFile(
            path = path,
            language = "Kotlin",
            packageName = "com.a",
            lastModified = 100L,
            sha256 = "deadbeef"
        ),
        symbols = symbols,
        edges = edges
    )

    @Test
    fun roundTripSearchAndIncrementalPrune() {
        val storage = IndexStorage(MPFile(":memory:"))

        val resolverSym = Symbol("com.a.Resolver", "Resolver", SymbolType.CLASS, "src/a.kt", "com.a", 3)
        val buildFn = Symbol("com.a#build", "build", SymbolType.FUNCTION, "src/b.kt", "com.a", 10)

        storage.saveResultsIncremental(
            listOf(
                result("src/a.kt", listOf(resolverSym), listOf(Edge("src/a.kt", "com.a.Resolver", RelationType.CONTAINS))),
                result("src/b.kt", listOf(buildFn), listOf(Edge("com.a#build", "com.a.Resolver", RelationType.CALLS)))
            ),
            emptyList()
        )

        assertEquals(2, storage.getAllSymbols().size)
        assertEquals(2, storage.getAllEdges().size)
        assertTrue(storage.getFilesMetadata().containsKey("src/a.kt"))

        val stats = storage.getRepositoryStats()
        assertEquals(2L, stats.fileCount)
        assertEquals(2L, stats.symbolCount)
        assertEquals(2L, stats.edgeCount)
        assertEquals(1L, stats.classCount)
        assertEquals(1L, stats.functionCount)

        val ftsHits = storage.searchSymbols("Resol")
        assertEquals(1, ftsHits.size)
        assertEquals("com.a.Resolver", ftsHits[0].id)
        assertEquals(SymbolType.CLASS, ftsHits[0].type)

        val likeFallback = storage.searchSymbols("%zzz-nothing%")
        assertTrue(likeFallback.isEmpty())

        storage.saveResultsIncremental(emptyList(), listOf("src/a.kt"))

        val remaining = storage.getAllSymbols()
        assertEquals(listOf("com.a#build"), remaining.map { it.id })
        assertEquals(listOf("com.a#build" to "com.a.Resolver"), storage.getAllEdges().map { it.sourceId to it.targetId })
        assertFalse(storage.getFilesMetadata().containsKey("src/a.kt"))
    }

    @Test
    fun resavingSameFileReplacesWithoutDuplicates() {
        val storage = IndexStorage(MPFile(":memory:"))
        val sym = Symbol("com.x.Widget", "Widget", SymbolType.CLASS, "src/w.kt", "com.x", 4)
        val res = result("src/w.kt", listOf(sym), listOf(Edge("src/w.kt", sym.id, RelationType.CONTAINS)))

        storage.saveResultsIncremental(listOf(res), emptyList())
        storage.saveResultsIncremental(listOf(res.copy(sourceFile = res.sourceFile.copy(lastModified = 200L))), emptyList())

        assertEquals(1, storage.getAllSymbols().size)
        assertEquals(1, storage.getAllEdges().size)
        assertEquals(200L, storage.getFilesMetadata()["src/w.kt"]?.first)
    }

    @Test
    fun dependencyLookupsReturnIncomingAndOutgoing() {
        val storage = IndexStorage(MPFile(":memory:"))
        val target = Symbol("com.t.Core", "Core", SymbolType.CLASS, "src/core.kt", "com.t", 1)
        val callerFn = Symbol("com.t#useCore", "useCore", SymbolType.FUNCTION, "src/use.kt", "com.t", 5)

        storage.saveResultsIncremental(
            listOf(
                result("src/core.kt", listOf(target), emptyList()),
                result("src/use.kt", listOf(callerFn), listOf(Edge(callerFn.id, target.id, RelationType.CALLS)))
            ),
            emptyList()
        )

        assertEquals(
            listOf("com.t#useCore"),
            storage.getIncomingDependencies(target.id).map { it.sourceId }
        )
        assertEquals(
            listOf("com.t.Core"),
            storage.getOutgoingDependencies(callerFn.id).map { it.targetId }
        )
    }
}
