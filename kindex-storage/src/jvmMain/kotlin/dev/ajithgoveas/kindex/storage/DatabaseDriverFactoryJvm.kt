package dev.ajithgoveas.kindex.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.storage.db.KIndexDatabase
import java.io.File

actual class DatabaseDriverFactory actual constructor(private val dbFile: MPFile) {
    actual fun createDriver(): SqlDriver {
        val isInMemory = dbFile.path == ":memory:" || dbFile.path.startsWith("jdbc:sqlite::memory:")
        val connectionUrl = if (isInMemory) "jdbc:sqlite::memory:" else "jdbc:sqlite:${dbFile.path}"

        if (!isInMemory) {
            val file = File(dbFile.path)
            file.parentFile?.mkdirs()
        }

        val driver = JdbcSqliteDriver(connectionUrl)
        
        // Auto-create schema on JVM if fresh or in-memory
        if (isInMemory || !File(dbFile.path).exists() || File(dbFile.path).length() == 0L) {
            KIndexDatabase.Schema.create(driver)
        }
        
        return driver
    }
}
