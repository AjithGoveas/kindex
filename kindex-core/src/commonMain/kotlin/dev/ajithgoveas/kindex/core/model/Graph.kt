package dev.ajithgoveas.kindex.core.model

enum class RelationType {
    CONTAINS,   // File contains Class, Class contains Method
    IMPORTS,    // File imports Class/Package
    EXTENDS     // Class implements/extends Interface/Class
}

data class Edge(
    val sourceId: String,
    val targetId: String,
    val relation: RelationType
)
