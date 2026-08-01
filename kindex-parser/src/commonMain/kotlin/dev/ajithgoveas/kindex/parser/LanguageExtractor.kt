package dev.ajithgoveas.kindex.parser

import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.core.io.MPFile

interface LanguageExtractor {
    fun supports(file: MPFile): Boolean
    fun extract(file: MPFile): ParseResult
}
