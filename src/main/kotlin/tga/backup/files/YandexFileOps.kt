package tga.backup.files

import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import tga.backup.log.formatFileSize
import tga.backup.log.formatNumber
import tga.backup.log.toLog
import tga.backup.terminal.Color
import tga.backup.terminal.Icons
import tga.backup.terminal.style
import tga.backup.utils.ConsoleMultiThreadWorkers
import tga.backup.utils.DynamicTask
import tga.backup.utils.WorkerPrinter
import tga.backup.yandex.YandexResponseException
import tga.backup.yandex.YandexResumableUploader
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class YandexFileOps(
    private val yandex: YandexResumableUploader,
    val maxPageSize: Int  = 5000,
    val profile: String,
    val useCache: Boolean,
    excludePatterns: List<String> = emptyList()
) : FileOps(filesSeparator = "/", excludePatterns) {

    private val logger = KotlinLogging.logger {  }

    override fun getFilesSet(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> {
        val cacheFilePath = getCacheFilePath(profile, rootPath)
        if (useCache) {
            readRemoteCache(cacheFilePath)?.let { return it }
        }

        val files = scanYandex(rootPath, throwIfNotExist)
        writeRemoteCacheIfChanged(cacheFilePath, files)

        return files
    }

    private fun scanYandex(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> {
        println("\nLoading files tree from yandex disk:")

        val fullRootPath = rootPath.removePrefix("yandex://")
        val fullRootPathPrefix = "/$fullRootPath"
        val files = ConcurrentHashMap.newKeySet<FileInfo>()
        val totalSize = AtomicLong(0)

        val workers = ConsoleMultiThreadWorkers<Unit>(20)

        fun updateGlobalLine(printer: WorkerPrinter) {
            val count = files.size
            val size = totalSize.get()
            printer.updateGlobalStatus("${style("Scanning Yandex:", bold = true)} ${style(formatNumber(count.toLong()), Color.ACCENT)} files ${style("[${formatFileSize(size)}]", Color.MUTED)}")
        }

        fun scanFolder(
            path: String,
            printer: WorkerPrinter,
            submitChild: (DynamicTask<Unit>) -> Unit
        ) {
            var offset = 0
            while (true) {
                val shortPath = path.removePrefix(fullRootPath).ifEmpty { "/" }
                printer.updateStatus("${style("Fetching:", Color.INFO)} $shortPath" + if (offset > 0) " (offset: $offset)" else "")
                logger.debug { "Fetching folder metadata: $path (offset: $offset)" }

                val resource = try {
                    yandex.getResources(path, maxPageSize, offset)
                } catch (e: YandexResponseException) {
                    if (path == fullRootPath && e.code == 404) {
                        if (throwIfNotExist) throw RuntimeException("Source directory does not exist: $path", e)
                        return
                    }
                    if (e.code != 404) throw e
                    null
                }

                if (resource == null) break

                if (offset == 0) {
                    val fileInfo = resource.toFileInfo(fullRootPathPrefix)
                    if (fileInfo.name.isNotEmpty()) {
                        files.add(fileInfo)
                        updateGlobalLine(printer)
                    }
                }

                val embedded = resource.getAsJsonObject("_embedded")
                val itemsArray = embedded?.getAsJsonArray("items")
                val items = mutableListOf<JsonObject>()
                itemsArray?.forEach { items.add(it.asJsonObject) }

                val total = embedded?.get("total")?.asInt ?: 0

                items.forEach { item ->
                    val name = item.get("name").asString
                    val itemPath = item.get("path").asString.removePrefix("disk:")
                    val fullPath = itemPath.removePrefix(fullRootPathPrefix).removePrefix("/")

                    if (isExcluded(name, fullPath)) {
                        return@forEach
                    }

                    val type = item.get("type").asString
                    if (type == "dir") {
                        submitChild(DynamicTask { childPrinter, childSubmit ->
                            scanFolder(itemPath, childPrinter, childSubmit)
                        })
                    } else {
                        val fileInfo = item.toFileInfo(fullRootPathPrefix)
                        files.add(fileInfo)
                        totalSize.addAndGet(fileInfo.size)
                        updateGlobalLine(printer)
                    }
                }

                val itemsCount = items.size
                if (itemsCount < maxPageSize || (offset + itemsCount).toLong() >= total.toLong()) break
                offset += itemsCount
            }

            printer.updateStatus("")
        }

        workers.submitDynamic { printer, submitChild ->
            scanFolder(fullRootPath, printer, submitChild)
        }

        workers.awaitDynamic()
        workers.shutdown()

        println("...done\n")

        return files
    }

    private val createdFolders = ConcurrentHashMap.newKeySet<String>()

    override fun mkDirs(dirPath: String) {
        val path = dirPath.toYandexPath().removePrefix("/")
        if (path.isEmpty()) return

        val folders = path.split("/").filter { it.isNotEmpty() }
        var currentPath = ""

        for (folder in folders) {
            if (currentPath.isNotEmpty()) currentPath += "/"
            currentPath += folder

            if (createdFolders.contains(currentPath)) continue

            try {
                yandex.makeFolder(currentPath)
                createdFolders.add(currentPath)
            } catch (e: YandexResponseException) {
                if (e.code == 409) {
                    createdFolders.add(currentPath)
                } else {
                    throw e
                }
            }
        }
    }

    override fun copyFile(
        action: String,
        from: String,
        to: String,
        srcFileOps: FileOps,
        printer: WorkerPrinter,
        syncStatus: SyncStatus,
    ) {
        when (srcFileOps) {
            is LocalFileOps -> uploadToYandex(action, from, to, printer, syncStatus)
            else -> throw CopyDirectionIsNotSupportedYet()
        }
    }

    override fun deleteFileOrFolder(path: String) {
        val notPermanently = false
        yandex.delete(path.toYandexPath(), notPermanently)
    }

    override fun moveFileOrFolder(fromPath: String, toPath: String) {
        yandex.move(fromPath.toYandexPath(), toPath.toYandexPath())
    }

    override fun close() {
        yandex.close()
    }

    override fun generateWebLink(path: String, rootPath: String): String {
        val baseUrl = "https://disk.yandex.ru/client/disk"

        val rRaw = rootPath.removePrefix("yandex://")
        val r = rRaw.replace(Regex("[/]+$"), "")
        val p = path.replace(Regex("^[/]+"), "")

        val fullPath = when {
            r.isEmpty() && p.isEmpty() -> "/"
            r.isEmpty() -> if (p.startsWith("/")) p else "/$p"
            p.isEmpty() -> if (r.startsWith("/")) r else "/$r"
            else -> {
                val rNorm = if (r.startsWith("/")) r else "/$r"
                "$rNorm/$p"
            }
        }

        val escapedPath = fullPath.split("/").joinToString("/") { part ->
            java.net.URLEncoder.encode(part, "UTF-8")
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%7E", "~")
                // Restore Russian characters: Cyrillic block is roughly covered by %D0 and %D1 in UTF-8
                .replace(Regex("(%D0%[89AB][0-9A-F]|%D1%8[0-9A-F])")) { match ->
                    val bytes = match.value.split("%").filter { it.isNotEmpty() }
                        .map { it.toInt(16).toByte() }.toByteArray()
                    String(bytes, Charsets.UTF_8)
                }
        }

        return "$baseUrl$escapedPath"
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////


    private fun JsonObject.toFileInfo(commonPrefix: String): FileInfo {
        val path = this.get("path").asString.removePrefix("disk:")
        val type = this.get("type").asString
        val isDir = type == "dir"
        val size = if (isDir) 10L else this.get("size")?.asLong ?: 0L
        val md5_ = this.get("md5")?.asString

        return FileInfo(
            name = path.removePrefix(commonPrefix).removePrefix("/"),
            isDirectory = isDir,
            size = size,
        ).apply { if(md5_ != null) setupMd5(md5_) }
    }

    private fun uploadToYandex(
        action: String,
        from: String,
        to: String,
        printer: WorkerPrinter,
        syncStatus: SyncStatus,
    ) {
        val sl = StatusListener(action, from, printer, syncStatus)
        try {
            yandex.uploadFile(File(from), to.toYandexPath(), sl::updateProgress)
            sl.printDone()
        } catch (e: Throwable) {
            sl.printProgress(e)
            Thread.sleep(2000)
            throw e
        }
    }

    fun downloadFile(from: String, to: File, onProgress: (Long, Long) -> Unit) {
        yandex.downloadFile(from.toYandexPath(), to, onProgress)
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
                printer.updateStatus("$styledAction $fileName $styledPct ${style("${Icons.CROSS} Error: ${err.toLog()}", Color.ERROR)}")
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

    private fun String.toYandexPath() = this.removePrefix("yandex://")

}