package com.llmapp.rag.domain

data class Document(
    val id: String,
    val title: String,
    val content: String,
    val source: String,
    val sections: List<Section>,
)

data class Section(
    val heading: String,
    val content: String,
)
