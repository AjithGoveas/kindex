package dev.ajithgoveas.kindex.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File

class KIndex : CliktCommand(name = "kindex", help = "Universal Code Knowledge Indexer") {
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
        t.println("[green]Scanning repository at:[/] ${directory.absolutePath}")
    }
}

fun main(args: Array<String>) {
    KIndex().subcommands(ScanCommand()).main(args)
}