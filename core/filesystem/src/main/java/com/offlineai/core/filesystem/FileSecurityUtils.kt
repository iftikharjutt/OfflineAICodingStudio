package com.offlineai.core.filesystem

import java.io.File
import java.nio.file.Path

object FileSecurityUtils {

    fun isPathSafe(root: File, target: File): Boolean {
        val canonicalRoot = root.canonicalFile.toPath()
        val canonicalTarget = target.canonicalFile.toPath()
        return canonicalTarget.startsWith(canonicalRoot)
    }
}
