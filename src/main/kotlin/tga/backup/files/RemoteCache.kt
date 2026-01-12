package tga.backup.files

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

fun getCacheFilePath(profile: String, remoteRoot: String): String {
    val userHome = System.getProperty("user.home")
    val profileDir = File(userHome, ".tga-backup/$profile")
    
    // Escape special characters in remote root path
    val escapedRemoteRoot = remoteRoot
        .replace(Regex("[/\\\\:*?\"<>|]"), "_")
        .trim('_')
    
    return File(profileDir, "$escapedRemoteRoot-files.txt").absolutePath
}

fun writeRemoteCache(cacheFilePath: String, files: Set<FileInfo>) {
    val cacheFile = File(cacheFilePath)
    cacheFile.parentFile.mkdirs()
    
    val newContent = files
        .sortedBy { it.name }
        .joinToString("\n") { fileInfo ->
            "${fileInfo.name}\t${fileInfo.isDirectory}\t${fileInfo.size}\t${fileInfo.md5 ?: ""}"
        }
    
    // Optimization: only write if content differs
    if (cacheFile.exists()) {
        val existingContent = cacheFile.readText()
        if (existingContent == newContent) {
            logger.debug { "Cache file unchanged, skipping write: $cacheFilePath" }
            return
        }
    }
    
    cacheFile.writeText(newContent)
    logger.info { "Remote cache written: $cacheFilePath (${files.size} files)" }
}

fun readRemoteCache(cacheFilePath: String): Set<FileInfo>? {
    val cacheFile = File(cacheFilePath)
    if (!cacheFile.exists()) {
        logger.info { "Cache file not found: $cacheFilePath" }
        return null
    }
    
    return try {
        val files = cacheFile.readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("\t")
                if (parts.size < 3) {
                    throw IllegalArgumentException("Invalid cache line format: $line")
                }
                val name = parts[0]
                val isDirectory = parts[1].toBoolean()
                val size = parts[2].toLong()
                val md5 = if (parts.size > 3 && parts[3].isNotBlank()) parts[3] else null
                val fileInfo = FileInfo(name, isDirectory, size)
                if (md5 != null) {
                    fileInfo.setupMd5(md5)
                }
                fileInfo
            }
            .toSet()
        
        logger.info { "Remote cache loaded: $cacheFilePath (${files.size} files)" }
        files
    } catch (e: Exception) {
        logger.error(e) { "Failed to read cache file: $cacheFilePath" }
        null
    }
}
