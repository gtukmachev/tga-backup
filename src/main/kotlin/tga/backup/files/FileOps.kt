package tga.backup.files

import tga.backup.log.logWrap
import java.io.File

class FileOps {

    private val filesSeparator = File.separatorChar

    fun getFilesSet(root: String): Set<String> {
        if (!File(root).exists()) return emptySet()
        val outSet = HashSet<String>()
        return File(root).listFilesRecursive(outSet, "")
    }

    fun copyFiles(srcFolder: String, filesList: Iterable<String>, dstFolder: String, dryRun: Boolean) {
        val sortedFilesList = filesList.sorted()

        for (filePath in sortedFilesList) {
            val srcFileOrFolder = File("${srcFolder}${filesSeparator}${filePath}")
            val dstFileOrFolder = File("${dstFolder}${filesSeparator}${filePath}")
            if (srcFileOrFolder.isDirectory) {
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

    fun deleteFiles(filesList: Iterable<String>, dstFolder: String, dryRun: Boolean) {
        val sortedFilesList = filesList.sortedDescending()
        for (filePath in sortedFilesList) {
            val dstFileOrFolder = File("${dstFolder}${filesSeparator}${filePath}")
            val fType = if (dstFileOrFolder.isDirectory) "folder" else "file"

            logWrap("deleting $fType: ${dstFileOrFolder.path}...", eatErrors = true) {
                if (!dryRun) dstFileOrFolder.delete()
            }

        }
    }

    private fun File.listFilesRecursive(outSet: MutableSet<String>, path: String): Set<String> {
        val content = this.listFiles()!!
        content.forEach { outSet.add(path + it.name) }
        content.forEach { if (it.isDirectory) it.listFilesRecursive(outSet, path + it.name + filesSeparator) }
        return outSet
    }

}

