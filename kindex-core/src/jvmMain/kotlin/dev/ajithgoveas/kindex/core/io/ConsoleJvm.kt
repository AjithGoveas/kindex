package dev.ajithgoveas.kindex.core.io

actual fun readKey(): KeyEvent {
    val b = System.`in`.read()
    if (b < 0) return KeyEvent.Unknown
    return when (b) {
        13, 10       -> KeyEvent.Enter
        27           -> {
            // Possible ANSI escape sequence — peek ahead
            if (System.`in`.available() > 0) {
                val b2 = System.`in`.read()
                if (b2 == '['.code && System.`in`.available() > 0) {
                    when (System.`in`.read()) {
                        'A'.code -> KeyEvent.Up
                        'B'.code -> KeyEvent.Down
                        'C'.code -> KeyEvent.Right
                        'D'.code -> KeyEvent.Left
                        else     -> KeyEvent.Escape
                    }
                } else KeyEvent.Escape
            } else KeyEvent.Escape
        }
        127, 8       -> KeyEvent.Backspace
        9            -> KeyEvent.Tab
        3, 113       -> KeyEvent.Escape   // Ctrl-C or 'q'
        else         -> if (b in 32..126) KeyEvent.Character(b.toChar()) else KeyEvent.Unknown
    }
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
