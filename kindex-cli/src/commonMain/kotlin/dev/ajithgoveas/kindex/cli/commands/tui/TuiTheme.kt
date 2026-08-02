package dev.ajithgoveas.kindex.cli.commands.tui

/**
 * Terminal primitives: ANSI escape helpers, the shared draw buffer, and
 * ANSI-aware text measurement/truncation used by the TUI renderer.
 */
object Term {
    const val ESC     = "\u001B"
    const val ESC_CHAR = '\u001B'
    const val RESET   = "$ESC[0m"
    const val BOLD    = "$ESC[1m"
    const val DIM     = "$ESC[2m"
    const val CLEAR   = "$ESC[2J$ESC[H"
    const val ALT_ON  = "$ESC[?1049h"
    const val ALT_OFF = "$ESC[?1049l"
    const val HIDE    = "$ESC[?25l"
    const val SHOW    = "$ESC[?25h"
    const val CLR_EOL = "$ESC[0K"

    fun fg(r: Int, g: Int, b: Int) = "$ESC[38;2;$r;$g;${b}m"
    fun bg(r: Int, g: Int, b: Int) = "$ESC[48;2;$r;$g;${b}m"
    fun at(row: Int, col: Int)     = "$ESC[${row};${col}H"

    // Colour palette
    val ACCENT  = fg(139,  92, 246)  // purple
    val ACCENT2 = fg(167, 139, 250)  // lavender
    val SUCCESS = fg( 52, 211, 153)  // emerald
    val WARN    = fg(251, 191,  36)  // amber
    val MUTED   = fg(107, 114, 128)  // grey-500
    val BRIGHT  = fg(243, 244, 246)  // grey-100
    val RED     = fg(248, 113, 113)  // red-400
    val CYAN    = fg( 34, 211, 238)  // cyan
    val GREEN   = fg( 52, 211, 153)

    val BG_TITLE = bg( 17,  24,  39)  // grey-900
    val BG_SEL   = bg( 55,  48, 163)  // indigo-800
    val BG_SEL_HOT = bg(76, 29, 149)  // purple-800 (dialogs / hot states)
    val BG_ROW   = bg( 30,  41,  59)  // slate-800

    val buf = StringBuilder(16384)
    fun w(s: String) { buf.append(s) }
    fun flush() { print(buf); buf.clear() }

    /** Count the visible columns of an ANSI-coloured string (escape codes are ignored). */
    fun visibleLength(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == ESC_CHAR) {
                i++
                if (i < s.length && s[i] == '[') {
                    i++
                    while (i < s.length && !(s[i] in 'A'..'Z' || s[i] in 'a'..'z')) i++
                    i++
                } else {
                    i++
                }
            } else {
                n++
                i++
            }
        }
        return n
    }

    /** Truncate an ANSI-coloured string to at most [max] visible columns, preserving colour codes. */
    fun fit(s: String, max: Int): String {
        if (max <= 0) return ""
        if (visibleLength(s) <= max) return s
        val sb = StringBuilder()
        var vis = 0
        var i = 0
        while (i < s.length && vis < max) {
            val c = s[i]
            if (c == ESC_CHAR) {
                val start = i
                i++
                if (i < s.length && s[i] == '[') {
                    i++
                    while (i < s.length && !(s[i] in 'A'..'Z' || s[i] in 'a'..'z')) i++
                    i++
                } else {
                    i++
                }
                sb.append(s.substring(start, i))
            } else {
                sb.append(c)
                vis++
                i++
            }
        }
        return sb.append(RESET).toString()
    }

    /** Right-pad an ANSI-coloured string to exactly [w] visible columns. */
    fun pad(s: String, w: Int): String = s + " ".repeat((w - visibleLength(s)).coerceAtLeast(0))

    /** Pad [s] to exactly [w] visible columns applying [fill] as a full-width background
     *  colour. When [fill] is empty this behaves like [pad] + RESET. The fill colour is
     *  carried across the trailing padding (no unhighlighted gaps). */
    fun sel(s: String, w: Int, fill: String): String {
        if (fill.isEmpty()) return pad(fit(s, w), w) + RESET
        var txt = fit(s, w)
        if (txt.endsWith(RESET)) txt = txt.dropLast(RESET.length)
        return fill + txt + " ".repeat((w - visibleLength(txt)).coerceAtLeast(0)) + RESET
    }

    /** Linear two-colour gradient across the characters of [s]. */
    fun gradient(s: String, c1: Triple<Int, Int, Int>, c2: Triple<Int, Int, Int>): String {
        val n = s.length
        if (n == 0) return s
        return s.mapIndexed { i, ch ->
            val t = if (n == 1) 0.0 else i.toDouble() / (n - 1)
            val r = (c1.first + (c2.first - c1.first) * t).toInt()
            val g = (c1.second + (c2.second - c1.second) * t).toInt()
            val b = (c1.third + (c2.third - c1.third) * t).toInt()
            fg(r, g, b) + ch
        }.joinToString("") + RESET
    }
}

/** A fully-rendered content pane shown in the right-hand side of the TUI. */
data class ContentModel(
    val title: String,
    val rows: List<String>,
    val selectable: List<Int> = emptyList(),
    val onActivate: ((Int) -> ContentModel)? = null,
    val filters: ((Char) -> ContentModel)? = null,
    val status: String = ""
) {
    companion object {
        fun text(title: String, rows: List<String>, status: String = "") =
            ContentModel(title, rows, emptyList(), null, null, status)
    }
}
