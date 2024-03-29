package tga.backup.files

import tga.backup.log.logWrap
import java.io.File

class LocalFileOps : FileOps {

    private val filesSeparator = File.separatorChar

    override fun getFilesSet(rootPath: String): Set<FileInfo> {
        if (!File(rootPath).exists()) return emptySet()
        return File(rootPath).listFilesRecursive(HashSet(), "")
    }

    fun copyFiles(srcFolder: String, filesList: Set<FileInfo>, dstFolder: String, dryRun: Boolean) {
        val sortedFilesList = filesList.sorted()

        for (fileInfo in sortedFilesList) {
            val srcFileOrFolder = File("${srcFolder}${filesSeparator}${fileInfo.name}")
            val dstFileOrFolder = File("${dstFolder}${filesSeparator}${fileInfo.name}")
            if (fileInfo.isDirectory) {
                logWrap("creating folder: ${dstFileOrFolder.path}...") {
                    if (!dryRun) dstFileOrFolder.mkdirs()
                }
            } else {
                logWrap("copying        : ${dstFileOrFolder.path}.........") {
                    if (!dryRun) srcFileOrFolder.copyTo(dstFileOrFolder)
                }
            }
        }
    }

    fun deleteFiles(filesList: Set<FileInfo>, dstFolder: String, dryRun: Boolean) {
        val sortedFilesList = filesList.sortedDescending()
        for (fileInfo in sortedFilesList) {
            val dstFileOrFolder = File("${dstFolder}${filesSeparator}${fileInfo.name}")
            val fType = if (dstFileOrFolder.isDirectory) "folder" else "file"

            logWrap("deleting $fType: ${dstFileOrFolder.path}...", eatErrors = true) {
                if (!dryRun) dstFileOrFolder.delete()
            }
        }
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

