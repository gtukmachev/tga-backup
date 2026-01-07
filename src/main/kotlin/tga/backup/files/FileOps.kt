package tga.backup.files

import tga.backup.log.logWrap
import tga.backup.utils.ConsoleMultiThreadWorkers
import java.util.concurrent.atomic.AtomicLong

abstract class FileOps(
    protected val filesSeparator: String
) {
    // Interface part
    abstract fun getFilesSet(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> // platform specific

    fun createFolders(filesList: Set<FileInfo>, dstFolder: String, dryRun: Boolean) {
        val sortedFoldersList = filesList.filter { it.isDirectory }.sorted()

        for (fileInfo in sortedFoldersList) {
            val dstPath = "${dstFolder}${filesSeparator}${fileInfo.name}"
            logWrap("creating folder: $dstPath") {
                if (!dryRun) mkDirs(dstPath)
            }
        }
    }

    fun copyFiles(srcFileOps: FileOps, srcFolder: String, filesList: Set<FileInfo>, dstFolder: String, dryRun: Boolean, override: Boolean = false, workers: ConsoleMultiThreadWorkers<Unit>): List<Result<Unit>> {
        val sortedFilesList = filesList.filter { !it.isDirectory }.sorted()
        val futures = mutableListOf<java.util.concurrent.Future<Result<Unit>>>()
        val totalSize = sortedFilesList.sumOf { it.size }
        val totalLoadedSize = AtomicLong(0L)

        for (fileInfo in sortedFilesList) {
            val srcPath = "${srcFolder}${filesSeparator}${fileInfo.name}"
            val dstPath = "${dstFolder}${filesSeparator}${fileInfo.name}"
            val action = if (override) "overriding" else "copying   "

            futures.add(workers.submit { updateStatus, updateGlobalStatus ->
                if (!dryRun) copyFile(action, srcPath, dstPath, srcFileOps, override, updateStatus, totalSize, totalLoadedSize, updateGlobalStatus)
            })
        }
        workers.waitForCompletion()

        return futures.map { it.get() }
    }

    fun deleteFiles(filesList: Set<FileInfo>, dstFolder: String, dryRun: Boolean): List<Result<Unit>> {
        val results = mutableListOf<Result<Unit>>()
        val sortedFilesList = filesList.sortedDescending()
        for (fileInfo in sortedFilesList) {
            val dstPath = "${dstFolder}${filesSeparator}${fileInfo.name}"
            val fType = if (fileInfo.isDirectory) "folder" else "file"

            try {
                logWrap("deleting $fType: $dstPath...", eatErrors = false) {
                    if (!dryRun) deleteFileOrFolder(dstPath)
                }
                results.add(Result.success(Unit))
            } catch (e: Throwable) {
                results.add(Result.failure(e))
            }
        }
        return results
    }

    // platform specific implementation part
    protected abstract fun mkDirs(dirPath: String)
    protected abstract fun copyFile(
        action: String,
        from: String,
        to: String,
        srcFileOps: FileOps,
        override: Boolean,
        updateStatus: (String) -> Unit,
        totalSize: Long,
        totalLoadedSize: AtomicLong,
        updateGlobalStatus: (String) -> Unit,
    )
    protected abstract fun deleteFileOrFolder(path: String)

    abstract fun close()

}