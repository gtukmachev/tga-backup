package tga.backup.files

import java.io.File

class LocalFileOps : FileOps("/") {


    override fun getFilesSet(rootPath: String): Set<FileInfo> {
        if (!File(rootPath).exists()) return emptySet()
        return File(rootPath).listFilesRecursive(HashSet(), "")
    }

    override fun mkDirs(dirPath: String) {
        File(dirPath).mkdirs()
    }

    override fun copyFile(from: String,  to: String, srcFileOps: FileOps) {
        when (srcFileOps) {
            is LocalFileOps -> File(from).copyTo(File(to))
            else -> throw CopyDirectionIsNotSupportedYet()
        }
    }

    override fun deleteFileOrFolder(path: String) {
        File(path).delete()
    }

    private fun File.listFilesRecursive(outSet: MutableSet<FileInfo>, path: String): Set<FileInfo> {
        val content = this.listFiles()!!
        content.forEach {
            outSet.add(
                FileInfo(
                    name = path + it.name,
                    isDirectory = it.isDirectory,
                    size = if (it.isDirectory) 10 else it.length()
                )
            )
        }
        content.forEach { if (it.isDirectory) it.listFilesRecursive(outSet, path + it.name + filesSeparator) }
        return outSet
    }

}

