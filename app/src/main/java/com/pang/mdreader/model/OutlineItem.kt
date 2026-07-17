package com.pang.mdreader.model

/**
 * Represents a heading in the document outline.
 */
data class OutlineItem(
    val level: Int,
    val title: String,
    val id: String,
    val startLine: Int = 0,
)
