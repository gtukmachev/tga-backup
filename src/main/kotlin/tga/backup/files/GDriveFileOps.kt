package tga.backup.files

import com.google.api.services.drive.model.File as GDriveFile
import io.github.oshai.kotlinlogging.KotlinLogging
import tga.backup.gdrive.GDriveClient
import tga.backup.gdrive.GDriveResponseException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
        print("\nLoading files tree from Google Drive:")

        val cleanPath = rootPath.removePrefix("gdrive://")
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

        val rootFolderId = try {
            gdrive.resolvePathToId(cleanPath)
        } catch (e: GDriveResponseException) {
            if (throwIfNotExist) throw RuntimeException("Source directory does not exist: $rootPath", e)
            return emptySet()
        }

        pathToIdMap[cleanPath] = rootFolderId

        val executor = Executors.newFixedThreadPool(20)
        val phaser = Phaser(1)
        val error = AtomicReference<Throwable?>(null)

        fun scan(folderId: String, relativePath: String) {
            phaser.register()
            executor.execute {
                try {
                    var pageToken: String? = null
                    do {
                        if (error.get() != null) break

                        logger.debug { "Fetching folder: $relativePath (folderId: $folderId)" }

                        val (items, nextToken) = try {
                            gdrive.listFiles(folderId, pageToken)
                        } catch (e: GDriveResponseException) {
                            error.compareAndSet(null, e)
                            break
                        }

                        for (item in items) {
                            val itemName = item.name
                            val itemRelPath = if (relativePath.isEmpty()) itemName else "$relativePath/$itemName"

                            if (isExcluded(itemName, itemRelPath)) continue

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
                            pathToIdMap[itemRelPath] = item.id
                            printFilesSize()

                            if (isDir) {
                                scan(item.id, itemRelPath)
                            }
                        }

                        pageToken = nextToken
                    } while (pageToken != null)
                } catch (e: Throwable) {
                    error.compareAndSet(null, e)
                } finally {
                    phaser.arriveAndDeregister()
                }
            }
        }

        scan(rootFolderId, "")
        phaser.arriveAndAwaitAdvance()
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.MINUTES)

        error.get()?.let { throw it }
        println(" ...done")

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
        throw CopyDirectionIsNotSupportedYet()
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
