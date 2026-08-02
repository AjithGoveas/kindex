package dev.ajithgoveas.kindex.core.io

/**
 * Platform-agnostic terminal key event.
 */
sealed class KeyEvent {
    object Up       : KeyEvent()
    object Down     : KeyEvent()
    object Left     : KeyEvent()
    object Right    : KeyEvent()
    object Enter    : KeyEvent()
    object Escape   : KeyEvent()
    object Backspace: KeyEvent()
    object Tab      : KeyEvent()
    data class Character(val c: Char) : KeyEvent()
    object Unknown  : KeyEvent()
}

/**
 * Read a single keystroke from stdin without requiring Enter.
 * Parses ANSI escape sequences for arrow keys.
 */
expect fun readKey(): KeyEvent

/**
 * Enable ANSI/VT100 mode on the current terminal (no-op on platforms that
 * already support it; required on Windows native to activate virtual terminal).
 */
expect fun enableRawMode()

/** Restore terminal to its original state (flush VT flags set in enableRawMode). */
expect fun disableRawMode()
