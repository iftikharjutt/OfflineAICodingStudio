package com.offlineai.ai.prompting

import com.offlineai.core.models.FileOperation

data class ParsedAiResponse(
    val summary: String,
    val operations: List<FileOperation>
)

object FileOperationParser {

    fun parseJsonResponse(jsonStr: String): Result<ParsedAiResponse> {
        return try {
            val firstBrace = jsonStr.indexOf('{')
            val lastBrace = jsonStr.lastIndexOf('}')
            if (firstBrace == -1 || lastBrace <= firstBrace) {
                return Result.failure(IllegalStateException("No JSON object found in LLM output"))
            }

            val sanitized = jsonStr.substring(firstBrace, lastBrace + 1)

            // Try standard org.json.JSONObject first
            try {
                val jsonObject = org.json.JSONObject(sanitized)
                val summary = jsonObject.optString("summary", "").trim()
                val opsArray = jsonObject.optJSONArray("operations")
                val operations = mutableListOf<FileOperation>()

                if (opsArray != null) {
                    for (i in 0 until opsArray.length()) {
                        val opObj = opsArray.optJSONObject(i) ?: continue
                        val type = opObj.optString("type", "")
                        val path = opObj.optString("path", "")
                        when (type) {
                            "create_file" -> operations.add(FileOperation.CreateFile(path, opObj.optString("content", "")))
                            "replace_file" -> operations.add(FileOperation.ReplaceFile(path, opObj.optString("content", "")))
                            "replace_block" -> operations.add(FileOperation.ReplaceBlock(path, opObj.optString("find", ""), opObj.optString("replace", "")))
                            "delete_file" -> operations.add(FileOperation.DeleteFile(path))
                            "create_directory" -> operations.add(FileOperation.CreateDirectory(path))
                        }
                    }
                }
                return Result.success(ParsedAiResponse(summary, operations))
            } catch (e: Throwable) {
                // Fallback to regex parsing for local JVM unit test context where org.json is a stub
            }

            val summaryMatch = """"summary"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex().find(sanitized)
            val summary = summaryMatch?.groupValues?.get(1)?.replace("\\\"", "\"")?.trim() ?: ""

            val operations = mutableListOf<FileOperation>()
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
