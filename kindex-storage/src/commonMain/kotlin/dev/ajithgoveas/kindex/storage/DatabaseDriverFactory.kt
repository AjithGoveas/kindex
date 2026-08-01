package dev.ajithgoveas.kindex.storage

import app.cash.sqldelight.db.SqlDriver
import dev.ajithgoveas.kindex.core.io.MPFile

expect class DatabaseDriverFactory(dbFile: MPFile) {
    fun createDriver(): SqlDriver
}
