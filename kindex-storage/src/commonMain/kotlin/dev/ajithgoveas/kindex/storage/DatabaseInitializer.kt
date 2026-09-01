package dev.ajithgoveas.kindex.storage

import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType
import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.storage.db.KIndexDatabase
import app.cash.sqldelight.db.QueryResult

data class RepositoryStats(
    val fileCount: Long,
    val symbolCount: Long,
    val packageCount: Long,
    val classCount: Long,
    val functionCount: Long,
    val edgeCount: Long
)

object SymbolTokenizer {
    fun tokenize(name: String): String {
        val tokens = mutableSetOf<String>()
        tokens.add(name)

        val parts = name.split(Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|_|\\$|::|\\."))
            .filter { it.isNotBlank() }

        tokens.addAll(parts)
        return tokens.joinToString(" ")
    }
}

class IndexStorage(dbPath: MPFile) {
    private val driver = DatabaseDriverFactory(dbPath).createDriver()
    private val database = KIndexDatabase(driver)
    private val queries = database.kIndexDatabaseQueries

    var ftsSearchAvailable: Boolean = false
        private set

    init {
        // Run SQLite performance pragmas
        try {
            driver.executeQuery(null, "PRAGMA journal_mode = WAL;", { QueryResult.Value(Unit) }, 0)
            driver.execute(null, "PRAGMA synchronous = NORMAL;", 0)
        } catch (e: Exception) {
            // Ignore PRAGMA execution errors if some drivers restrict them
        }

        // Initialize SQLite FTS5 Virtual Table for tokenized fuzzy searches
        try {
            driver.execute(
                null,
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS symbols_fts USING fts5(
                    symbolId,
                    name,
                    tokens,
                    type,
                    filePath,
                    packageName,
                    lineNumber
                );
                """.trimIndent(),
                0
            )
            ftsSearchAvailable = true
        } catch (e: Exception) {
            // Ignore if FTS5 is unsupported on a specific platform
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
                try {
                    driver.execute(null, "DELETE FROM symbols_fts WHERE filePath = ?;", 1) {
                        bindString(0, path)
                    }
                } catch (e: Exception) {
                    // Ignore FTS errors
                }
            }

            for (result in results) {
                queries.deleteFile(result.sourceFile.path)
                queries.clearFileData(result.sourceFile.path)
                queries.clearFileRelations(result.sourceFile.path)
                try {
                    driver.execute(null, "DELETE FROM symbols_fts WHERE filePath = ?;", 1) {
                        bindString(0, result.sourceFile.path)
                    }
                } catch (e: Exception) {
                    // Ignore FTS errors
                }

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

                    try {
                        val tokens = SymbolTokenizer.tokenize(sym.name)
                        driver.execute(
                            null,
                            "INSERT INTO symbols_fts(symbolId, name, tokens, type, filePath, packageName, lineNumber) VALUES (?, ?, ?, ?, ?, ?, ?);",
                            7
                        ) {
                            bindString(0, sym.id)
                            bindString(1, sym.name)
                            bindString(2, tokens)
                            bindString(3, sym.type.name)
                            bindString(4, sym.filePath)
                            bindString(5, sym.packageName ?: "")
                            bindLong(6, sym.lineNumber.toLong())
                        }
                    } catch (e: Exception) {
                        // Ignore FTS error
                    }
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
        val trimmed = term.trim()
        if (trimmed.isNotEmpty()) {
            try {
                val cleanTerm = trimmed.replace(Regex("[^a-zA-Z0-9_]"), " ")
                val formattedQuery = cleanTerm.split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .joinToString(" ") { "$it*" }

                if (formattedQuery.isBlank()) {
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

                val ftsQuerySql = """
                    SELECT symbolId, name, type, filePath, packageName, lineNumber 
                    FROM symbols_fts 
                    WHERE tokens MATCH ? 
                    LIMIT 100
                """.trimIndent()

                val queryResult = driver.executeQuery(
                    identifier = null,
                    sql = ftsQuerySql,
                    mapper = { cursor ->
                        val list = mutableListOf<Symbol>()
                        while (cursor.next().value) {
                            val id = cursor.getString(0)!!
                            val name = cursor.getString(1)!!
                            val typeStr = cursor.getString(2)!!
                            val filePath = cursor.getString(3)!!
                            val pkg = cursor.getString(4)
                            val line = cursor.getLong(5)?.toInt() ?: 1
                            list.add(
                                Symbol(
                                    id = id,
                                    name = name,
                                    type = SymbolType.valueOf(typeStr),
                                    filePath = filePath,
                                    packageName = if (pkg.isNullOrEmpty()) null else pkg,
                                    lineNumber = line
                                )
                            )
                        }
                        QueryResult.Value(list)
                    },
                    parameters = 1,
                    binders = {
                        bindString(0, formattedQuery)
                    }
                )

                val ftsResults = queryResult.value
                if (ftsResults.isNotEmpty()) {
                    return ftsResults
                }
            } catch (e: Exception) {
                // Fall back to standard SQL LIKE query
            }
        }

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
