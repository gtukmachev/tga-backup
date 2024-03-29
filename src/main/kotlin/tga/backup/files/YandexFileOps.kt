package tga.backup.files

import com.yandex.disk.rest.ProgressListener
import com.yandex.disk.rest.ResourcesArgs
import com.yandex.disk.rest.RestClient
import com.yandex.disk.rest.exceptions.http.HttpCodeException
import com.yandex.disk.rest.json.Resource
import java.io.File

class YandexFileOps(
    private val yandex: RestClient
) : FileOps(filesSeparator = "/") {

    override fun getFilesSet(rootPath: String): Set<FileInfo> {
        val files: MutableSet<FileInfo> = HashSet()

        fun readFilesSetRecursively(path: String) {
            val resource = getYandexFolderWithItemsInside(path) ?: return
            files += resource.toFileInfo()

            resource.resourceList.items.forEach {
                when {
                    it.isDir -> readFilesSetRecursively(it.path.path)
                    else -> files += it.toFileInfo()
                }
            }
        }

        readFilesSetRecursively(rootPath.removePrefix("yandex://"))

        return files
    }

    override fun mkDirs(dirPath: String) {
        TODO("Not yet implemented")
    }

    override fun copyFile(from: String, to: String, srcFileOps: FileOps) {
        when (srcFileOps) {
            is LocalFileOps -> uploadToYandex(from, to, srcFileOps)
            else -> CopyDirectionIsNotSupportedYet()
        }
    }

    override fun deleteFileOrFolder(path: String) {
        val notPermanently = false
        yandex.delete(path, notPermanently)
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun getYandexFolderWithItemsInside(path: String): Resource? {
        val req = ResourcesArgs.Builder()
            .setPath(path)
            .setLimit(5000)
            .build()

        return try {
            yandex.getResources(req)
        } catch (err: HttpCodeException) {
            when (err.code) {
                404 -> null
                else -> throw err
            }
        }
    }

    private fun Resource.toFileInfo(): FileInfo {
        return FileInfo(this.path.path, this.isDir, this.size)
    }

    private fun uploadToYandex(from: String, to: String, srcFileOps: LocalFileOps) {
        val doNotOverride = false // `true` means "override", false - don't override
        val uploadUrl = yandex.getUploadLink(to, doNotOverride)
        yandex.uploadFile(uploadUrl, false, File(from), PrintStatusListener())
    }


    class PrintStatusListener : ProgressListener {
        private var previousLoaded: Float = 0.0f

        override fun updateProgress(loaded: Long, total: Long) {
            val loadedFloat = loaded.toFloat()
            val totalFloat = total.toFloat()
            val prcNow = loadedFloat / totalFloat
            val prcPrev = previousLoaded / totalFloat
            if ( (prcNow - prcPrev) >= 2.0) print(".")
        }

        override fun hasCancelled(): Boolean = false // todo: implement gracefully cancellation
    }

}