package tga.backup.files

import tga.backup.log.formatFileSize
import tga.backup.log.logWrap
import tga.backup.utils.ConsoleMultiThreadWorkers
import java.util.concurrent.ConcurrentLinkedDeque
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

    fun copyFiles(srcFileOps: FileOps,
                  srcFolder: String,
                  filesList: Set<FileInfo>,
                  dstFolder: String,
                  dryRun: Boolean,
                  override: Boolean = false,
                  workers: ConsoleMultiThreadWorkers<Unit>
    ): List<Result<Unit>> {
        val sortedFilesList = filesList.filter { !it.isDirectory }.sorted()
        val futures = mutableListOf<java.util.concurrent.Future<Result<Unit>>>()
        val totalSize = sortedFilesList.sumOf { it.size }
        val totalLoadedSize = AtomicLong(0L)
        var syncStatus: SyncStatus? = null

        for (fileInfo in sortedFilesList) {
            val srcPath = "${srcFolder}${filesSeparator}${fileInfo.name}"
            val dstPath = "${dstFolder}${filesSeparator}${fileInfo.name}"
            val action = if (override) "overriding" else "copying   "

            futures.add(workers.submit { updateStatus, updateGlobalStatus ->
                if (syncStatus == null) {
                    synchronized(this) {
                        if (syncStatus == null) {
                            syncStatus = SyncStatus(totalSize, totalLoadedSize, updateGlobalStatus)
                        }
                    }
                }
                if (!dryRun) copyFile(action, srcPath, dstPath, srcFileOps, override, updateStatus, syncStatus!!)
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
        syncStatus: SyncStatus,
    )
    protected abstract fun deleteFileOrFolder(path: String)

    abstract fun close()

}

class SpeedCalculator(val windowMillis: Long = 10000) {
    private val stats = ConcurrentLinkedDeque<Pair<Long, Long>>()

    fun addProgress(loaded: Long) {
        val now = System.currentTimeMillis()
        stats.add(now to loaded)
        val threshold = now - windowMillis
        while (stats.isNotEmpty() && stats.peekFirst()!!.first < threshold) {
            stats.removeFirst()
        }
    }

    fun getSpeed(): Long { // bytes per second
        if (stats.size < 2) return 0
        val first = stats.peekFirst()!!
        val last = stats.peekLast()!!
        val durationMs = last.first - first.first
        if (durationMs <= 0L) return 0
        val bytes = last.second - first.second
        return (bytes * 1000) / durationMs
    }
}

class SyncStatus(
    val totalSize: Long,
    val totalLoadedSize: AtomicLong,
    val updateGlobalStatus: (String) -> Unit
) {
    private val speedCalculator = SpeedCalculator()

    fun updateProgress(loadedDelta: Long) {
        val currentTotal = totalLoadedSize.addAndGet(loadedDelta)
        speedCalculator.addProgress(currentTotal)
    }

    fun getGlobalSpeed(): Long = speedCalculator.getSpeed()

    fun formatProgress() {
        val loadedGlobal = totalLoadedSize.get()
        val globalPrcNum = if (totalSize > 0) (loadedGlobal.toDouble() / totalSize.toDouble()) * 100 else 0.0
        val globalPrc = "%6.2f".format(globalPrcNum)
        val loadedSizeStr = formatFileSize(loadedGlobal)
        val totalSizeStr = formatFileSize(totalSize)
        val speedStr = formatFileSize(getGlobalSpeed())

        updateGlobalStatus("Global status: ${globalPrc}%  $loadedSizeStr / $totalSizeStr [$speedStr/s]")
    }
}