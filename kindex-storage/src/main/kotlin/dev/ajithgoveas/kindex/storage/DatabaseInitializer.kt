package dev.ajithgoveas.kindex.storage

import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType
import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.RelationType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object FilesTable : Table("files") {
    val id = varchar("id", 512)
    val path = text("path")
    val language = varchar("language", 50)
    val packageName = varchar("package_name", 256).nullable()
    val lastModified = long("last_modified").default(0L)
    val sha256 = varchar("sha256", 64).default("")
    override val primaryKey = PrimaryKey(id)
}

object SymbolsTable : Table("symbols") {
    val id = varchar("id", 512)
    val name = varchar("name", 256)
    val type = varchar("type", 50)
    val filePath = text("file_path")
    val packageName = varchar("package_name", 256).nullable()
    val lineNumber = integer("line_number")
    override val primaryKey = PrimaryKey(id)
}

object RelationshipsTable : Table("relationships") {
    val sourceId = varchar("source_id", 512)
    val targetId = varchar("target_id", 512)
    val relation = varchar("relation", 50)
    override val primaryKey = PrimaryKey(sourceId, targetId, relation)
}

data class RepositoryStats(
    val fileCount: Long,
    val symbolCount: Long,
    val packageCount: Long,
    val classCount: Long,
    val functionCount: Long,
    val edgeCount: Long
)

class IndexStorage(dbPath: File) {
    init {
        dbPath.parentFile?.mkdirs()
        Database.connect("jdbc:sqlite:${dbPath.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(FilesTable, SymbolsTable, RelationshipsTable)
        }
    }

    fun saveResults(results: List<ParseResult>) {
        saveResultsIncremental(results, emptyList())
    }

    fun saveResultsIncremental(results: List<ParseResult>, deletedPaths: List<String>) {
        transaction {
            // Prune deleted files
            for (path in deletedPaths) {
                FilesTable.deleteWhere { FilesTable.path eq path }
                SymbolsTable.deleteWhere { SymbolsTable.filePath eq path }
                RelationshipsTable.deleteWhere { RelationshipsTable.sourceId eq path }
            }

            // Insert new/modified results
            for (result in results) {
                // Delete existing records to avoid primary key conflicts
                FilesTable.deleteWhere { FilesTable.path eq result.sourceFile.path }
                SymbolsTable.deleteWhere { SymbolsTable.filePath eq result.sourceFile.path }
                RelationshipsTable.deleteWhere { RelationshipsTable.sourceId eq result.sourceFile.path }

                FilesTable.insertIgnore {
                    it[id] = result.sourceFile.path
                    it[path] = result.sourceFile.path
                    it[language] = result.sourceFile.language
                    it[packageName] = result.sourceFile.packageName
                    it[lastModified] = result.sourceFile.lastModified
                    it[sha256] = result.sourceFile.sha256
                }

                for (sym in result.symbols) {
                    SymbolsTable.insertIgnore {
                        it[id] = sym.id
                        it[name] = sym.name
                        it[type] = sym.type.name
                        it[filePath] = sym.filePath
                        it[packageName] = sym.packageName
                        it[lineNumber] = sym.lineNumber
                    }
                }

                for (edge in result.edges) {
                    RelationshipsTable.insertIgnore {
                        it[sourceId] = edge.sourceId
                        it[targetId] = edge.targetId
                        it[relation] = edge.relation.name
                    }
                }
            }
        }
    }

    fun getFilesMetadata(): Map<String, Pair<Long, String>> {
        return transaction {
            FilesTable.selectAll().associate { row ->
                row[FilesTable.path] to (row[FilesTable.lastModified] to row[FilesTable.sha256])
            }
        }
    }

    fun searchSymbols(term: String): List<Symbol> {
        return transaction {
            SymbolsTable.selectAll()
                .where { SymbolsTable.name like "%$term%" }
                .map { row ->
                    Symbol(
                        id = row[SymbolsTable.id],
                        name = row[SymbolsTable.name],
                        type = SymbolType.valueOf(row[SymbolsTable.type]),
                        filePath = row[SymbolsTable.filePath],
                        packageName = row[SymbolsTable.packageName],
                        lineNumber = row[SymbolsTable.lineNumber]
                    )
                }
        }
    }

    fun getAllSymbols(): List<Symbol> {
        return transaction {
            SymbolsTable.selectAll()
                .map { row ->
                    Symbol(
                        id = row[SymbolsTable.id],
                        name = row[SymbolsTable.name],
                        type = SymbolType.valueOf(row[SymbolsTable.type]),
                        filePath = row[SymbolsTable.filePath],
                        packageName = row[SymbolsTable.packageName],
                        lineNumber = row[SymbolsTable.lineNumber]
                    )
                }
        }
    }

    fun getIncomingDependencies(targetId: String): List<Edge> {
        return transaction {
            RelationshipsTable.selectAll()
                .where { RelationshipsTable.targetId eq targetId }
                .map { row ->
                    Edge(
                        sourceId = row[RelationshipsTable.sourceId],
                        targetId = row[RelationshipsTable.targetId],
                        relation = RelationType.valueOf(row[RelationshipsTable.relation])
                    )
                }
        }
    }

    fun getOutgoingDependencies(sourceId: String): List<Edge> {
        return transaction {
            RelationshipsTable.selectAll()
                .where { RelationshipsTable.sourceId eq sourceId }
                .map { row ->
                    Edge(
                        sourceId = row[RelationshipsTable.sourceId],
                        targetId = row[RelationshipsTable.targetId],
                        relation = RelationType.valueOf(row[RelationshipsTable.relation])
                    )
                }
        }
    }

    fun getAllEdges(): List<Edge> {
        return transaction {
            RelationshipsTable.selectAll()
                .map { row ->
                    Edge(
                        sourceId = row[RelationshipsTable.sourceId],
                        targetId = row[RelationshipsTable.targetId],
                        relation = RelationType.valueOf(row[RelationshipsTable.relation])
                    )
                }
        }
    }

    fun getRepositoryStats(): RepositoryStats {
        return transaction {
            val files = FilesTable.selectAll().count()
            val symbols = SymbolsTable.selectAll().count()
            val packages = SymbolsTable.select(SymbolsTable.packageName)
                .where { SymbolsTable.packageName.isNotNull() }
                .withDistinct()
                .count()
            val classes = SymbolsTable.selectAll()
                .where { (SymbolsTable.type eq SymbolType.CLASS.name) or (SymbolsTable.type eq SymbolType.INTERFACE.name) }
                .count()
            val functions = SymbolsTable.selectAll()
                .where { SymbolsTable.type eq SymbolType.FUNCTION.name }
                .count()
            val edges = RelationshipsTable.selectAll().count()

            RepositoryStats(
                fileCount = files,
                symbolCount = symbols,
                packageCount = packages,
                classCount = classes,
                functionCount = functions,
                edgeCount = edges
            )
        }
    }
}
