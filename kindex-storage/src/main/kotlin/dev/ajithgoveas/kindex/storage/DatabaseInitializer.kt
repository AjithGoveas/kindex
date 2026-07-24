package dev.ajithgoveas.kindex.storage

import dev.ajithgoveas.kindex.core.model.ParseResult
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object FilesTable : Table("files") {
    val id = varchar("id", 512)
    val path = text("path")
    val language = varchar("language", 50)
    val packageName = varchar("package_name", 256).nullable()
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

class IndexStorage(dbPath: File) {
    init {
        dbPath.parentFile?.mkdirs()
        Database.connect("jdbc:sqlite:${dbPath.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(FilesTable, SymbolsTable)
        }
    }

    fun saveResults(results: List<ParseResult>) {
        transaction {
            for (result in results) {
                FilesTable.insertIgnore {
                    it[id] = result.sourceFile.path
                    it[path] = result.sourceFile.path
                    it[language] = result.sourceFile.language
                    it[packageName] = result.sourceFile.packageName
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
            }
        }
    }
}
