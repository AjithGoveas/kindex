package dev.ajithgoveas.kindex.core.io

import okio.ByteString.Companion.toByteString

object HashUtils {
    fun sha256(file: MPFile): String {
        val bytes = file.readBytes()
        return bytes.toByteString().sha256().hex()
    }
}
