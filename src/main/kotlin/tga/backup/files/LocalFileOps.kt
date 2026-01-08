package tga.backup.files

import tga.backup.log.formatFileSize
import tga.backup.log.formatNumber
import tga.backup.utils.ConsoleMultiThreadWorkers
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

class LocalFileOps : FileOps("/") {


    override fun getFilesSet(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> {
        val workers = ConsoleMultiThreadWorkers<Set<FileInfo>>(1) // single thread - we use this engine only for status printing

        val result = workers.submit { updateStatus, updateGlobalStatus ->
            updateStatus("Scanning: $rootPath")
            val rootFile = File(rootPath)
            if (!rootFile.exists()) {
                if (throwIfNotExist) throw RuntimeException("Source directory does not exist: $rootPath")
                return@submit emptySet()
            }
            val localFiles = rootFile.listFilesRecursive(HashSet(), "", updateStatus)
            val totalSize: Long = localFiles.sumOf { it.size }
            val totalSizeStr = formatFileSize(totalSize)
            var scannedSize: Long = 0L
            val numberOfFiles = localFiles.sumOf { if (it.isDirectory) 0L else 1L }
            updateStatus("Scanned: [files: ${formatNumber(numberOfFiles)}] [size: ${formatFileSize(totalSize)}]")

            val rootPathWithSeparator = if (rootPath.endsWith(filesSeparator)) rootPath else "$rootPath$filesSeparator"

            val syncStatus = SyncStatus(totalSize, AtomicLong(0L), updateGlobalStatus)

            // calculating md5 hash for each file (slow operation)
            val filesByFolder = localFiles.filter { !it.isDirectory }.groupBy {
                val lastSeparatorIndex = it.name.lastIndexOf(filesSeparator)
                if (lastSeparatorIndex == -1) "" else it.name.substring(0, lastSeparatorIndex)
            }

            for ((folderPath, filesInFolder) in filesByFolder) {
                val folderFile = File(rootPathWithSeparator + folderPath)
                val cache = Md5Cache(folderFile)

                for (it in filesInFolder) {
                    syncStatus.formatProgress()

                    try {
                        var md5 = cache.getMd5(it)
                        if (md5 == null) {
                            md5 = File(rootPathWithSeparator + it.name).calculateMd5()
                            cache.updateMd5(it, md5)
                        }
                        it.setupMd5(md5)
                    } catch (e: Throwable) {
                        it.readException = e
                    }
                    syncStatus.updateProgress(it.size)
                }
                cache.save()
            }

            return@submit localFiles
        }

        try {
            return result.get().getOrThrow()
        } finally {
            workers.waitForCompletion()
        }
    }

    override fun mkDirs(dirPath: String) {
        File(dirPath).mkdirs()
    }

    override fun copyFile(action: String, from: String, to: String, srcFileOps: FileOps, override: Boolean, updateStatus: (String) -> Unit, syncStatus: SyncStatus) {
        val fFrom = File(from)
        when (srcFileOps) {
            is LocalFileOps -> {
                fFrom.copyTo(File(to), overwrite = true)
                syncStatus.updateProgress(fFrom.length())
                syncStatus.formatProgress()
            }
            else -> throw CopyDirectionIsNotSupportedYet()
        }
    }

    override fun deleteFileOrFolder(path: String) {
        File(path).delete()
    }

    override fun close() {
    }

    private fun File.listFilesRecursive(outSet: MutableSet<FileInfo>, path: String, updateStatus: (String) -> Unit): Set<FileInfo> {
        updateStatus("Scanning: ${this.path}")
        val content = this.listFiles() ?: emptyArray()
        content.forEach {
            if (it.name == ".md5" || it.name.startsWith("._")) {
                return@forEach
            }

            outSet.add(
                FileInfo(
                    name = path + it.name,
                    isDirectory = it.isDirectory,
                    size = if (it.isDirectory) 10L else it.length(),
                    creationTime = it.getCreationTime(),
                    lastModifiedTime = it.lastModified(),
                )
            )
        }
        content.forEach {
            if (it.isDirectory && it.name != ".md5" && !it.name.startsWith("._")) {
                it.listFilesRecursive(outSet, path + it.name + filesSeparator, updateStatus)
            }
        }
        return outSet
    }

    private fun File.getCreationTime(): Long {
        return try {
            val attr = java.nio.file.Files.readAttributes(this.toPath(), java.nio.file.attribute.BasicFileAttributes::class.java)
            attr.creationTime().toMillis()
        } catch (e: Exception) {
            0L
        }
    }

    private fun File.calculateMd5(): String {
        val digest = MessageDigest.getInstance("MD5")
        this.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

}

