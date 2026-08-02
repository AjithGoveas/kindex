package dev.ajithgoveas.kindex.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.core.model.ParseResult
import dev.ajithgoveas.kindex.parser.extractors.*
import dev.ajithgoveas.kindex.core.io.HashUtils
import dev.ajithgoveas.kindex.core.io.MPFile
import dev.ajithgoveas.kindex.storage.IndexStorage
import dev.ajithgoveas.kindex.parser.SymbolResolver
import com.github.ajalt.clikt.parameters.options.flag
import dev.ajithgoveas.kindex.cli.commands.DepsCommand
import dev.ajithgoveas.kindex.cli.commands.StatsCommand
import dev.ajithgoveas.kindex.cli.commands.ExportCommand
import dev.ajithgoveas.kindex.cli.commands.DeadCommand
import dev.ajithgoveas.kindex.cli.commands.HookCommand

expect fun getInteractiveCommand(): CliktCommand?

class KIndex : CliktCommand(name = "kindex", help = "Code Knowledge Indexer") {
    override fun run() = Unit
}

fun walkFiles(dir: MPFile): List<MPFile> {
    val results = mutableListOf<MPFile>()
    val files = dir.listFiles() ?: return emptyList()
    for (file in files) {
        val path = file.path.replace('\\', '/')
        if (path.contains("/build/") || path.contains("/.gradle/") || path.contains("/.kindex/")) {
            continue
        }
        if (file.isDirectory) {
            results.addAll(walkFiles(file))
        } else {
            results.add(file)
        }
    }
    return results
}

class ScanCommand : CliktCommand(name = "scan", help = "Scan a repository directory and index symbols") {
    private val directory: String by argument(help = "Project directory to scan")
    private val quiet: Boolean by option("-q", "--quiet", help = "Suppress output during scanning").flag(default = false)

    override fun run() {
        val t = Terminal()
        val currentWorkspaceDir = MPFile(".")
        val rootDir = dev.ajithgoveas.kindex.core.io.RepositoryRootResolver.findRepositoryRoot(currentWorkspaceDir)
        val dirFile = if (directory == "." || MPFile(directory).absolutePath == currentWorkspaceDir.absolutePath) {
            rootDir
        } else {
            MPFile(directory)
        }
        try {
            dev.ajithgoveas.kindex.core.io.RepositoryGuardrail.assertWithinRepository(dirFile, rootDir)
        } catch (e: dev.ajithgoveas.kindex.core.io.RepositoryBoundaryException) {
            t.println(red(e.message ?: "Security Boundary Error"))
            return
        }
        val dbFile = MPFile("${rootDir.path}/.kindex/index.db")
        if (!quiet) t.println(cyan("Scanning repository at: ") + bold(dirFile.absolutePath))

        val extractors = listOf(
            KotlinJavaExtractor(),
            RustExtractor(),
            CExtractor(),
            CppExtractor(),
            CSharpExtractor(),
            JavaScriptExtractor(),
            GoExtractor(),
            CssExtractor()
        )
        
        val walkedFiles = walkFiles(dirFile)
            .filter { file -> extractors.any { it.supports(file) } }

        if (!quiet) t.println("Found ${walkedFiles.size} candidate source files. Checking for modifications...")

        val storage = IndexStorage(dbFile)

        // Load existing index file metadata
        val existingFiles = storage.getFilesMetadata()

        val filesToScan = mutableListOf<MPFile>()
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

        if (!quiet) t.println("Skipping ${unchangedFilesCount.size} unchanged files. Processing ${filesToScan.size} modified/new files. Pruning ${deletedPaths.size} deleted files...")

        if (filesToScan.isEmpty() && deletedPaths.isEmpty()) {
            if (!quiet) t.println(green("\n✓ Repository is already up-to-date. No changes detected.\n"))
            return
        }

        // Progress indicators while processing files
        val parseResults = mutableListOf<ParseResult>()
        if (filesToScan.isNotEmpty()) {
            if (!quiet) t.println("Parsing ASTs and extracting symbols...")
            filesToScan.forEachIndexed { index, file ->
                val displayIndex = index + 1
                if (!quiet) t.print("\r${cyan("[")}$displayIndex/${filesToScan.size}${cyan("]")} Processing: ${file.name.take(30).padEnd(30)}")
                val ext = extractors.first { it.supports(file) }
                parseResults.add(ext.extract(file))
            }
            if (!quiet) t.print("\r✓ AST parsing complete.                                                        \n")
        }

        // Load all symbols to resolve imports across unchanged + modified files
        val dbSymbols = storage.getAllSymbols()
        val modifiedPathsSet = filesToScan.map { it.path }.toSet()
        val unmodifiedSymbols = dbSymbols.filter { it.filePath !in modifiedPathsSet && it.filePath !in deletedPaths }

        val newSymbols = parseResults.flatMap { it.symbols }
        val allSymbolsMap = (unmodifiedSymbols + newSymbols).associateBy { it.id }

        // Resolve imports and call references using SymbolResolver
        if (!quiet) t.println("Resolving cross-symbol reference edges...")
        val resolver = SymbolResolver()
        val fullyResolvedResults = parseResults.map { result ->
            val rawImportStrings = result.edges
                .filter { it.relation == dev.ajithgoveas.kindex.core.model.RelationType.IMPORTS }
                .map { it.targetId }

            // 1. Resolve imports
            val resolvedImportEdges = resolver.resolveImports(result.sourceFile.path, rawImportStrings, allSymbolsMap)

            // 2. Filter containment/extends edges
            val staticEdges = result.edges.filter { 
                it.relation == dev.ajithgoveas.kindex.core.model.RelationType.CONTAINS || 
                it.relation == dev.ajithgoveas.kindex.core.model.RelationType.EXTENDS 
            }

            // 3. Resolve CALLS references
            val unresolvedCalls = result.edges
                .filter { it.relation == dev.ajithgoveas.kindex.core.model.RelationType.CALLS && it.targetId.startsWith("REF:") }

            val resolvedCallEdges = unresolvedCalls.flatMap { callEdge ->
                resolver.resolveCalls(
                    sourceId = callEdge.sourceId, // e.g. "package#functionName"
                    unresolvedCalls = listOf(callEdge.targetId),
                    imports = rawImportStrings,
                    currentPackage = result.sourceFile.packageName,
                    symbolIndex = allSymbolsMap
                )
            }

            result.copy(edges = staticEdges + resolvedImportEdges + resolvedCallEdges)
        }

        // Save incrementally
        storage.saveResultsIncremental(fullyResolvedResults, deletedPaths)

        if (!quiet) t.println(green("\n✓ Successfully updated index in ${dbFile.path}\n"))

        // Render summary table for modified files
        if (!quiet && fullyResolvedResults.isNotEmpty()) {
            t.println(bold("Processed Files Summary:"))
            t.println(
                table {
                    header { row("File Path", "Language", "Package", "Symbols", "Edges") }
                    body {
                        fullyResolvedResults.take(10).forEach { res ->
                            row(
                                res.sourceFile.path.removePrefix(dirFile.absolutePath),
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

        if (!quiet) {
            val finalStats = storage.getRepositoryStats()
            t.println("\n" + bold("Total Files in Index:") + " ${finalStats.fileCount}")
            t.println(bold("Total Symbols in Index:") + " ${finalStats.symbolCount}")
            t.println(bold("Total Relationships in Index:") + " ${finalStats.edgeCount}")
        }
    }
}

class QueryCommand : CliktCommand(name = "query", help = "Search indexed symbols in the project") {
    private val term by argument(help = "Symbol name or substring to search for")
    private val dirArg by argument(name = "dir", help = "Project directory").optional()
    private val dirOpt by option("-d", "--dir", help = "Project directory")

    override fun run() {
        val t = Terminal()
        val directory = dirOpt ?: dirArg ?: "."
        val currentWorkspaceDir = MPFile(".")
        val rootDir = dev.ajithgoveas.kindex.core.io.RepositoryRootResolver.findRepositoryRoot(currentWorkspaceDir)
        val targetDir = if (directory == "." || MPFile(directory).absolutePath == currentWorkspaceDir.absolutePath) {
            rootDir
        } else {
            MPFile(directory)
        }
        try {
            dev.ajithgoveas.kindex.core.io.RepositoryGuardrail.assertWithinRepository(targetDir, rootDir)
        } catch (e: dev.ajithgoveas.kindex.core.io.RepositoryBoundaryException) {
            t.println(red(e.message ?: "Security Boundary Error"))
            return
        }
        val dbFile = MPFile("${rootDir.path}/.kindex/index.db")
        if (!dbFile.exists) {
            t.println(red("No index found. Run 'kindex scan $directory' first."))
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
                            sym.filePath.removePrefix(MPFile(directory).absolutePath),
                            sym.lineNumber.toString()
                        )
                    }
                }
            }
        )
    }
}

fun main(args: Array<String>) {
    val subcommandsList = listOf("scan", "query", "deps", "stats", "export", "dead", "hook", "interactive")
    val mappedArgs = if (args.isNotEmpty()) {
        val firstArgLower = args[0].lowercase()
        if (firstArgLower in subcommandsList) {
            val newArgs = args.copyOf()
            newArgs[0] = firstArgLower
            newArgs
        } else {
            args
        }
    } else {
        args
    }

    val commands = mutableListOf<CliktCommand>(
        ScanCommand(),
        QueryCommand(),
        DepsCommand(),
        StatsCommand(),
        ExportCommand(),
        DeadCommand(),
        HookCommand(),
        dev.ajithgoveas.kindex.cli.commands.FlowCommand()
    )
    getInteractiveCommand()?.let { commands.add(it) }

    KIndex().subcommands(commands).main(mappedArgs)
}