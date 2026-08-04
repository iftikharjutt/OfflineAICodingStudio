package com.offlineai.core.filesystem

data class FileTreeNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0,
    val extension: String = "",
    val children: List<FileTreeNode> = emptyList(),
    val isExpanded: Boolean = false
)
