package com.offlineai.ai.prompting

import com.offlineai.core.models.FileOperation

data class ParsedAiResponse(
    val summary: String,
    val operations: List<FileOperation>
)

object FileOperationParser {

    fun parseJsonResponse(jsonStr: String): Result<ParsedAiResponse> {
        return try {
            val sanitized = jsonStr.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            // Robust JSON extraction without org.json stub dependency for JVM unit tests
            val summaryRegex = """"summary"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex()
            val summaryMatch = summaryRegex.find(sanitized)
            val summary = summaryMatch?.groupValues?.get(1)?.replace("\\\"", "\"") ?: "AI Project Patch"

            val operations = mutableListOf<FileOperation>()

            val opRegex = """\{\s*"type"\s*:\s*"([^"]+)"\s*,\s*"path"\s*:\s*"([^"]+)"(?:[^\}]*?)\\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
            
            // Extract individual operations via regex / manual block parsing
            val opsBlock = sanitized.substringAfter(""""operations"""", "").substringAfter("[", "").substringBeforeLast("]", "")
            
            val blocks = opsBlock.split(Regex("""\}\s*,\s*\{"""))
            for (rawBlock in blocks) {
                val block = rawBlock.trim().removePrefix("{").removeSuffix("}")
                if (block.isBlank()) continue

                val type = """"type"\s*:\s*"([^"]+)"""".toRegex().find(block)?.groupValues?.get(1) ?: continue
                val path = """"path"\s*:\s*"([^"]+)"""".toRegex().find(block)?.groupValues?.get(1) ?: continue

                when (type) {
                    "create_file" -> {
                        val content = """"content"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex().find(block)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", "\n") ?: ""
                        operations.add(FileOperation.CreateFile(path, content))
                    }
                    "replace_file" -> {
                        val content = """"content"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex().find(block)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", "\n") ?: ""
                        operations.add(FileOperation.ReplaceFile(path, content))
                    }
                    "replace_block" -> {
                        val find = """"find"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex().find(block)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", "\n") ?: ""
                        val replace = """"replace"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex().find(block)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\n", "\n") ?: ""
                        operations.add(FileOperation.ReplaceBlock(path, find, replace))
                    }
                    "delete_file" -> {
                        operations.add(FileOperation.DeleteFile(path))
                    }
                    "create_directory" -> {
                        operations.add(FileOperation.CreateDirectory(path))
                    }
                }
            }

            Result.success(ParsedAiResponse(summary, operations))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
