package dev.ajithgoveas.kindex.core.io

actual fun readKey(): KeyEvent {
    val b = System.`in`.read()
    if (b < 0) return KeyEvent.Eof
    return when (b) {
        13, 10       -> KeyEvent.Enter
        27           -> {
            // Possible ANSI escape sequence — peek ahead
            if (System.`in`.available() > 0) {
                val b2 = System.`in`.read()
                if (b2 == '['.code && System.`in`.available() > 0) {
                    when (val b3 = System.`in`.read()) {
                        'A'.code -> KeyEvent.Up
                        'B'.code -> KeyEvent.Down
                        'C'.code -> KeyEvent.Right
                        'D'.code -> KeyEvent.Left
                        'H'.code, '1'.code, '7'.code -> { drainToTilde(); KeyEvent.Home }
                        'F'.code, '4'.code, '8'.code -> { drainToTilde(); KeyEvent.End }
                        '5'.code -> { drainToTilde(); KeyEvent.PageUp }
                        '6'.code -> { drainToTilde(); KeyEvent.PageDown }
                        '3'.code -> { drainToTilde(); KeyEvent.Escape }  // Delete key
                        else     -> KeyEvent.Escape
                    }
                } else KeyEvent.Escape
            } else KeyEvent.Escape
        }
        127, 8       -> KeyEvent.Backspace
        9            -> KeyEvent.Tab
        3            -> KeyEvent.Escape   // Ctrl-C
        else         -> if (b in 32..126) KeyEvent.Character(b.toChar()) else KeyEvent.Unknown
    }
}

/** Consume the trailing '~' (or any) byte of an ESC [ n~ sequence if present. */
private fun drainToTilde() {
    try {
        if (System.`in`.available() > 0 && System.`in`.read() == '~'.code) {
            if (System.`in`.available() > 0) System.`in`.read()  // flush modifiers if any
        }
    } catch (_: Exception) { }
}

actual fun enableRawMode() {
    // Switch to raw-mode on JVM: suppress echo via stty if available
    try {
        Runtime.getRuntime().exec(arrayOf("/bin/sh", "-c", "stty raw -echo </dev/tty"))
            .waitFor()
    } catch (_: Exception) {
        // On Windows JVM, stty is not available — fall back to no-op
    }
}

actual fun disableRawMode() {
    try {
        Runtime.getRuntime().exec(arrayOf("/bin/sh", "-c", "stty sane </dev/tty"))
            .waitFor()
    } catch (_: Exception) { }
}

actual fun getTerminalSize(): Pair<Int, Int> {
    val w = System.getenv("COLUMNS")?.toIntOrNull() ?: 120
    val h = System.getenv("LINES")?.toIntOrNull()   ?: 30
    return Pair(w.coerceAtLeast(80), h.coerceAtLeast(20))
}
