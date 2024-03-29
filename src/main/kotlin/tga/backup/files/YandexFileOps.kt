package tga.backup.files

import com.yandex.disk.rest.ResourcesArgs
import com.yandex.disk.rest.RestClient
import com.yandex.disk.rest.exceptions.http.HttpCodeException
import com.yandex.disk.rest.json.Resource

class YandexFileOps(
    private val yandex: RestClient
) : FileOps {

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

}