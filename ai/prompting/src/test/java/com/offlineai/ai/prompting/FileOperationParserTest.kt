package com.offlineai.ai.prompting

import com.offlineai.core.models.FileOperation
import org.junit.Assert.*
import org.junit.Test

class FileOperationParserTest {

    @Test
    fun testParseJsonResponse_validCreateFile() {
        val json = """
        {
          "summary": "Create index.html",
          "operations": [
            {
              "type": "create_file",
              "path": "index.html",
              "content": "<h1>Hello World</h1>"
            }
          ]
        }
        """.trimIndent()

        val result = FileOperationParser.parseJsonResponse(json)
        assertTrue(result.isSuccess)
        val parsed = result.getOrNull()
        assertNotNull(parsed)
        assertEquals("Create index.html", parsed?.summary)
        assertEquals(1, parsed?.operations?.size)

        val op = parsed?.operations?.first()
        assertTrue(op is FileOperation.CreateFile)
        val createFileOp = op as FileOperation.CreateFile
        assertEquals("index.html", createFileOp.path)
        assertEquals("<h1>Hello World</h1>", createFileOp.content)
    }
}
