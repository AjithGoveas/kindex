package dev.ajithgoveas.kindex.parser

import java.io.File
import java.security.MessageDigest

object HashUtils {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
