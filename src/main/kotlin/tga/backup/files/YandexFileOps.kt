package tga.backup.files

import com.yandex.disk.rest.ProgressListener
import com.yandex.disk.rest.ResourcesArgs
import com.yandex.disk.rest.RestClient
import com.yandex.disk.rest.exceptions.http.HttpCodeException
import com.yandex.disk.rest.json.Resource
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

class YandexFileOps(
    private val yandex: RestClient,
    val maxPageSize: Int  = 5000
) : FileOps(filesSeparator = "/") {

    private val logger = KotlinLogging.logger {  }

    override fun getFilesSet(rootPath: String): Set<FileInfo> {
        print("\nLoading files tree from yandex disk:")

        val fullRootPath = rootPath.removePrefix("yandex://")
        val fullRootPathPrefix = "/$fullRootPath"
        val files: MutableSet<FileInfo> = HashSet()
        val backspacesLine = "\b".repeat(12)
        val numLen = backspacesLine.length
        print(" " + " ".repeat(numLen))

        fun printFilesSize() {
            val filesNumberStr = "${files.size}".padEnd(numLen)
            print(backspacesLine)
            print(filesNumberStr)
        }

        fun readFilesSetRecursively(path: String) {
            val resource = getYandexFolderWithItemsInside(path) ?: return
            files += resource.toFileInfo(fullRootPathPrefix)
            printFilesSize()

            resource.resourceList.items.forEach {
                when {
                    it.isDir -> readFilesSetRecursively(it.path.path)
                    else -> {
                        files += it.toFileInfo(fullRootPathPrefix)
                        printFilesSize()
                    }
                }
            }
        }

        readFilesSetRecursively(fullRootPath)
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

    private fun getYandexFolderWithItemsInside(path: String): Resource? {
        val req = ResourcesArgs.Builder()
            .setPath(path)
            .setLimit(maxPageSize)
            .build()

        val yandexDiskItems = try {
            yandex.getResources(req)
        } catch (err: HttpCodeException) {
            when (err.code) {
                404 -> null
                else -> throw err
            }
        }

        if (yandexDiskItems.size == maxPageSize.toLong()) {
            throw Exception("Yandex disk folder contains more than $maxPageSize items")
        }

        return yandexDiskItems
    }

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