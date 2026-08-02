@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.ajithgoveas.kindex.core.io

import conio._getch
import conio.enableVT100
import conio.queryTerminalSize
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.getenv

actual fun enableRawMode() {
    // Enable VT100/ANSI escape code processing on Windows 10+ console
    try { enableVT100() } catch (_: Throwable) { }
}

actual fun disableRawMode() {
    // No cleanup needed — Windows terminal restores state on process exit
}

actual fun readKey(): KeyEvent {
    val b = _getch()
    return when (b) {
        13, 10  -> KeyEvent.Enter
        27      -> KeyEvent.Escape
        0, 224  -> {
            // Extended key prefix — second byte carries the scan code
            when (_getch()) {
                72 -> KeyEvent.Up       // ↑
                80 -> KeyEvent.Down     // ↓
                75 -> KeyEvent.Left     // ←
                77 -> KeyEvent.Right    // →
                else -> KeyEvent.Unknown
            }
        }
        8, 127  -> KeyEvent.Backspace
        9       -> KeyEvent.Tab
        3       -> KeyEvent.Escape   // Ctrl-C
        else    -> if (b in 32..126) KeyEvent.Character(b.toChar()) else KeyEvent.Unknown
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun getTerminalSize(): Pair<Int, Int> {
    val envW = getenv("COLUMNS")?.toKString()?.toIntOrNull()
    val envH = getenv("LINES")?.toKString()?.toIntOrNull()
    if (envW != null && envH != null) return Pair(envW, envH)

    // Query the real console window via Win32 API (COLUMNS/LINES are usually
    // unset on Windows, so the hardcoded fallback is a poor second choice).
    return try {
        memScoped {
            val cols = alloc<IntVar>()
            val rows = alloc<IntVar>()
            if (queryTerminalSize(cols.ptr, rows.ptr) == 1) {
                Pair(cols.value.coerceAtLeast(80), rows.value.coerceAtLeast(20))
            } else fallbackSize()
        }
    } catch (_: Throwable) {
        fallbackSize()
    }
}

private fun fallbackSize(): Pair<Int, Int> = Pair(120, 30)
