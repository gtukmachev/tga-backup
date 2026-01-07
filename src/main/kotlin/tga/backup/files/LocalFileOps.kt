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

            // calculating md5 hash for each file (slow operation)
            localFiles.forEach {
                if (!it.isDirectory) {
                    val md5prcDouble = if (totalSize > 0) (scannedSize.toDouble() / totalSize.toDouble() * 100.0) else 0.0
                    val md5prc = "%6.2f".format(md5prcDouble)
                    updateGlobalStatus("md5 calculating: ${formatFileSize(scannedSize)} / $totalSizeStr  ${md5prc}% - ${it.name}")

                    try {
                        val md5 = File(rootPathWithSeparator + it.name).calculateMd5()
                        it.setupMd5(md5)
                    } catch (e: Throwable) {
                        it.readException = e
                    }
                    scannedSize += it.size
                }
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

    override fun copyFile(action: String, from: String, to: String, srcFileOps: FileOps, override: Boolean, updateStatus: (String) -> Unit, totalSize: Long, totalLoadedSize: AtomicLong, updateGlobalStatus: (String) -> Unit) {
        when (srcFileOps) {
            is LocalFileOps -> File(from).copyTo(File(to), overwrite = true)
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
            outSet.add(
                FileInfo(
                    name = path + it.name,
                    isDirectory = it.isDirectory,
                    size = if (it.isDirectory) 10L else it.length(),
                )
            )
        }
        content.forEach { if (it.isDirectory) it.listFilesRecursive(outSet, path + it.name + filesSeparator, updateStatus) }
        return outSet
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

