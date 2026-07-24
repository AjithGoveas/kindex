package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.storage.IndexStorage
import java.io.File

class StatsCommand : CliktCommand(name = "stats", help = "Display repository structural overview and stats") {
    private val directory by option("-d", "--dir", help = "Project directory").file().default(File("."))

    override fun run() {
        val t = Terminal()
        val dbFile = File(directory, ".kindex/index.db")
        if (!dbFile.exists()) {
            t.println(red("No index found. Run 'kindex scan ${directory.path}' first."))
            return
        }

        val storage = IndexStorage(dbFile)
        val stats = storage.getRepositoryStats()

        t.println(bold(cyan("\n=== Repository Knowledge Summary ===\n")))
        t.println(table {
            header { row("Metric", "Count") }
            body {
                row("Total Source Files", stats.fileCount.toString())
                row("Total Extracted Symbols", stats.symbolCount.toString())
                row("Total Packages", stats.packageCount.toString())
                row("Classes & Interfaces", stats.classCount.toString())
                row("Functions & Methods", stats.functionCount.toString())
                row("Dependency Relationships", stats.edgeCount.toString())
            }
        })
    }
}
