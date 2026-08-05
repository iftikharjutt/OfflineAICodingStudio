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
            val jsonObject = org.json.JSONObject(sanitized)

            if (!jsonObject.has("summary") && !jsonObject.has("operations")) {
                return Result.failure(IllegalStateException("JSON output missing 'summary' or 'operations' keys"))
            }

            val summary = jsonObject.optString("summary", "").trim()
            val opsArray = jsonObject.optJSONArray("operations")
            val operations = mutableListOf<FileOperation>()

            if (opsArray != null) {
                for (i in 0 until opsArray.length()) {
                    val opObj = opsArray.optJSONObject(i) ?: continue
                    val type = opObj.optString("type", "")
                    val path = opObj.optString("path", "")
                    when (type) {
                        "create_file" -> {
                            operations.add(FileOperation.CreateFile(path, opObj.optString("content", "")))
                        }
                        "replace_file" -> {
                            operations.add(FileOperation.ReplaceFile(path, opObj.optString("content", "")))
                        }
                        "replace_block" -> {
                            operations.add(FileOperation.ReplaceBlock(path, opObj.optString("find", ""), opObj.optString("replace", "")))
                        }
                        "delete_file" -> {
                            operations.add(FileOperation.DeleteFile(path))
                        }
                        "create_directory" -> {
                            operations.add(FileOperation.CreateDirectory(path))
                        }
                    }
                }
            }

            Result.success(ParsedAiResponse(summary, operations))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
