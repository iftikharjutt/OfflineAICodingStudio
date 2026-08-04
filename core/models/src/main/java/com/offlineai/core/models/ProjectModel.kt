package com.offlineai.core.models

data class ProjectModel(
    val id: String,
    val name: String,
    val path: String,
    val createdAt: Long,
    val updatedAt: Long
)
