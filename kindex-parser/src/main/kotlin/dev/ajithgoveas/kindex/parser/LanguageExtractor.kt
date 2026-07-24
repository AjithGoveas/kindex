package dev.ajithgoveas.kindex.parser

import dev.ajithgoveas.kindex.core.model.ParseResult
import java.io.File

interface LanguageExtractor {
    fun supports(file: File): Boolean
    fun extract(file: File): ParseResult
}
