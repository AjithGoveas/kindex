package dev.ajithgoveas.kindex.parser

import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.RelationType
import dev.ajithgoveas.kindex.core.model.Symbol

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
            // Direct FQN match (e.g., dev.ajithgoveas.kindex.core.model.Symbol)
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
                // Wildcard import handling (e.g. import dev.ajithgoveas.kindex.core.model.*)
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
}
