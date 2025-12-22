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

    override fun getFilesSet(rootPath: String): Set<FileInfo> {
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
                            .setFields("name,type,path,size,_embedded.items.name,_embedded.items.type,_embedded.items.path,_embedded.items.size,_embedded.total")
                            .build()

                        val resource = yandex.getResources(req)

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

    override fun mkDirs(dirPath: String) {
        try {
            yandex.makeFolder(dirPath.toYandexPath())
        } catch (e: HttpCodeException) {
            when {
                (e.response.error == "DiskPathPointsToExistentDirectoryError") -> { /* dir already exists */ }
                else -> throw e
            }
        }
    }

    override fun copyFile(from: String, to: String, srcFileOps: FileOps) {
        when (srcFileOps) {
            is LocalFileOps -> uploadToYandex(from, to)
            else -> CopyDirectionIsNotSupportedYet()
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
            size = if (this.isDir) 10L else this.size
        )
    }

    private fun uploadToYandex(from: String, to: String) {
        try {
            val doNotOverride = false // `true` means "override", false - don't override
            val uploadUrl = yandex.getUploadLink(to.toYandexPath(), doNotOverride)
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