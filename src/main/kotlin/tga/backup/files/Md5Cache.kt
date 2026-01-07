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
                        val name = parts[0].trim()
                        val size = parts[1].trim().toLong()
                        val creationTime = df.parse(parts[2].trim()).time
                        val lastModifiedTime = df.parse(parts[3].trim()).time
                        val md5 = parts[4].trim()
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
            val sortedEntries = entries.values.sortedBy { it.name }
            val formattedEntries = sortedEntries.map { entry ->
                listOf(
                    entry.name,
                    entry.size.toString(),
                    df.format(Date(entry.creationTime)),
                    df.format(Date(entry.lastModifiedTime)),
                    entry.md5
                )
            }

            val maxWidths = IntArray(5) { i -> formattedEntries.maxOf { it[i].length } }

            cacheFile.bufferedWriter().use { writer ->
                formattedEntries.forEach { parts ->
                    val line = parts.mapIndexed { i, value ->
                        when (i) {
                            0 -> value.padEnd(maxWidths[i]) // name aligned by start
                            1 -> value.padStart(maxWidths[i]) // size aligned to the right
                            else -> value // dates and md5 (dates have same length by default, md5 too)
                        }
                    }.joinToString("\t")
                    writer.write(line)
                    writer.write("\n")
                }
            }
            modified = false
        }
    }
}
