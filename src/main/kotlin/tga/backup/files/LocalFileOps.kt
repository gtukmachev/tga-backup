package tga.backup.files

import java.io.File
import java.security.MessageDigest

class LocalFileOps : FileOps("/") {


    override fun getFilesSet(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> {
        val rootFile = File(rootPath)
        if (!rootFile.exists()) {
            if (throwIfNotExist) throw RuntimeException("Source directory does not exist: $rootPath")
            return emptySet()
        }
        return rootFile.listFilesRecursive(HashSet(), "")
    }

    override fun mkDirs(dirPath: String) {
        File(dirPath).mkdirs()
    }

    override fun copyFile(action: String, from: String,  to: String, srcFileOps: FileOps, override: Boolean, updateStatus: (String) -> Unit) {
        when (srcFileOps) {
            is LocalFileOps -> File(from).copyTo(File(to), overwrite = true)
            else -> throw CopyDirectionIsNotSupportedYet()
        }
    }

    override fun deleteFileOrFolder(path: String) {
        File(path).delete()
    }

    private fun File.listFilesRecursive(outSet: MutableSet<FileInfo>, path: String): Set<FileInfo> {
        val content = this.listFiles() ?: emptyArray()
        content.forEach {
            outSet.add(
                FileInfo(
                    name = path + it.name,
                    isDirectory = it.isDirectory,
                    size = if (it.isDirectory) 10L else it.length(),
                    md5 = if (it.isDirectory) null else it.calculateMd5()
                )
            )
        }
        content.forEach { if (it.isDirectory) it.listFilesRecursive(outSet, path + it.name + filesSeparator) }
        return outSet
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

