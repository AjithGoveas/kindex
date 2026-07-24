package dev.ajithgoveas.kindex.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.parser.extractors.KotlinJavaExtractor
import dev.ajithgoveas.kindex.parser.extractors.RustExtractor
import dev.ajithgoveas.kindex.parser.HashUtils
import dev.ajithgoveas.kindex.storage.IndexStorage
import dev.ajithgoveas.kindex.parser.SymbolResolver
import dev.ajithgoveas.kindex.cli.commands.DepsCommand
import dev.ajithgoveas.kindex.cli.commands.StatsCommand
import dev.ajithgoveas.kindex.cli.commands.ExportCommand
import dev.ajithgoveas.kindex.cli.commands.DeadCommand
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

        val extractors = listOf(KotlinJavaExtractor(), RustExtractor())
        val walkedFiles = directory.walkTopDown()
            .filter { file -> file.isFile && extractors.any { it.supports(file) } }
            .filter { !it.path.contains("/build/") && !it.path.contains("/.gradle/") && !it.path.contains("/.kindex/") }
            .toList()

        t.println("Found ${walkedFiles.size} candidate source files. Checking for modifications...")

        val dbFile = File(directory, ".kindex/index.db")
        val storage = IndexStorage(dbFile)

        // Load existing index file metadata
        val existingFiles = storage.getFilesMetadata()

        val filesToScan = mutableListOf<File>()
        val unchangedFilesCount = mutableListOf<String>()

        for (file in walkedFiles) {
            val dbMeta = existingFiles[file.path]
            if (dbMeta != null) {
                val (dbTime, dbHash) = dbMeta
                val currentHash = HashUtils.sha256(file)
                if (file.lastModified() == dbTime && currentHash == dbHash) {
                    unchangedFilesCount.add(file.path)
                    continue
                }
            }
            filesToScan.add(file)
        }

        // Identify deleted files
        val walkedPathsSet = walkedFiles.map { it.path }.toSet()
        val deletedPaths = existingFiles.keys.filter { it !in walkedPathsSet }

        t.println("Skipping ${unchangedFilesCount.size} unchanged files. Processing ${filesToScan.size} modified/new files. Pruning ${deletedPaths.size} deleted files...")

        if (filesToScan.isEmpty() && deletedPaths.isEmpty()) {
            t.println(green("\n✓ Repository is already up-to-date. No changes detected.\n"))
            return
        }

        // Progress indicators while processing files
        val parseResults = mutableListOf<ParseResult>()
        if (filesToScan.isNotEmpty()) {
            t.println("Parsing ASTs and extracting symbols...")
            filesToScan.forEachIndexed { index, file ->
                val displayIndex = index + 1
                t.print("\r${cyan("[")}$displayIndex/${filesToScan.size}${cyan("]")} Processing: ${file.name.take(30).padEnd(30)}")
                val ext = extractors.first { it.supports(file) }
                parseResults.add(ext.extract(file))
            }
            t.print("\r✓ AST parsing complete.                                                        \n")
        }

        // Load all symbols to resolve imports across unchanged + modified files
        val dbSymbols = storage.getAllSymbols()
        val modifiedPathsSet = filesToScan.map { it.path }.toSet()
        val unmodifiedSymbols = dbSymbols.filter { it.filePath !in modifiedPathsSet && it.filePath !in deletedPaths }

        val newSymbols = parseResults.flatMap { it.symbols }
        val allSymbolsMap = (unmodifiedSymbols + newSymbols).associateBy { it.id }

        // Resolve imports using SymbolResolver
        t.println("Resolving cross-symbol reference edges...")
        val resolver = SymbolResolver()
        val fullyResolvedResults = parseResults.map { result ->
            val rawImportStrings = result.edges
                .filter { it.relation == dev.ajithgoveas.kindex.core.model.RelationType.IMPORTS }
                .map { it.targetId }

            val resolvedImportEdges = resolver.resolveImports(result.sourceFile.path, rawImportStrings, allSymbolsMap)
            val containmentEdges = result.edges.filter { it.relation != dev.ajithgoveas.kindex.core.model.RelationType.IMPORTS }

            result.copy(edges = containmentEdges + resolvedImportEdges)
        }

        // Save incrementally
        storage.saveResultsIncremental(fullyResolvedResults, deletedPaths)

        t.println(green("\n✓ Successfully updated index in ${dbFile.path}\n"))

        // Render summary table for modified files
        if (fullyResolvedResults.isNotEmpty()) {
            t.println(bold("Processed Files Summary:"))
            t.println(
                table {
                    header { row("File Path", "Language", "Package", "Symbols", "Edges") }
                    body {
                        fullyResolvedResults.take(10).forEach { res ->
                            row(
                                res.sourceFile.path.removePrefix(directory.absolutePath),
                                res.sourceFile.language,
                                res.sourceFile.packageName ?: "default",
                                res.symbols.size.toString(),
                                res.edges.size.toString()
                            )
                        }
                    }
                }
            )
        }

        val finalStats = storage.getRepositoryStats()
        t.println("\n" + bold("Total Files in Index:") + " ${finalStats.fileCount}")
        t.println(bold("Total Symbols in Index:") + " ${finalStats.symbolCount}")
        t.println(bold("Total Relationships in Index:") + " ${finalStats.edgeCount}")
    }
}

class QueryCommand : CliktCommand(name = "query", help = "Search indexed symbols in the project") {
    private val term by argument(help = "Symbol name or substring to search for")
    private val directory by option("-d", "--dir", help = "Project directory").file().default(File("."))

    override fun run() {
        val t = Terminal()
        val dbFile = File(directory, ".kindex/index.db")
        if (!dbFile.exists()) {
            t.println(red("No index found. Run 'kindex scan ${directory.path}' first."))
            return
        }

        val storage = IndexStorage(dbFile)
        val matches = storage.searchSymbols(term)

        if (matches.isEmpty()) {
            t.println(yellow("No symbols found matching '$term'"))
            return
        }

        t.println(bold("\nSearch results for '$term':\n"))
        t.println(
            table {
                header { row("Kind", "Name", "Package", "File", "Line") }
                body {
                    matches.forEach { sym ->
                        row(
                            sym.type.name,
                            sym.name,
                            sym.packageName ?: "-",
                            sym.filePath.removePrefix(directory.absolutePath),
                            sym.lineNumber.toString()
                        )
                    }
                }
            }
        )
    }
}

fun main(args: Array<String>) {
    KIndex().subcommands(
        ScanCommand(),
        QueryCommand(),
        DepsCommand(),
        StatsCommand(),
        ExportCommand(),
        DeadCommand()
    ).main(args)
}