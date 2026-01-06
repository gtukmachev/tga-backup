package tga.backup.files

import com.yandex.disk.rest.ProgressListener
import com.yandex.disk.rest.ResourcesArgs
import com.yandex.disk.rest.RestClient
import com.yandex.disk.rest.exceptions.http.HttpCodeException
import com.yandex.disk.rest.json.Resource
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Phaser
import java.util.concurrent.atomic.AtomicReference

class YandexFileOps(
    private val yandex: RestClient,
    val maxPageSize: Int  = 5000
) : FileOps(filesSeparator = "/") {

    private val logger = KotlinLogging.logger {  }

    override fun getFilesSet(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> {
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
                        val req = ResourcesArgs.Builder()
                            .setPath(path)
                            .setLimit(maxPageSize)
                            .setOffset(offset)
                            .setFields("name,type,path,size,md5,_embedded.items.name,_embedded.items.type,_embedded.items.path,_embedded.items.size,_embedded.items.md5,_embedded.total")
                            .build()

                        val resource = try {
                            yandex.getResources(req)
                        } catch (e: HttpCodeException) {
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

                        val resourceList = resource.resourceList
                        val items = resourceList?.items ?: emptyList()

                        items.forEach {
                            if (it.isDir) {
                                scan(it.path.path)
                            } else {
                                files.add(it.toFileInfo(fullRootPathPrefix))
                                printFilesSize()
                            }
                        }

                        if (items.size < maxPageSize || (resourceList != null && offset + items.size >= resourceList.total)) break
                        offset += items.size
                    }
                } catch (e: HttpCodeException) {
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
            } catch (e: HttpCodeException) {
                when {
                    (e.response.error == "DiskPathPointsToExistentDirectoryError") -> {
                        createdFolders.add(currentPath)
                    }
                    (e.code == 409 && e.response.error == "DiskPathPointsToExistentDirectoryError") -> {
                        createdFolders.add(currentPath)
                    }
                    else -> throw e
                }
            }
        }
    }

    override fun copyFile(from: String, to: String, srcFileOps: FileOps, override: Boolean) {
        when (srcFileOps) {
            is LocalFileOps -> uploadToYandex(from, to, override)
            else -> throw CopyDirectionIsNotSupportedYet()
        }
    }

    override fun deleteFileOrFolder(path: String) {
        val notPermanently = false
        yandex.delete(path.toYandexPath(), notPermanently)
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////


    private fun Resource.toFileInfo(commonPrefix: String): FileInfo {
        return FileInfo(
            name = this.path.path.removePrefix(commonPrefix).removePrefix("/"),
            isDirectory = this.isDir,
            size = if (this.isDir) 10L else this.size,
            md5 = this.md5
        )
    }

    private fun uploadToYandex(from: String, to: String, override: Boolean) {
        try {
            val uploadUrl = yandex.getUploadLink(to.toYandexPath(), override)
            yandex.uploadFile(uploadUrl, false, File(from), PrintStatusListener())
        } catch (e: Exception) {
            logger.error(e) {  }
        }
    }


    class PrintStatusListener : ProgressListener {
        private var previousLoaded: Float = 0.0f

        override fun updateProgress(loaded: Long, total: Long) {
            val loadedFloat = loaded.toFloat()
            val totalFloat = total.toFloat()
            val prcNow = loadedFloat / totalFloat
            val prcPrev = previousLoaded / totalFloat
            if ( (prcNow - prcPrev) >= 0.02) {
                previousLoaded = loadedFloat
                print(".")
            }
        }

        override fun hasCancelled(): Boolean = false // todo: implement gracefully cancellation
    }

    private fun String.toYandexPath() = this.removePrefix("yandex://")

}