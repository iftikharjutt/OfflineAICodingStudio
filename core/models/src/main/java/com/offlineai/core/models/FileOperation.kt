package com.offlineai.core.models

sealed interface FileOperation {
    data class CreateFile(
        val path: String,
        val content: String
    ) : FileOperation

    data class ReplaceBlock(
        val path: String,
        val find: String,
        val replace: String
    ) : FileOperation

    data class ReplaceFile(
        val path: String,
        val content: String
    ) : FileOperation

    data class DeleteFile(
        val path: String
    ) : FileOperation

    data class CreateDirectory(
        val path: String
    ) : FileOperation
}
