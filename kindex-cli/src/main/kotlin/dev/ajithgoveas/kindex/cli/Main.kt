package dev.ajithgoveas.kindex.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.parser.extractors.KotlinJavaExtractor
import dev.ajithgoveas.kindex.storage.IndexStorage
import java.io.File

class KIndex : CliktCommand(name = "kindex", help = "Code Knowledge Indexer") {
    override fun run() = Unit
}

class ScanCommand : CliktCommand(name = "scan", help = "Scan a repository directory and index symbols") {
    private val directory: File by argument(help = "Project directory to scan").file(
        mustExist = true,
        canBeFile = false,
        canBeDir = true
    )

    override fun run() {
        val t = Terminal()
        t.println(cyan("Scanning repository at: ") + bold(directory.absolutePath))

        val extractor = KotlinJavaExtractor()
        val filesToScan = directory.walkTopDown()
            .filter { it.isFile && extractor.supports(it) }
            .filter { !it.path.contains("/build/") && !it.path.contains("/.gradle/") }
            .toList()

        t.println("Found ${filesToScan.size} source files. Extracting symbols...")

        val results = filesToScan.map { extractor.extract(it) }
        val totalSymbols = results.sumOf { it.symbols.size }

        // Save to local SQLite database inside target directory
        val dbFile = File(directory, ".kindex/index.db")
        val storage = IndexStorage(dbFile)
        storage.saveResults(results)

        t.println(green("\n✓ Successfully indexed project into ${dbFile.path}\n"))

        // Render summary table
        t.println(
            table {
                header { row("File Path", "Language", "Package", "Extracted Symbols") }
                body {
                    results.take(10).forEach { res ->
                        row(
                            res.sourceFile.path.removePrefix(directory.absolutePath),
                            res.sourceFile.language,
                            res.sourceFile.packageName ?: "default",
                            res.symbols.size.toString()
                        )
                    }
                }
            }
        )

        t.println("\n" + bold("Total Files Scanned:") + " ${filesToScan.size}")
        t.println(bold("Total Symbols Indexed:") + " $totalSymbols")
    }
}

fun main(args: Array<String>) {
    KIndex().subcommands(ScanCommand()).main(args)
}