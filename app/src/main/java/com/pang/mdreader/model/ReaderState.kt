package com.pang.mdreader.model

/**
 * Immutable state snapshot for the reader.
 */
data class ReaderState(
    val content: String = "",
    val title: String = "Document",
    val zoom: Int = 100,
    val theme: ReaderTheme = ReaderTheme.WARM_LIGHT,
    val outline: List<OutlineItem> = emptyList(),
    val activeHeadingId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

enum class ReaderTheme(val id: String, val label: String) {
    WARM_LIGHT("warm-light", "暖色"),
    WARM_DARK("warm-dark", "暖夜"),
    GITHUB("github", "GitHub"),
    SYSTEM("system", "跟随系统"),
}
