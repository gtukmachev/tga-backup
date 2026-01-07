package tga.backup.files

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class Md5Cache(val folder: File) {
    private val cacheFile = File(folder, ".md5")
    private val entries = mutableMapOf<String, CacheEntry>()
    private var modified = false

    private val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

    data class CacheEntry(
        val name: String,
        val size: Long,
        val creationTime: Long,
        val lastModifiedTime: Long,
        val md5: String
    )

    init {
        load()
    }

    private fun load() {
        if (cacheFile.exists()) {
            cacheFile.forEachLine { line ->
                val parts = line.split("\t")
                if (parts.size == 5) {
                    try {
                        val name = parts[0]
                        val size = parts[1].toLong()
                        val creationTime = df.parse(parts[2]).time
                        val lastModifiedTime = df.parse(parts[3]).time
                        val md5 = parts[4]
                        entries[name] = CacheEntry(name, size, creationTime, lastModifiedTime, md5)
                    } catch (e: Exception) {
                        // skip invalid line
                    }
                }
            }
        }
    }

    fun getMd5(fileInfo: FileInfo): String? {
        val fileName = File(fileInfo.name).name
        val entry = entries[fileName]
        if (entry != null &&
            entry.size == fileInfo.size &&
            entry.creationTime == fileInfo.creationTime &&
            entry.lastModifiedTime == fileInfo.lastModifiedTime
        ) {
            return entry.md5
        }
        return null
    }

    fun updateMd5(fileInfo: FileInfo, md5: String) {
        val fileName = File(fileInfo.name).name
        val newEntry = CacheEntry(
            fileName,
            fileInfo.size,
            fileInfo.creationTime,
            fileInfo.lastModifiedTime,
            md5
        )
        if (entries[fileName] != newEntry) {
            entries[fileName] = newEntry
            modified = true
        }
    }

    fun save() {
        if (modified) {
            cacheFile.bufferedWriter().use { writer ->
                entries.values.sortedBy { it.name }.forEach { entry ->
                    writer.write("${entry.name}\t${entry.size}\t${df.format(Date(entry.creationTime))}\t${df.format(Date(entry.lastModifiedTime))}\t${entry.md5}\n")
                }
            }
            modified = false
        }
    }
}
