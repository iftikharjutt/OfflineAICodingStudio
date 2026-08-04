package com.offlineai.core.filesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {

    private const val MAX_DECOMPRESSED_SIZE = 512 * 1024 * 1024L // 512 MB limit

    suspend fun exportProjectToZip(
        projectDir: File,
        destinationZip: File
    ) = withContext(Dispatchers.IO) {
        require(projectDir.isDirectory) { "Project path must be a directory" }
        destinationZip.parentFile?.mkdirs()

        ZipOutputStream(FileOutputStream(destinationZip)).use { zipOut ->
            projectDir.walkTopDown().forEach { file ->
                if (!file.isDirectory) {
                    val relativePath = file.relativeTo(projectDir).path
                    val entry = ZipEntry(relativePath)
                    zipOut.putNextEntry(entry)
                    FileInputStream(file).use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }
        }
    }

    suspend fun importProjectFromZip(
        zipFile: File,
        targetProjectsDir: File,
        projectName: String
    ): File = withContext(Dispatchers.IO) {
        val destDir = File(targetProjectsDir, projectName)
        destDir.mkdirs()

        var totalBytesWritten = 0L

        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                val entryFile = File(destDir, entry.name)

                require(FileSecurityUtils.isPathSafe(destDir, entryFile)) {
                    "Zip entry contains path traversal: ${entry.name}"
                }

                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
                    FileOutputStream(entryFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                            totalBytesWritten += bytesRead
                            if (totalBytesWritten > MAX_DECOMPRESSED_SIZE) {
                                throw IllegalStateException(
                                    "Zip extraction exceeded size limit " +
                                    "(${MAX_DECOMPRESSED_SIZE / (1024 * 1024)} MB). " +
                                    "Possible zip bomb."
                                )
                            }
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
        destDir
    }
}
