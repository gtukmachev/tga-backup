package tga.backup.files

import java.io.File

class FileOps {

    private val filesSeparator = File.separatorChar

    fun getFilesSet(root: String): Set<String> {
        if (!File(root).exists()) return emptySet()
        val outSet = HashSet<String>()
        return File(root).listFilesRecursive(outSet, "")
    }

    fun copyFiles(srcFolder: String, filesList: Set<String>, dstFolder: String, dryRun: Boolean) {
        val sortedFilesList = filesList.sorted()

        for (filePath in sortedFilesList) {
            val srcFileOrFolder = File("${srcFolder}${filesSeparator}${filePath}")
            val dstFileOrFolder = File("${dstFolder}${filesSeparator}${filePath}")
            if (srcFileOrFolder.isDirectory) {
                print("creating folder: ${dstFileOrFolder.path}...")
                if (!dryRun) dstFileOrFolder.mkdirs()
                println("ok")
            } else {
                print("copying        : ${dstFileOrFolder.path}.........")
                if (!dryRun) srcFileOrFolder.copyTo(dstFileOrFolder)
                println("ok")
            }
        }
    }

    fun deleteFiles(filesList: Set<String>, dstFolder: String, dryRun: Boolean) {
        val sortedFilesList = filesList.sortedDescending()
        for (filePath in sortedFilesList) {
            val dstFileOrFolder = File("${dstFolder}${filesSeparator}${filePath}")
            val fType = if (dstFileOrFolder.isDirectory) "folder" else "file"

            print("deleting $fType: ${dstFileOrFolder.path}...")
            try {
                if (!dryRun) dstFileOrFolder.delete()
                print("...ok")
            } catch (t: Throwable) {
                print("...${t.toLog()}")
            } finally {
                println()
            }
        }
    }

    private fun Throwable.toLog() = "${this::class.java.simpleName}: '${this.message}'"

    private fun File.listFilesRecursive(outSet: MutableSet<String>, path: String): Set<String> {
        val content = this.listFiles()!!
        content.forEach { outSet.add(path + it.name) }
        content.forEach { if (it.isDirectory) it.listFilesRecursive(outSet, path + it.name + filesSeparator) }
        return outSet
    }

}

