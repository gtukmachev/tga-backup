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
        private val speedCalculator = SpeedCalculator()
        private var spinIndex = 0
        private val spinChars = charArrayOf('|', '/', '-', '\\')

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

            val percentStr = "%6.2f".format(prc * 100)
            val speedStr = formatFileSize(speedCalculator.getSpeed()).padStart(7)
            val prediction = speedCalculator.predict(lastTotal)

            if (err != null) {
                val styledAction = style(action, bold = true)
                val styledPct = style("$percentStr%", Color.ACCENT)
                printer.updateStatus("$styledAction $fileName $styledPct ${style("${Icons.CROSS} Error: ${err.message}", Color.ERROR)}")
                syncStatus.formatProgress()
                return
            }

            val indicator = if (isDone) "✔" else spinChars[spinIndex++ % spinChars.size].toString()
            val rightText = " $indicator $percentStr% $speedStr/s${if (prediction != null) " $prediction" else ""}"
            val rightLen = rightText.length

            val actionPart = "$action "
            val minBarLen = 10
            val fileNameBudget = (w - actionPart.length - rightLen - minBarLen - 1).coerceIn(15, 60)
            val shortName = if (fileName.length > fileNameBudget) ("..." + fileName.takeLast(fileNameBudget - 3)) else fileName

            val leftLen = actionPart.length + shortName.length + 1
            val barLen = (w - leftLen - rightLen).coerceAtLeast(minBarLen)

            val filledCount = (prc * barLen).toInt()
            val bar = ".".repeat(filledCount).padEnd(barLen)

            val styledAction = style(action, bold = true)
            val styledBar = if (isDone) style(bar, Color.SUCCESS) else bar
            val styledIndicator = if (isDone) style(indicator, Color.SUCCESS) else indicator
            val styledPct = style("$percentStr%", Color.ACCENT)
            val styledSpeed = style("$speedStr/s", Color.MUTED)
            val styledPrediction = if (prediction != null) " $prediction" else ""

            printer.updateStatus("$styledAction $shortName $styledBar $styledIndicator $styledPct $styledSpeed$styledPrediction")
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

