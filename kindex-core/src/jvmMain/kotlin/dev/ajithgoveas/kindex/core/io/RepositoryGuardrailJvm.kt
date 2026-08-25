package dev.ajithgoveas.kindex.core.io

actual val isFileSystemCaseSensitive: Boolean = run {
    val os = System.getProperty("os.name", "").lowercase()
    !os.contains("win") && !os.contains("mac")
}
