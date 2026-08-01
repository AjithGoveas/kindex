package dev.ajithgoveas.kindex.storage

import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.io.MPFile

data class RepositoryStats(
    val fileCount: Long,
    val symbolCount: Long,
    val packageCount: Long,
    val classCount: Long,
    val functionCount: Long,
    val edgeCount: Long
)

expect class IndexStorage(dbPath: MPFile) {
    fun saveResults(results: List<ParseResult>)
    fun saveResultsIncremental(results: List<ParseResult>, deletedPaths: List<String>)
    fun getFilesMetadata(): Map<String, Pair<Long, String>>
    fun searchSymbols(term: String): List<Symbol>
    fun getAllSymbols(): List<Symbol>
    fun getIncomingDependencies(targetId: String): List<Edge>
    fun getOutgoingDependencies(sourceId: String): List<Edge>
    fun getAllEdges(): List<Edge>
    fun getRepositoryStats(): RepositoryStats
}
