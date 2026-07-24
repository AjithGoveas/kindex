package dev.ajithgoveas.kindex.core.graph

import dev.ajithgoveas.kindex.core.model.Edge
import dev.ajithgoveas.kindex.core.model.Symbol
import dev.ajithgoveas.kindex.core.model.RelationType

class KnowledgeGraph {
    private val nodes = mutableMapOf<String, Symbol>()
    private val outgoingEdges = mutableMapOf<String, MutableList<Edge>>()
    private val incomingEdges = mutableMapOf<String, MutableList<Edge>>()

    fun addSymbol(symbol: Symbol) {
        nodes[symbol.id] = symbol
    }

    fun addEdge(edge: Edge) {
        outgoingEdges.getOrPut(edge.sourceId) { mutableListOf() }.add(edge)
        incomingEdges.getOrPut(edge.targetId) { mutableListOf() }.add(edge)
    }

    fun getSymbol(id: String): Symbol? = nodes[id]

    fun findSymbolsByName(query: String): List<Symbol> {
        return nodes.values.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun getImportsForFile(filePath: String): List<Edge> {
        return outgoingEdges[filePath]?.filter { it.relation == RelationType.IMPORTS } ?: emptyList()
    }

    fun getDependents(symbolId: String): List<Edge> {
        return incomingEdges[symbolId] ?: emptyList()
    }
}
