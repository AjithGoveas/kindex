package dev.ajithgoveas.kindex.storage

import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType
import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.storage.db.KIndexDatabase

data class RepositoryStats(
    val fileCount: Long,
    val symbolCount: Long,
    val packageCount: Long,
    val classCount: Long,
    val functionCount: Long,
    val edgeCount: Long
)

class IndexStorage(dbPath: MPFile) {
    private val driver = DatabaseDriverFactory(dbPath).createDriver()
    private val database = KIndexDatabase(driver)
    private val queries = database.kIndexDatabaseQueries

    init {
        // Run SQLite performance pragmas
        try {
            driver.execute(null, "PRAGMA journal_mode = WAL;", 0)
            driver.execute(null, "PRAGMA synchronous = NORMAL;", 0)
        } catch (e: Exception) {
            // Ignore PRAGMA execution errors if some drivers restrict them
        }
    }

    fun saveResults(results: List<ParseResult>) {
        saveResultsIncremental(results, emptyList())
    }

    fun saveResultsIncremental(results: List<ParseResult>, deletedPaths: List<String>) {
        queries.transaction {
            for (path in deletedPaths) {
                queries.deleteFile(path)
                queries.clearFileData(path)
                queries.clearFileRelations(path)
            }

            for (result in results) {
                queries.deleteFile(result.sourceFile.path)
                queries.clearFileData(result.sourceFile.path)
                queries.clearFileRelations(result.sourceFile.path)

                queries.insertFile(
                    id = result.sourceFile.path,
                    path = result.sourceFile.path,
                    language = result.sourceFile.language,
                    packageName = result.sourceFile.packageName,
                    lastModified = result.sourceFile.lastModified,
                    sha256 = result.sourceFile.sha256
                )

                for (sym in result.symbols) {
                    queries.insertSymbol(
                        id = sym.id,
                        name = sym.name,
                        type = sym.type.name,
                        filePath = sym.filePath,
                        packageName = sym.packageName,
                        lineNumber = sym.lineNumber.toLong()
                    )
                }

                for (edge in result.edges) {
                    queries.insertRelationship(
                        sourceId = edge.sourceId,
                        targetId = edge.targetId,
                        relation = edge.relation.name
                    )
                }
            }
        }
    }

    fun getFilesMetadata(): Map<String, Pair<Long, String>> {
        return queries.getFilesMetadata().executeAsList().associate {
            it.path to (it.lastModified to it.sha256)
        }
    }

    fun searchSymbols(term: String): List<Symbol> {
        return queries.searchSymbols("%$term%").executeAsList().map {
            Symbol(
                id = it.id,
                name = it.name,
                type = SymbolType.valueOf(it.type),
                filePath = it.filePath,
                packageName = it.packageName,
                lineNumber = it.lineNumber.toInt()
            )
        }
    }

    fun getAllSymbols(): List<Symbol> {
        return queries.getAllSymbols().executeAsList().map {
            Symbol(
                id = it.id,
                name = it.name,
                type = SymbolType.valueOf(it.type),
                filePath = it.filePath,
                packageName = it.packageName,
                lineNumber = it.lineNumber.toInt()
            )
        }
    }

    fun getIncomingDependencies(targetId: String): List<Edge> {
        return queries.getIncomingDependencies(targetId).executeAsList().map {
            Edge(
                sourceId = it.sourceId,
                targetId = it.targetId,
                relation = RelationType.valueOf(it.relation)
            )
        }
    }

    fun getOutgoingDependencies(sourceId: String): List<Edge> {
        return queries.getOutgoingDependencies(sourceId).executeAsList().map {
            Edge(
                sourceId = it.sourceId,
                targetId = it.targetId,
                relation = RelationType.valueOf(it.relation)
            )
        }
    }

    fun getAllEdges(): List<Edge> {
        return queries.getAllEdges().executeAsList().map {
            Edge(
                sourceId = it.sourceId,
                targetId = it.targetId,
                relation = RelationType.valueOf(it.relation)
            )
        }
    }

    fun getRepositoryStats(): RepositoryStats {
        return queries.transactionWithResult {
            RepositoryStats(
                fileCount = queries.getFileCount().executeAsOne(),
                symbolCount = queries.getSymbolCount().executeAsOne(),
                packageCount = queries.getPackageCount().executeAsOne(),
                classCount = queries.getClassCount().executeAsOne(),
                functionCount = queries.getFunctionCount().executeAsOne(),
                edgeCount = queries.getEdgeCount().executeAsOne()
            )
        }
    }
}
