package dev.ajithgoveas.kindex.core.analysis

import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.SymbolType

enum class ArchitecturalLayer(val displayName: String, val emoji: String) {
    ENTRY_POINTS("Entry Points & Drivers", "🚀"),
    SERVICES("Service & Parser Engine", "⚙️"),
    STORAGE("Storage & Infrastructure", "💾"),
    UTILITIES("Solo & Standalone Utilities", "🛠️")
}

data class AggregatedEdge(
    val source: String,
    val target: String,
    val relation: String,
    val weight: Int
)

data class ComponentNode(
    val id: String,
    val name: String,
    val layer: ArchitecturalLayer,
    val symbolType: String,
    val packageName: String
)

object ArchitectureFlowAnalyzer {

    fun aggregateByFile(edges: List<Edge>): List<AggregatedEdge> {
        val countMap = mutableMapOf<Pair<String, Pair<String, String>>, Int>()

        for (edge in edges) {
            val srcFile = extractFileName(edge.sourceId)
            val tgtFile = extractFileName(edge.targetId)
            if (srcFile != tgtFile) {
                val key = Pair(srcFile, Pair(tgtFile, edge.relation.name))
                countMap[key] = (countMap[key] ?: 0) + 1
            }
        }

        return countMap.map { (key, count) ->
            AggregatedEdge(
                source = key.first,
                target = key.second.first,
                relation = key.second.second,
                weight = count
            )
        }.sortedByDescending { it.weight }
    }

    fun aggregateByPackage(edges: List<Edge>, symbols: List<Symbol>): List<AggregatedEdge> {
        val symbolPkgMap = symbols.associate { it.id to (it.packageName ?: "default") }
        val countMap = mutableMapOf<Pair<String, Pair<String, String>>, Int>()

        for (edge in edges) {
            val srcPkg = symbolPkgMap[edge.sourceId] ?: extractPackageFromId(edge.sourceId)
            val tgtPkg = symbolPkgMap[edge.targetId] ?: extractPackageFromId(edge.targetId)

            if (srcPkg != tgtPkg && srcPkg.isNotBlank() && tgtPkg.isNotBlank()) {
                val key = Pair(srcPkg, Pair(tgtPkg, edge.relation.name))
                countMap[key] = (countMap[key] ?: 0) + 1
            }
        }

        return countMap.map { (key, count) ->
            AggregatedEdge(
                source = key.first,
                target = key.second.first,
                relation = key.second.second,
                weight = count
            )
        }.sortedByDescending { it.weight }
    }

    fun classifyNodes(symbols: List<Symbol>, edges: List<Edge>): List<ComponentNode> {
        val entryPoints = EntryPointResolver.findEntryPoints(symbols).map { it.symbolId }.toSet()
        val inDegree = mutableMapOf<String, Int>()
        val outDegree = mutableMapOf<String, Int>()

        for (edge in edges) {
            outDegree[edge.sourceId] = (outDegree[edge.sourceId] ?: 0) + 1
            inDegree[edge.targetId] = (inDegree[edge.targetId] ?: 0) + 1
        }

        return symbols.map { sym ->
            val layer = when {
                sym.id in entryPoints -> ArchitecturalLayer.ENTRY_POINTS
                sym.filePath.lowercase().contains("storage") || sym.filePath.lowercase().contains("db") -> ArchitecturalLayer.STORAGE
                sym.filePath.lowercase().contains("extractor") || sym.filePath.lowercase().contains("resolver") || sym.filePath.lowercase().contains("parser") -> ArchitecturalLayer.SERVICES
                (inDegree[sym.id] ?: 0) == 0 && (outDegree[sym.id] ?: 0) == 0 -> ArchitecturalLayer.UTILITIES
                else -> ArchitecturalLayer.SERVICES
            }

            ComponentNode(
                id = sym.id,
                name = sym.name,
                layer = layer,
                symbolType = sym.type.name,
                packageName = sym.packageName ?: ""
            )
        }
    }

    fun computeFocusSubgraph(focusTarget: String, edges: List<Edge>, maxHops: Int = 2): List<Edge> {
        val focusLower = focusTarget.lowercase()
        val visitedNodes = mutableSetOf<String>()

        // Find initial matching nodes
        val matchingNodes = edges.flatMap { listOf(it.sourceId, it.targetId) }
            .filter { it.lowercase().contains(focusLower) }
            .toSet()

        if (matchingNodes.isEmpty()) return emptyList()

        var currentFrontier = matchingNodes
        visitedNodes.addAll(matchingNodes)

        for (hop in 0 until maxHops) {
            val nextFrontier = mutableSetOf<String>()
            for (edge in edges) {
                if (edge.sourceId in currentFrontier && edge.targetId !in visitedNodes) {
                    nextFrontier.add(edge.targetId)
                }
                if (edge.targetId in currentFrontier && edge.sourceId !in visitedNodes) {
                    nextFrontier.add(edge.sourceId)
                }
            }
            visitedNodes.addAll(nextFrontier)
            currentFrontier = nextFrontier
        }

        return edges.filter { it.sourceId in visitedNodes && it.targetId in visitedNodes }
    }

    private fun extractFileName(id: String): String {
        val pathPart = id.substringBefore("#")
        return pathPart.substringAfterLast("/").substringAfterLast("\\")
    }

    private fun extractPackageFromId(id: String): String {
        val symPart = id.substringBefore("#")
        return if (symPart.contains(".")) symPart.substringBeforeLast(".") else "default"
    }
}
