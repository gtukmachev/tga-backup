package tga.backup.files

import tga.backup.log.formatFileSize
import tga.backup.log.formatNumber
import tga.backup.log.formatTime
import tga.backup.log.logWrap
import tga.backup.terminal.Color
import tga.backup.terminal.style
import tga.backup.utils.ConsoleMultiThreadWorkers
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

abstract class FileOps(
    val filesSeparator: String,
    val excludePatterns: List<String> = emptyList()
) {

    open fun generateWebLink(path: String, rootPath: String = ""): String = ""

    private val exclusionMatcher: ExclusionMatcher by lazy {
        ExclusionMatcher(excludePatterns)
    }

    protected fun isExcluded(fileName: String, fullPath: String? = null): Boolean {
        return exclusionMatcher.isExcluded(fileName, fullPath)
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

    fun deleteFiles(
        filesList: Set<FileInfo>,
        dstFolder: String,
        dryRun: Boolean,
        noDeletion: Boolean = false,
        workers: ConsoleMultiThreadWorkers<Unit>? = null
    ): List<Result<Unit>> {
        if (noDeletion) return emptyList()
        if (filesList.isEmpty()) return emptyList()

        if (workers == null) {
            return deleteFilesSequential(filesList, dstFolder, dryRun)
        }

        val files = filesList.filter { !it.isDirectory }
        val folders = filesList.filter { it.isDirectory }

        val deletedFileCount = AtomicInteger(0)
        val deletedFileSize = AtomicLong(0)
        val deletedFolderCount = AtomicInteger(0)
        val totalFiles = files.size
        val totalFolders = folders.size

        fun updateGlobal() {
            val fc = deletedFileCount.get()
            val fs = deletedFileSize.get()
            val dc = deletedFolderCount.get()
            workers.outputGlobalStatus(
                "Deleting: ${formatNumber(fc)}/${formatNumber(totalFiles)} files (${formatFileSize(fs)}) | ${formatNumber(dc)}/${formatNumber(totalFolders)} folders"
            )
        }

        val results = mutableListOf<Result<Unit>>()

        // Phase 1: delete files in parallel
        val fileFutures = files.map { fileInfo ->
            val dstPath = "${dstFolder}${filesSeparator}${fileInfo.name}"
            workers.submit { updateStatus, _ ->
                updateStatus("deleting file: $dstPath")
                if (!dryRun) deleteFileOrFolder(dstPath)
                deletedFileCount.incrementAndGet()
                deletedFileSize.addAndGet(fileInfo.size)
                updateGlobal()
                updateStatus("")
            }
        }
        fileFutures.forEach { results.add(it.get()) }

        // Phase 2: delete folders level by level (deepest first)
        val foldersByDepth = folders
            .groupBy { it.name.count { ch -> ch == filesSeparator[0] } }
            .toSortedMap(compareByDescending { it })

        for ((_, levelFolders) in foldersByDepth) {
            val levelFutures = levelFolders.map { fileInfo ->
                val dstPath = "${dstFolder}${filesSeparator}${fileInfo.name}"
                workers.submit { updateStatus, _ ->
                    updateStatus("deleting folder: $dstPath")
                    if (!dryRun) deleteFileOrFolder(dstPath)
                    deletedFolderCount.incrementAndGet()
                    updateGlobal()
                    updateStatus("")
                }
            }
            levelFutures.forEach { results.add(it.get()) }
        }

        workers.waitForCompletion()
        return results
    }

    private fun deleteFilesSequential(filesList: Set<FileInfo>, dstFolder: String, dryRun: Boolean): List<Result<Unit>> {
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

        val styledLabel = style("Global status:", bold = true)
        val styledPct = style("${globalPrc}%", Color.ACCENT)
        val styledSizes = style("$loadedSizeStr / $totalSizeStr", Color.MUTED)
        val styledSpeed = style("[$speedStr/s]", Color.INFO)
        updateGlobalStatus("$styledLabel $styledPct  $styledSizes $predictionStr $styledSpeed")
    }
}