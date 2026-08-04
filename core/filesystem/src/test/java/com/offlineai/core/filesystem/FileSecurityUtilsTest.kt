package com.offlineai.core.filesystem

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FileSecurityUtilsTest {

    @Test
    fun testPathSafety_validChildPath() {
        val root = File("/tmp/workspace/project")
        val target = File("/tmp/workspace/project/index.html")
        assertTrue(FileSecurityUtils.isPathSafe(root, target))
    }

    @Test
    fun testPathSafety_prefixBypassBlocked() {
        val root = File("/tmp/workspace/project")
        val target = File("/tmp/workspace/project_evil/secret.txt")
        assertFalse(FileSecurityUtils.isPathSafe(root, target))
    }
}
