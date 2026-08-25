package dev.ajithgoveas.kindex.parser

import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolResolverTest {

    private val resolver = SymbolResolver()

    private fun sym(
        id: String,
        name: String,
        type: SymbolType = SymbolType.CLASS,
        filePath: String,
        pkg: String? = null
    ) = Symbol(id = id, name = name, type = type, filePath = filePath, packageName = pkg, lineNumber = 1)

    private fun index(vararg symbols: Symbol) = symbols.associateBy { it.id }

    @Test
    fun exactFqnImportResolvesToFileEdge() {
        val idx = index(sym("com.a.Foo", "Foo", filePath = "src/a/Foo.kt", pkg = "com.a"))
        val result = resolver.resolveImportsDetailed("src/b/Bar.kt", listOf("com.a.Foo"), idx)

        assertEquals(1, result.resolved.size)
        assertEquals(RelationType.IMPORTS, result.resolved[0].relation)
        assertEquals("src/b/Bar.kt", result.resolved[0].sourceId)
        assertEquals("src/a/Foo.kt", result.resolved[0].targetId)
        assertTrue(result.unresolved.isEmpty())
    }

    @Test
    fun selfFileImportProducesMatchButNoEdge() {
        val idx = index(sym("com.a.Foo", "Foo", filePath = "src/a/Foo.kt", pkg = "com.a"))
        val result = resolver.resolveImportsDetailed("src/a/Foo.kt", listOf("com.a.Foo"), idx)

        assertTrue(result.resolved.isEmpty())
        assertTrue(result.unresolved.isEmpty())
    }

    @Test
    fun wildcardPackageImportMatchesAllPackageSymbols() {
        val idx = index(
            sym("com.p.Alpha", "Alpha", filePath = "src/p/A.kt", pkg = "com.p"),
            sym("com.p.Beta", "Beta", filePath = "src/p/B.kt", pkg = "com.p"),
            sym("com.q.Gamma", "Gamma", filePath = "src/q/C.kt", pkg = "com.q")
        )
        val result = resolver.resolveImportsDetailed("src/x.kt", listOf("com.p.*"), idx)

        assertEquals(2, result.resolved.size)
        assertEquals(setOf("src/p/A.kt", "src/p/B.kt"), result.resolved.map { it.targetId }.toSet())
    }

    @Test
    fun topLevelFunctionImportMapsDotToHash() {
        val idx = index(sym("com.u#util", "util", SymbolType.FUNCTION, filePath = "src/u.kt", pkg = "com.u"))
        val result = resolver.resolveImportsDetailed("src/main.kt", listOf("com.u.util"), idx)

        assertEquals(1, result.resolved.size)
        assertEquals("src/u.kt", result.resolved[0].targetId)
    }

    @Test
    fun packagePlusNameFallbackFindsClassInKnownPackage() {
        val idx = index(sym("weird#id", "Widget", filePath = "src/w/Widget.kt", pkg = "com.x"))
        val result = resolver.resolveImportsDetailed("src/main.kt", listOf("com.x.Widget"), idx)

        assertEquals(1, result.resolved.size)
        assertEquals("src/w/Widget.kt", result.resolved[0].targetId)
    }

    @Test
    fun fileNameFallbackMatchesBaseNameCaseInsensitively() {
        val idx = index(sym("Reader", "Reader", filePath = "core/reader.kt", pkg = null))
        val result = resolver.resolveImportsDetailed("src/app.kt", listOf("Reader"), idx)

        assertEquals(1, result.resolved.size)
        assertEquals("core/reader.kt", result.resolved[0].targetId)
    }

    @Test
    fun unresolvableImportIsReported() {
        val result = resolver.resolveImportsDetailed("src/app.kt", listOf("no.such.Thing"), emptyMap())

        assertTrue(result.resolved.isEmpty())
        assertEquals(listOf("no.such.Thing"), result.unresolved)
    }

    private fun callTargets(resolver: SymbolResolver, ref: String, imports: List<String>, currentPackage: String?, idx: Map<String, Symbol>, sourceId: String = "pkg#caller") =
        resolver.resolveCalls(sourceId = sourceId, unresolvedCalls = listOf(ref), imports = imports, currentPackage = currentPackage, symbolIndex = idx).map { it.targetId }

    @Test
    fun callResolvesThroughDirectImport() {
        val idx = index(
            sym("com.a.Beta", "Beta", filePath = "src/Beta.kt", pkg = "com.a"),
            sym("com.a.Beta#run", "run", SymbolType.FUNCTION, filePath = "src/Beta.kt", pkg = "com.a")
        )
        assertEquals(listOf("com.a.Beta#run"), callTargets(resolver, "REF:Beta.run", listOf("com.a.Beta"), "other.pkg", idx))
        assertEquals(listOf("com.a.Beta"), callTargets(resolver, "REF:Beta", listOf("com.a.Beta"), "other.pkg", idx))
    }

    @Test
    fun callResolvesToSiblingInSamePackage() {
        val idx = index(
            sym("com.b.Gamma#go", "go", SymbolType.FUNCTION, filePath = "src/Gamma.kt", pkg = "com.b"),
            sym("com.b#helper", "helper", SymbolType.FUNCTION, filePath = "src/helper.kt", pkg = "com.b")
        )
        assertEquals(listOf("com.b.Gamma#go"), callTargets(resolver, "REF:Gamma.go", emptyList(), "com.b", idx))
        assertEquals(listOf("com.b#helper"), callTargets(resolver, "REF:helper", emptyList(), "com.b", idx))
    }

    @Test
    fun callResolvesToLocalSameFileSymbol() {
        val idx = index(sym("f#tick", "tick", SymbolType.FUNCTION, filePath = "f.kt", pkg = null))
        assertEquals(listOf("f#tick"), callTargets(resolver, "REF:tick", emptyList(), null, idx, sourceId = "f.kt"))
    }

    @Test
    fun callResolvesThroughWildcardImport() {
        val idx = index(sym("com.w.Tool#hit", "hit", SymbolType.FUNCTION, filePath = "w/Tool.kt", pkg = "com.w"))
        assertEquals(listOf("com.w.Tool#hit"), callTargets(resolver, "REF:Tool.hit", listOf("com.w.*"), null, idx))
    }

    @Test
    fun callFallsBackToGlobalNameMatch() {
        val idx = index(sym("zap", "zap", SymbolType.FUNCTION, filePath = "z.rs", pkg = "crate"))
        assertEquals(listOf("zap"), callTargets(resolver, "REF:zap", emptyList(), null, idx))
    }

    @Test
    fun unresolvableCallProducesNoEdge() {
        val targets = callTargets(resolver, "REF:ghost", emptyList(), null, emptyMap())
        assertTrue(targets.isEmpty())
    }
}
