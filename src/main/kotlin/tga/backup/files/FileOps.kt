package tga.backup.files

import tga.backup.log.formatFileSize
import tga.backup.log.formatTime
import tga.backup.log.logWrap
import tga.backup.utils.ConsoleMultiThreadWorkers
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

abstract class FileOps(
    val filesSeparator: String,
    val excludePatterns: List<String> = emptyList()
) {
    private val excludeRegexes: List<Regex> by lazy {
        excludePatterns.map { Regex(it) }
    }

    protected fun isExcluded(fileName: String): Boolean {
        return excludeRegexes.any { it.matches(fileName) }
    }
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
        val syncStatus = SyncStatus(totalSize, totalLoadedSize, workers::outputGlobalStatus)

        for (fileInfo in sortedFilesList) {
            val srcPath = "${srcFolder}${filesSeparator}${fileInfo.name}"
            val dstPath = "${dstFolder}${filesSeparator}${fileInfo.name}"
            val action = if (override) "overriding" else "copying   "

            futures.add(workers.submit { updateStatus, _ ->
                if (!dryRun) copyFile(action, srcPath, dstPath, srcFileOps, updateStatus, syncStatus)
            })
        }
        workers.waitForCompletion()

        return futures.map { it.get() }
    }

    fun deleteFiles(filesList: Set<FileInfo>, dstFolder: String, dryRun: Boolean, noDeletion: Boolean = false): List<Result<Unit>> {
        if (noDeletion) return emptyList()

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
        updateStatus: (String) -> Unit,
        syncStatus: SyncStatus,
    )
    protected abstract fun deleteFileOrFolder(path: String)

    abstract fun moveFileOrFolder(fromPath: String, toPath: String)

    abstract fun close()

}

class SpeedCalculator(val windowMillis: Long = 10000, private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val stats = ConcurrentLinkedDeque<Pair<Long, Long>>()
    private val startTime = clock()

    @Synchronized
    fun addProgress(loaded: Long) {
        val now = clock()
        stats.add(now to loaded)
        val threshold = now - windowMillis
        while (stats.isNotEmpty() && stats.peekFirst()!!.first < threshold) {
            stats.removeFirst()
        }
    }

    @Synchronized
    fun getSpeed(): Long { // bytes per second
        if (stats.size < 2) return 0
        val first = stats.peekFirst()!!
        val last = stats.peekLast()!!
        val durationMs = last.first - first.first
        if (durationMs <= 0L) return 0
        val bytes = last.second - first.second
        return (bytes * 1000) / durationMs
    }

    @Synchronized
    fun predict(totalSize: Long): String? {
        val now = clock()
        if (now - startTime < 3000) return null

        val speed = getSpeed()
        if (speed <= 0) return null

        val last = stats.peekLast() ?: return null
        val loaded = last.second
        val remaining = totalSize - loaded
        if (remaining <= 0) return null

        val remainingMs = (remaining * 1000) / speed
        val totalMs = (totalSize * 1000) / speed

        if (totalMs < 10000) return null

        return "(${formatTime(totalMs)} | ${formatTime(remainingMs)})"
    }
}

class SyncStatus(
    val totalSize: Long,
    val totalLoadedSize: AtomicLong,
    val updateGlobalStatus: (String) -> Unit
) {
    private val speedCalculator = SpeedCalculator()

    @Synchronized
    fun updateProgress(loadedDelta: Long) {
        val currentTotal = totalLoadedSize.addAndGet(loadedDelta)
        speedCalculator.addProgress(currentTotal)
    }

    fun getGlobalSpeed(): Long = speedCalculator.getSpeed()

    @Synchronized
    fun formatProgress() {
        val loadedGlobal = totalLoadedSize.get()
        val globalPrcNum = if (totalSize > 0) (loadedGlobal.toDouble() / totalSize.toDouble()) * 100 else 0.0
        val globalPrc = "%6.2f".format(globalPrcNum)
        val loadedSizeStr = formatFileSize(loadedGlobal)
        val totalSizeStr = formatFileSize(totalSize)
        val speedStr = formatFileSize(getGlobalSpeed())
        val prediction = speedCalculator.predict(totalSize)
        val predictionStr = (prediction ?: "").padStart(30)

        updateGlobalStatus("Global status: ${globalPrc}%  $loadedSizeStr / $totalSizeStr $predictionStr [$speedStr/s]")
    }
}