package dev.ajithgoveas.kindex.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.storage.db.KIndexDatabase

actual class DatabaseDriverFactory actual constructor(private val dbFile: MPFile) {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(KIndexDatabase.Schema, dbFile.path)
    }
}
