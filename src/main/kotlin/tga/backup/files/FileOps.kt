package tga.backup.files

import tga.backup.log.logWrap
import tga.backup.utils.ConsoleMultiThreadWorkers

abstract class FileOps(
    protected val filesSeparator: String
) {
    // Interface part
    abstract fun getFilesSet(rootPath: String, throwIfNotExist: Boolean): Set<FileInfo> // platform specific

    fun createFolders(filesList: Set<FileInfo>, dstFolder: String, dryRun: Boolean) {
        val sortedFoldersList = filesList.filter { it.isDirectory }.sorted()

        for (fileInfo in sortedFoldersList) {
            val dstPath = "${dstFolder}${filesSeparator}${fileInfo.name}"
            logWrap("creating folder: $dstPath") {
                if (!dryRun) mkDirs(dstPath)
            }
        }
    }

    fun copyFiles(srcFileOps: FileOps, srcFolder: String, filesList: Set<FileInfo>, dstFolder: String, dryRun: Boolean, override: Boolean = false, workers: ConsoleMultiThreadWorkers<Unit>? = null) {
        val sortedFilesList = filesList.filter { !it.isDirectory }.sorted()

        for (fileInfo in sortedFilesList) {
            val srcPath = "${srcFolder}${filesSeparator}${fileInfo.name}"
            val dstPath = "${dstFolder}${filesSeparator}${fileInfo.name}"
            val action = if (override) "overriding" else "copying   "

            if (workers == null) {
                logWrap("$action : $dstPath", eatErrors = true) {
                    if (!dryRun) copyFile(srcPath, dstPath, srcFileOps, override) {}
                }
            } else {
                workers.submit { updateStatus ->
                    if (!dryRun) copyFile(srcPath, dstPath, srcFileOps, override, updateStatus)
                }
            }
        }
        workers?.waitForCompletion()

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
    protected abstract fun copyFile(from: String,  to: String, srcFileOps: FileOps, override: Boolean, updateStatus: (String) -> Unit)
    protected abstract fun deleteFileOrFolder(path: String)

}