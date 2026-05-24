package tga.backup.files

import com.google.api.services.drive.model.File as GDriveFile
import io.github.oshai.kotlinlogging.KotlinLogging
import tga.backup.gdrive.GDriveClient
import tga.backup.gdrive.GDriveResponseException
import tga.backup.log.formatFileSize
import tga.backup.log.formatNumber
import tga.backup.log.toLog
import tga.backup.utils.ConsoleMultiThreadWorkers
import tga.backup.utils.DynamicTask
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class GDriveFileOps(
    private val gdrive: GDriveClient,
    val profile: String,
    val useCache: Boolean,
    excludePatterns: List<String> = emptyList()
) : FileOps(filesSeparator = "/", excludePatterns) {

    private val logger = KotlinLogging.logger {}

    private val pathToIdMap = ConcurrentHashMap<String, String>()

    override fun getFilesSet(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> {
        val cacheFilePath = getCacheFilePath(profile, rootPath)
        if (useCache) {
            readRemoteCache(cacheFilePath)?.let { return it }
        }

        val files = scanGDrive(rootPath, throwIfNotExist)
        writeRemoteCacheIfChanged(cacheFilePath, files)

        return files
    }

    private fun scanGDrive(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> {
        println("\nLoading files tree from Google Drive:")

        val cleanPath = rootPath.removePrefix("gdrive://")
        val files = ConcurrentHashMap.newKeySet<FileInfo>()
        val totalSize = AtomicLong(0)

        val rootFolderId = try {
            gdrive.resolvePathToId(cleanPath)
        } catch (e: GDriveResponseException) {
            if (throwIfNotExist) throw RuntimeException("Source directory does not exist: $rootPath", e)
            return emptySet()
        }

        pathToIdMap[cleanPath] = rootFolderId

        val workers = ConsoleMultiThreadWorkers<Unit>(20)

        fun updateGlobalLine(updateGlobalStatus: (String) -> Unit) {
            val count = files.size
            val size = totalSize.get()
            updateGlobalStatus("Scanning GDrive: ${formatNumber(count.toLong())} files [${formatFileSize(size)}]")
        }

        fun scanFolder(
            folderId: String,
            relativePath: String,
            updateStatus: (String) -> Unit,
            updateGlobalStatus: (String) -> Unit,
            submitChild: (DynamicTask<Unit>) -> Unit
        ) {
            var pageToken: String? = null
            do {
                val shortPath = relativePath.ifEmpty { "/" }
                updateStatus("Fetching: $shortPath")
                logger.debug { "Fetching folder: $relativePath (folderId: $folderId)" }

                val (items, nextToken) = gdrive.listFiles(folderId, pageToken)

                for (item in items) {
                    val itemName = item.name
                    val itemRelPath = if (relativePath.isEmpty()) itemName else "$relativePath/$itemName"

                    if (isExcluded(itemName, itemRelPath)) continue

                    if (GDriveClient.isGoogleNativeFile(item.mimeType)) {
                        logger.warn { "Skipping Google native file (${item.mimeType}): $itemRelPath" }
                        continue
                    }

                    val isDir = item.mimeType == GDriveClient.FOLDER_MIME_TYPE
                    val size = if (isDir) 10L else (item.getSize() ?: 0L)

                    val fileInfo = FileInfo(
                        name = itemRelPath,
                        isDirectory = isDir,
                        size = size,
                    )

                    if (!isDir) {
                        item.md5Checksum?.let { fileInfo.setupMd5(it) }
                    }

                    files.add(fileInfo)
                    totalSize.addAndGet(size)
                    val fullItemPath = if (cleanPath.isEmpty()) itemRelPath else "$cleanPath/$itemRelPath"
                    pathToIdMap[fullItemPath] = item.id
                    updateGlobalLine(updateGlobalStatus)

                    if (isDir) {
                        submitChild(DynamicTask { childStatus, childGlobal, childSubmit ->
                            scanFolder(item.id, itemRelPath, childStatus, childGlobal, childSubmit)
                        })
                    }
                }

                pageToken = nextToken
            } while (pageToken != null)

            updateStatus("")
        }

        workers.submitDynamic { updateStatus, updateGlobalStatus, submitChild ->
            scanFolder(rootFolderId, "", updateStatus, updateGlobalStatus, submitChild)
        }

        workers.awaitDynamic()
        workers.shutdown()

        println("...done\n")

        return files
    }

    private val createdFolders = ConcurrentHashMap<String, String>()

    override fun mkDirs(dirPath: String) {
        val path = dirPath.toGDrivePath()
        if (path.isEmpty()) return

        val parts = path.split("/").filter { it.isNotEmpty() }
        var currentPath = ""
        var parentId = pathToIdMap[getRootFromPath(dirPath)] ?: "root"

        for (part in parts) {
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

            val existingId = createdFolders[currentPath] ?: pathToIdMap[currentPath]
            if (existingId != null) {
                parentId = existingId
                continue
            }

            val folder = gdrive.createFolder(part, parentId)
            createdFolders[currentPath] = folder.id
            pathToIdMap[currentPath] = folder.id
            parentId = folder.id
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
            is LocalFileOps -> uploadToGDrive(action, from, to, updateStatus, syncStatus)
            else -> throw CopyDirectionIsNotSupportedYet()
        }
    }

    private fun uploadToGDrive(
        action: String,
        from: String,
        to: String,
        updateStatus: (String) -> Unit,
        syncStatus: SyncStatus,
    ) {
        val sl = StatusListener(action, from, updateStatus, syncStatus)
        try {
            val cleanTo = to.toGDrivePath()
            val parentPath = cleanTo.substringBeforeLast("/", "")
            val parentId = if (parentPath.isEmpty()) "root"
                else pathToIdMap[parentPath] ?: gdrive.resolvePathToId(parentPath)

            gdrive.uploadFile(File(from), parentId, sl::updateProgress)
            sl.printDone()
        } catch (e: Throwable) {
            sl.printProgress(e)
            Thread.sleep(2000)
            throw e
        }
    }

    fun downloadFile(from: String, toFile: File, onProgress: (Long, Long) -> Unit) {
        val cleanPath = from.toGDrivePath()
        val fileId = pathToIdMap[cleanPath] ?: gdrive.resolvePathToId(cleanPath)
        val metadata = gdrive.getFileMetadata(fileId)
        val fileSize = metadata.getSize() ?: 0L
        toFile.outputStream().use { outputStream ->
            gdrive.downloadFile(fileId, outputStream, fileSize, onProgress)
        }
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
            val shortName = if (fileName.length > fileNameLen) ("..." + fileName.takeLast(fileNameLen - 3)) else fileName.padEnd(fileNameLen)
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

    override fun deleteFileOrFolder(path: String) {
        val cleanPath = path.toGDrivePath()
        val fileId = pathToIdMap[cleanPath]
            ?: gdrive.resolvePathToId(cleanPath)
        gdrive.deleteFile(fileId)
    }

    override fun moveFileOrFolder(fromPath: String, toPath: String) {
        val cleanFrom = fromPath.toGDrivePath()
        val cleanTo = toPath.toGDrivePath()

        val fileId = pathToIdMap[cleanFrom]
            ?: gdrive.resolvePathToId(cleanFrom)

        val fromParent = cleanFrom.substringBeforeLast("/", "")
        val toParent = cleanTo.substringBeforeLast("/", "")
        val newName = cleanTo.substringAfterLast("/")

        val fromParentId = if (fromParent.isEmpty()) "root" else pathToIdMap[fromParent] ?: gdrive.resolvePathToId(fromParent)
        val toParentId = if (toParent.isEmpty()) "root" else pathToIdMap[toParent] ?: gdrive.resolvePathToId(toParent)

        if (fromParentId == toParentId) {
            gdrive.renameFile(fileId, newName)
        } else {
            gdrive.moveFile(fileId, fromParentId, toParentId, newName)
        }

        pathToIdMap.remove(cleanFrom)
        pathToIdMap[cleanTo] = fileId
    }

    override fun close() {
        gdrive.close()
    }

    override fun generateWebLink(path: String, rootPath: String): String {
        val rootClean = rootPath.removePrefix("gdrive://").replace(Regex("[/]+$"), "")
        val pathClean = path.replace(Regex("^[/]+"), "")

        val fullPath = when {
            rootClean.isEmpty() && pathClean.isEmpty() -> ""
            rootClean.isEmpty() -> pathClean
            pathClean.isEmpty() -> rootClean
            else -> "$rootClean/$pathClean"
        }

        val fileId = pathToIdMap[fullPath]
        if (fileId != null) {
            return "https://drive.google.com/drive/folders/$fileId"
        }
        return "https://drive.google.com/drive/"
    }

    fun getFileId(relativePath: String): String? = pathToIdMap[relativePath]

    fun getRootFolderId(rootPath: String): String {
        val cleanPath = rootPath.removePrefix("gdrive://")
        return pathToIdMap[cleanPath] ?: "root"
    }

    private fun getRootFromPath(fullPath: String): String {
        val clean = fullPath.toGDrivePath()
        return pathToIdMap.keys.firstOrNull { clean.startsWith("$it/") || clean == it } ?: ""
    }

    private fun String.toGDrivePath() = this.removePrefix("gdrive://")
}
