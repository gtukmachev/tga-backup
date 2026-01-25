package tga.backup.files

import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import tga.backup.log.formatFileSize
import tga.backup.log.toLog
import tga.backup.yandex.YandexResponseException
import tga.backup.yandex.YandexResumableUploader
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicReference

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
        print("\nLoading files tree from yandex disk:")

        val fullRootPath = rootPath.removePrefix("yandex://")
        val fullRootPathPrefix = "/$fullRootPath"
        val files = ConcurrentHashMap.newKeySet<FileInfo>()
        val backspacesLine = "\b".repeat(12)
        val numLen = backspacesLine.length
        print(" " + " ".repeat(numLen))

        val printLock = Any()
        fun printFilesSize() {
            synchronized(printLock) {
                val filesNumberStr = "${files.size}".padEnd(numLen)
                print(backspacesLine)
                print(filesNumberStr)
            }
        }

        val executor = Executors.newFixedThreadPool(20)
        val phaser = Phaser(1)
        val error = AtomicReference<Throwable?>(null)

        fun scan(path: String) {
            phaser.register()
            executor.execute {
                try {
                    var offset = 0
                    while (error.get() == null) {
                        logger.debug { "Fetching folder metadata: $path (offset: $offset)" }

                        val resource = try {
                            yandex.getResources(path, maxPageSize, offset)
                        } catch (e: YandexResponseException) {
                            if (path == fullRootPath && e.code == 404) {
                                if (throwIfNotExist) throw RuntimeException("Source directory does not exist: $path", e)
                                return@execute
                            }
                            if (e.code != 404) error.compareAndSet(null, e)
                            null
                        }

                        if (resource == null) break

                        if (offset == 0) {
                            val fileInfo = resource.toFileInfo(fullRootPathPrefix)
                            if (fileInfo.name.isNotEmpty()) {
                                files.add(fileInfo)
                                printFilesSize()
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
                                scan(itemPath)
                            } else {
                                files.add(item.toFileInfo(fullRootPathPrefix))
                                printFilesSize()
                            }
                        }

                        val itemsCount = items.size
                        if (itemsCount < maxPageSize || (offset + itemsCount).toLong() >= total.toLong()) break
                        offset += itemsCount
                    }
                } catch (e: YandexResponseException) {
                    if (e.code != 404) error.compareAndSet(null, e)
                } catch (e: Throwable) {
                    error.compareAndSet(null, e)
                } finally {
                    phaser.arriveAndDeregister()
                }
            }
        }

        scan(fullRootPath)
        phaser.arriveAndAwaitAdvance()
        executor.shutdown()
        executor.awaitTermination(1, java.util.concurrent.TimeUnit.MINUTES)

        error.get()?.let { throw it }
        println(" ...done")

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
        updateStatus: (String) -> Unit,
        syncStatus: SyncStatus,
    ) {
        when (srcFileOps) {
            is LocalFileOps -> uploadToYandex(action, from, to, updateStatus, syncStatus)
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

    override fun generateWebLink(path: String): String {
        val baseUrl = "https://disk.yandex.ru/client/disk"
        val normalizedPath = if (path.startsWith("/")) path else "/$path"

        val escapedPath = normalizedPath.split("/").joinToString("/") { part ->
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
        updateStatus: (String) -> Unit,
        syncStatus: SyncStatus,
    ) {
        val sl = StatusListener(action, from, updateStatus, syncStatus)
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
        val updateStatus: (String) -> Unit,
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
            val dotsCount = (prc * 90).toInt()
            var progressBar = ".".repeat(dotsCount).padEnd(90)

            val prediction = speedCalculator.predict(lastTotal)
            if (prediction != null) {
                progressBar = prediction + progressBar.substring(prediction.length)
            }

            if (isDone) progressBar += " DONE "

            val fileNameLen = 50
            val shortName = if (fileName.length > fileNameLen) ("..."+fileName.takeLast(fileNameLen-3)) else fileName.padEnd(fileNameLen)
            val percentStr = "%6.2f".format(prc * 100)
            val speedStr = formatFileSize(speedCalculator.getSpeed()).padStart(7)

            val status = if (err == null) {
                "$action $shortName [$percentStr% $speedStr/s $progressBar]"
            } else {
                "$action $shortName [$percentStr%] Error: ${err.toLog()}"
            }
            updateStatus(status)

            syncStatus.formatProgress()
        }
    }

    private fun String.toYandexPath() = this.removePrefix("yandex://")

}