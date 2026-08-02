package dev.ajithgoveas.kindex.parser

import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType

data class ImportResolution(
    val resolved: List<Edge>,
    val unresolved: List<String>
)

class SymbolResolver {
    /**
     * Resolves raw import strings against known symbols to build explicit dependency edges.
     * Imports that cannot be resolved to any indexed symbol are reported as [ImportResolution.unresolved]
     * so they can be surfaced as external dependencies in the module graph.
     */
    fun resolveImports(
        filePath: String,
        rawImports: List<String>,
        symbolIndex: Map<String, Symbol>
    ): List<Edge> = resolveImportsDetailed(filePath, rawImports, symbolIndex).resolved

    fun resolveImportsDetailed(
        filePath: String,
        rawImports: List<String>,
        symbolIndex: Map<String, Symbol>
    ): ImportResolution {
        val resolvedEdges = mutableListOf<Edge>()
        val unresolvedImports = mutableListOf<String>()

        for (importFqn in rawImports) {
            var matched = false

            val addResolvedEdge = { sym: Symbol ->
                matched = true
                if (sym.filePath != filePath) {
                    resolvedEdges.add(
                        Edge(
                            sourceId = filePath,
                            targetId = sym.filePath,
                            relation = RelationType.IMPORTS
                        )
                    )
                }
            }

            // 1. Exact symbol id (class/enum/object fully-qualified name)
            val exact = symbolIndex[importFqn]
            if (exact != null) {
                addResolvedEdge(exact)
            } else if (importFqn.endsWith(".*")) {
                // 2. Wildcard package import: match every symbol declared in that package
                val pkgPrefix = importFqn.removeSuffix(".*")
                val pkgMatches = symbolIndex.values.filter { it.packageName == pkgPrefix }
                if (pkgMatches.isNotEmpty()) {
                    for (sym in pkgMatches) addResolvedEdge(sym)
                }
            } else {
                // 3. Top-level function import: "import pkg.name" is stored as symbol id "pkg#name"
                val lastDot = importFqn.lastIndexOf('.')
                val functionId = if (lastDot > 0) {
                    importFqn.substring(0, lastDot) + "#" + importFqn.substring(lastDot + 1)
                } else null
                val functionMatch = functionId?.let { symbolIndex[it] }

                // 4. Package + name match (handles multiple classes declared in one file)
                val packageMatch = if (functionMatch == null && lastDot > 0) {
                    val pkg = importFqn.substring(0, lastDot)
                    val name = importFqn.substring(lastDot + 1)
                    symbolIndex.values.find { it.packageName == pkg && it.name == name }
                } else null

                // 5. File-name fallback (e.g. import "MPFile" -> MPFile.kt)
                val fileMatch = if (functionMatch == null && packageMatch == null) {
                    val baseName = importFqn.substringAfterLast('.').substringAfterLast('/')
                    symbolIndex.values.find {
                        val fName = it.filePath.substringAfterLast('/').substringAfterLast('\\').substringBefore('.')
                        fName.equals(baseName, ignoreCase = true)
                    }
                } else null

                val sym = functionMatch ?: packageMatch ?: fileMatch
                if (sym != null) addResolvedEdge(sym)
            }

            if (!matched) unresolvedImports.add(importFqn)
        }

        return ImportResolution(resolvedEdges, unresolvedImports)
    }

    /**
     * Resolves local call references (e.g. targetId = "REF:ClassB.execute" or "REF:execute")
     * against package declarations, imports, and definitions to create fully qualified CALLS edges.
     */
    fun resolveCalls(
        sourceId: String,
        unresolvedCalls: List<String>,
        imports: List<String>,
        currentPackage: String?,
        symbolIndex: Map<String, Symbol>
    ): List<Edge> {
        val resolvedEdges = mutableListOf<Edge>()

        for (callRef in unresolvedCalls) {
            val refName = callRef.removePrefix("REF:")
            val parts = refName.split(".")
            val baseName = parts.first()

            var resolvedSymbol: Symbol? = null

            // 1. Check if the base class/method matches a direct import
            val importedFqn = imports.find { it.endsWith(".$baseName") || it == baseName }
            if (importedFqn != null) {
                // Resolved to imported class or package member
                val targetFqn = if (parts.size > 1) {
                    "$importedFqn#${parts.last()}"
                } else {
                    importedFqn
                }
                resolvedSymbol = symbolIndex[targetFqn] ?: symbolIndex[importedFqn]
            }

            // 2. Sibling resolution: check if it matches a symbol in the same package
            if (resolvedSymbol == null && currentPackage != null) {
                val packageFqn = if (parts.size > 1) {
                    "$currentPackage.$baseName#${parts.last()}"
                } else {
                    "$currentPackage.$baseName"
                }
                resolvedSymbol = symbolIndex[packageFqn] ?: symbolIndex["$currentPackage#$refName"]
            }

            // 3. Fallback: match a function/class name directly in the file (local scope)
            if (resolvedSymbol == null) {
                // Find a function in the same file
                val localMatches = symbolIndex.values.filter {
                    it.filePath == sourceId && it.name == refName
                }
                resolvedSymbol = localMatches.firstOrNull()
            }

            // 4. Wildcard imports resolution
            if (resolvedSymbol == null) {
                val wildcardImports = imports.filter { it.endsWith(".*") }.map { it.removeSuffix(".*") }
                for (wildcardPkg in wildcardImports) {
                    val targetFqn = if (parts.size > 1) {
                        "$wildcardPkg.$baseName#${parts.last()}"
                    } else {
                        "$wildcardPkg.$baseName"
                    }
                    val found = symbolIndex[targetFqn] ?: symbolIndex["$wildcardPkg#$refName"]
                    if (found != null) {
                        resolvedSymbol = found
                        break
                    }
                }
            }

            // 5. Direct global match fallback (for projects without package declarations or simple calls)
            if (resolvedSymbol == null) {
                resolvedSymbol = symbolIndex[refName] ?: symbolIndex.values.find { it.name == refName }
            }

            // If we resolved it to a known target, create the CALLS edge
            if (resolvedSymbol != null) {
                resolvedEdges.add(
                    Edge(
                        sourceId = sourceId,
                        targetId = resolvedSymbol.id,
                        relation = RelationType.CALLS
                    )
                )
            }
        }

        return resolvedEdges
    }
}
