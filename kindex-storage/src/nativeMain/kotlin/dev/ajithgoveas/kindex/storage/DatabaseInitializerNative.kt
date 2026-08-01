package dev.ajithgoveas.kindex.storage

import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.io.MPFile

actual class IndexStorage actual constructor(dbPath: MPFile) {
    actual fun saveResults(results: List<ParseResult>) {}
    actual fun saveResultsIncremental(results: List<ParseResult>, deletedPaths: List<String>) {}
    actual fun getFilesMetadata(): Map<String, Pair<Long, String>> = emptyMap()
    actual fun searchSymbols(term: String): List<Symbol> = emptyList()
    actual fun getAllSymbols(): List<Symbol> = emptyList()
    actual fun getIncomingDependencies(targetId: String): List<Edge> = emptyList()
    actual fun getOutgoingDependencies(sourceId: String): List<Edge> = emptyList()
    actual fun getAllEdges(): List<Edge> = emptyList()
    actual fun getRepositoryStats(): RepositoryStats = RepositoryStats(0, 0, 0, 0, 0, 0)
}
