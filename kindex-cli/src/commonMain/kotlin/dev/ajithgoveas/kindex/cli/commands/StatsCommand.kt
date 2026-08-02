package dev.ajithgoveas.kindex.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import dev.ajithgoveas.kindex.storage.IndexStorage
import dev.ajithgoveas.kindex.core.io.MPFile

class StatsCommand : CliktCommand(name = "stats", help = "Display repository structural overview and stats") {
    private val directory by option("-d", "--dir", help = "Project directory").default(".")

    override fun run() {
        val t = Terminal()
        val rootDir = dev.ajithgoveas.kindex.core.io.RepositoryRootResolver.findRepositoryRoot(MPFile(directory))
        val dbFile = MPFile("${rootDir.path}/.kindex/index.db")
        if (!dbFile.exists) {
            t.println(red("No index found. Run 'kindex scan $directory' first."))
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
