package dev.ajithgoveas.kindex.storage

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.storage.db.KIndexDatabase

private object ExistingDbSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long get() = KIndexDatabase.Schema.version
    override fun create(driver: SqlDriver): QueryResult.Value<Unit> = QueryResult.Value(Unit)
    override fun migrate(driver: SqlDriver, oldVersion: Long, newVersion: Long, vararg callbacks: AfterVersion): QueryResult.Value<Unit> = QueryResult.Value(Unit)
}

actual class DatabaseDriverFactory actual constructor(private val dbFile: MPFile) {
    actual fun createDriver(): SqlDriver {
        val parentDir = dbFile.parentFile?.absolutePath ?: "."
        val dbName = dbFile.name
        val dbExists = dbFile.exists

        return NativeSqliteDriver(
            schema = if (dbExists) ExistingDbSchema else KIndexDatabase.Schema,
            name = dbName,
            onConfiguration = { config ->
                config.copy(
                    extendedConfig = config.extendedConfig.copy(
                        basePath = parentDir
                    )
                )
            }
        )
    }
}
