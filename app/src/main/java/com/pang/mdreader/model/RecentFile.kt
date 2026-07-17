package com.pang.mdreader.model

/**
 * A recently opened document, persisted across sessions.
 */
data class RecentFile(
    val uri: String,
    val name: String,
    val workspaceUri: String,
    val lastOpened: Long = System.currentTimeMillis(),
)
