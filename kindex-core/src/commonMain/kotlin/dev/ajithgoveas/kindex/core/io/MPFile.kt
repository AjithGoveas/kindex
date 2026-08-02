package dev.ajithgoveas.kindex.core.io

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

class MPFile(val path: String) {
    private val okioPath = path.toPath()

    val absolutePath: String
        get() = try {
            FileSystem.SYSTEM.canonicalize(okioPath).toString()
        } catch (e: Exception) {
            okioPath.toString()
        }

    val name: String
        get() = okioPath.name

    val extension: String
        get() = name.substringAfterLast('.', "")

    val exists: Boolean
        get() = FileSystem.SYSTEM.exists(okioPath)

    val isDirectory: Boolean
        get() = FileSystem.SYSTEM.metadataOrNull(okioPath)?.isDirectory == true

    val parentFile: MPFile?
        get() = okioPath.parent?.let { MPFile(it.toString()) }

    fun readText(): String {
        return FileSystem.SYSTEM.read(okioPath) { readUtf8() }
    }

    fun readBytes(): ByteArray {
        return FileSystem.SYSTEM.read(okioPath) { readByteArray() }
    }

    fun lastModified(): Long {
        return FileSystem.SYSTEM.metadataOrNull(okioPath)?.lastModifiedAtMillis ?: 0L
    }

    fun mkdirs(): Boolean {
        FileSystem.SYSTEM.createDirectories(okioPath)
        return true
    }

    fun listFiles(): List<MPFile>? {
        if (!isDirectory) return null
        return try {
            FileSystem.SYSTEM.list(okioPath).map { MPFile(it.toString()) }
        } catch (e: Exception) {
            null
        }
    }

    override fun toString(): String = path

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MPFile) return false
        return this.path == other.path
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }
}
