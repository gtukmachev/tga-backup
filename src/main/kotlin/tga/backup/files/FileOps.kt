package tga.backup.files

import tga.backup.log.logWrap

abstract class FileOps(
    protected val filesSeparator: String
) {
    // Interface part
    abstract fun getFilesSet(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> // platform specific

    fun copyFiles(srcFileOps: FileOps, srcFolder: String, filesList: Set<FileInfo>, dstFolder: String, dryRun: Boolean, override: Boolean = false) {
        val sortedFilesList = filesList.sorted()

        for (fileInfo in sortedFilesList) {
            val srcPath = "${srcFolder}${filesSeparator}${fileInfo.name}"
            val dstPath = "${dstFolder}${filesSeparator}${fileInfo.name}"
            if (fileInfo.isDirectory) {
                logWrap("creating folder: $dstPath") {
                    if (!dryRun) mkDirs(dstPath)
                }
            } else {
                val action = if (override) "overriding" else "copying   "
                logWrap("$action : $dstPath", eatErrors = true) {
                    if (!dryRun) copyFile(srcPath, dstPath, srcFileOps, override)
                }
            }
        }

    }

    fun deleteFiles(filesList: Set<FileInfo>, dstFolder: String, dryRun: Boolean) {
        val sortedFilesList = filesList.sortedDescending()
        for (fileInfo in sortedFilesList) {
            val dstPath = "${dstFolder}${filesSeparator}${fileInfo.name}"
            val fType = if (fileInfo.isDirectory) "folder" else "file"

            logWrap("deleting $fType: $dstPath...", eatErrors = true) {
                if (!dryRun) deleteFileOrFolder(dstPath)
            }
        }
    }

    // platform specific implementation part
    protected abstract fun mkDirs(dirPath: String)
    protected abstract fun copyFile(from: String,  to: String, srcFileOps: FileOps, override: Boolean)
    protected abstract fun deleteFileOrFolder(path: String)

}