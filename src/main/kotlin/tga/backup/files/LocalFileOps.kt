package tga.backup.files

import tga.backup.log.formatFileSize
import tga.backup.log.formatNumber
import tga.backup.terminal.Color
import tga.backup.terminal.Icons
import tga.backup.terminal.style
import tga.backup.utils.ConsoleMultiThreadWorkers
import tga.backup.utils.WorkerPrinter
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

class LocalFileOps(excludePatterns: List<String> = emptyList()) : FileOps("/", excludePatterns) {


    override fun

            getFilesSet(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> {
        val workers = ConsoleMultiThreadWorkers<Set<FileInfo>>(1) // single thread - we use this engine only for status printing

        val result = workers.submit { printer ->
            val rootFile = File(rootPath)
            if (!rootFile.exists()) {
                if (throwIfNotExist) throw RuntimeException("Source directory does not exist: $rootPath")
                return@submit emptySet()
            }
            val localFiles = rootFile.listFilesRecursive(HashSet(), "", printer)
            val totalSize: Long = localFiles.sumOf { it.size }
            val numberOfFiles = localFiles.sumOf { if (it.isDirectory) 0L else 1L }
            printer.updateGlobalStatus("Listed files: ${formatNumber(numberOfFiles)} [total size: ${formatFileSize(totalSize)}]")

            println("\n\nScanning files content (building md5 hashes):\n\n")

            val rootPathWithSeparator = if (rootPath.endsWith(filesSeparator)) rootPath else "$rootPath$filesSeparator"

            val syncStatus = SyncStatus(totalSize, AtomicLong(0L), printer::updateGlobalStatus)

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
                    printer.updateStatus(it.name)
                    try {
                        var md5 = cache.getMd5(it)
                        if (md5 == null) {
                            println("  md5 recalc: ${it.name} (${cache.cacheMissReason(it)})")
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

    override fun copyFile(action: String, from: String, to: String, srcFileOps: FileOps, printer: WorkerPrinter, syncStatus: SyncStatus) {
        when (srcFileOps) {
            is LocalFileOps -> {
                val fFrom = File(from)
                fFrom.copyTo(File(to), overwrite = true)
                syncStatus.updateProgress(fFrom.length())
                syncStatus.formatProgress()
            }
            is YandexFileOps -> {
                downloadFromYandex(action, from, to, srcFileOps, printer, syncStatus)
            }
            is GDriveFileOps -> {
                downloadFromGDrive(action, from, to, srcFileOps, printer, syncStatus)
            }
            else -> throw CopyDirectionIsNotSupportedYet()
        }
    }

    private fun downloadFromYandex(
        action: String,
        from: String,
        to: String,
        srcFileOps: YandexFileOps,
        printer: WorkerPrinter,
        syncStatus: SyncStatus,
    ) {
        val sl = StatusListener(action, from, printer, syncStatus)
        try {
            srcFileOps.downloadFile(from, File(to), sl::updateProgress)
            sl.printDone()
        } catch (e: Throwable) {
            sl.printProgress(e)
            Thread.sleep(2000)
            throw e
        }
    }

    private fun downloadFromGDrive(
        action: String,
        from: String,
        to: String,
        srcFileOps: GDriveFileOps,
        printer: WorkerPrinter,
        syncStatus: SyncStatus,
    ) {
        val sl = StatusListener(action, from, printer, syncStatus)
        try {
            srcFileOps.downloadFile(from, File(to), sl::updateProgress)
            sl.printDone()
        } catch (e: Throwable) {
            sl.printProgress(e)
            Thread.sleep(2000)
            throw e
        }
    }

    class StatusListener(
        val action: String,
        val fileName: String,
        val printer: WorkerPrinter,
        val syncStatus: SyncStatus,
    ) {

        var lastLoaded: Long = 0
        var lastTotal: Long = 1
        var lastUpdateTs: Long = 0
        val totalSizeStr: String = formatFileSize(syncStatus.totalSize)
        private val speedCalculator = SpeedCalculator()

        fun updateProgress(loaded: Long, total: Long) {
            val loadedDelta = loaded - lastLoaded
            syncStatus.updateProgress(loadedDelta)
            speedCalculator.addProgress(loaded)

            lastLoaded = loaded
            lastTotal = total
            val now = System.currentTimeMillis()
            if (now - lastUpdateTs > 250) {
                lastUpdateTs = now
                printProgress()
            }
        }

        fun printDone() {
            printProgress(null, isDone = true)
        }

        fun printProgress(err: Throwable? = null, isDone: Boolean = false) {
            val prc = if (lastTotal > 0) (lastLoaded.toDouble() / lastTotal.toDouble()) else 0.0

            val w = printer.width
            val fixedParts = 35
            val available = (w - fixedParts).coerceAtLeast(30)
            val fileNameLen = (available * 35 / 100).coerceIn(20, 60)
            val progressBarLen = (available - fileNameLen).coerceAtLeast(10)

            val dotsCount = (prc * progressBarLen).toInt()
            var progressBar = ".".repeat(dotsCount).padEnd(progressBarLen)

            val prediction = speedCalculator.predict(lastTotal)
            if (prediction != null && prediction.length < progressBarLen) {
                progressBar = prediction + progressBar.substring(prediction.length)
            }

            if (isDone) progressBar += " ${Icons.CHECK} DONE "

            val shortName = if (fileName.length > fileNameLen) ("..."+fileName.takeLast(fileNameLen-3)) else fileName.padEnd(fileNameLen)
            val percentStr = "%6.2f".format(prc * 100)
            val speedStr = formatFileSize(speedCalculator.getSpeed()).padStart(7)

            val styledAction = style(action, bold = true)
            val styledPct = style("$percentStr%", Color.ACCENT)
            val styledSpeed = style("[$speedStr/s]", Color.MUTED)
            val styledBar = if (isDone) style(progressBar, Color.SUCCESS) else progressBar
            val errStr = if (err != null) style(" ${Icons.CROSS} ERROR: ${err.message}", Color.ERROR) else ""
            printer.updateStatus("$styledAction: $shortName $styledPct $styledSpeed $styledBar$errStr")
            syncStatus.formatProgress()
        }
    }

    override fun deleteFileOrFolder(path: String) {
        File(path).delete()
    }

    override fun moveFileOrFolder(fromPath: String, toPath: String) {
        File(fromPath).renameTo(File(toPath))
    }

    override fun close() {
    }

    private fun File.listFilesRecursive(outSet: MutableSet<FileInfo>, path: String, printer: WorkerPrinter): Set<FileInfo> {
        printer.updateStatus("Listing: ${this.path}")
        val content = this.listFiles() ?: emptyArray()
        content.forEach {
            if (it.name == ".md5") return@forEach
            val fullPath = path + it.name
            if (isExcluded(it.name, fullPath)) {
                return@forEach
            }

            outSet.add(
                FileInfo(
                    name = fullPath,
                    isDirectory = it.isDirectory,
                    size = if (it.isDirectory) 10L else it.length(),
                    creationTime = it.getCreationTime(),
                    lastModifiedTime = it.lastModified(),
                )
            )
        }
        content.forEach {
            val fullPath = path + it.name
            if (it.isDirectory && !isExcluded(it.name, fullPath)) {
                it.listFilesRecursive(outSet, fullPath + filesSeparator, printer)
            }
        }
        printer.updateGlobalStatus("Listed files: ${formatNumber(outSet.size)}]")
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

