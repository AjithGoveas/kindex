package dev.ajithgoveas.kindex.parser

import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType

class SymbolResolver {
    /**
     * Resolves raw import strings against known symbols to build explicit dependency edges.
     */
    fun resolveImports(
        filePath: String,
        rawImports: List<String>,
        symbolIndex: Map<String, Symbol>
    ): List<Edge> {
        val resolvedEdges = mutableListOf<Edge>()

        for (importFqn in rawImports) {
            val matchedSymbol = symbolIndex[importFqn]
            if (matchedSymbol != null) {
                resolvedEdges.add(
                    Edge(
                        sourceId = filePath,
                        targetId = matchedSymbol.id,
                        relation = RelationType.IMPORTS
                    )
                )
            } else if (importFqn.endsWith(".*")) {
                val pkgPrefix = importFqn.removeSuffix(".*")
                val pkgMatches = symbolIndex.values.filter { it.packageName == pkgPrefix }
                
                for (sym in pkgMatches) {
                    resolvedEdges.add(
                        Edge(
                            sourceId = filePath,
                            targetId = sym.id,
                            relation = RelationType.IMPORTS
                        )
                    )
                }
            }
        }

        return resolvedEdges
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
