package com.offlineai.core.models

sealed interface FileOperation {
    val path: String

    data class CreateFile(
        override val path: String,
        val content: String
    ) : FileOperation

    data class ReplaceBlock(
        override val path: String,
        val find: String,
        val replace: String
    ) : FileOperation

    data class ReplaceFile(
        override val path: String,
        val content: String
    ) : FileOperation

    data class DeleteFile(
        override val path: String
    ) : FileOperation

    data class CreateDirectory(
        override val path: String
    ) : FileOperation
}
