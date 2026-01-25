package tga.backup.scripts

import tga.backup.files.ExclusionMatcher
import tga.backup.files.FileInfo
import tga.backup.files.loadTree
import tga.backup.log.formatFileSize
import tga.backup.params.Params
import java.util.*

class DelOldDuplicatesScript(params: Params) : Script(params) {

    override fun run() {
        println("=".repeat(80))
        println("TGA BACKUP UTILITY - DELETE OLD DUPLICATES MODE")
        println("=".repeat(80))
        println()
        println(params)
        println()

        val (srcFileOps, srcFiles) = loadTree("Source", params.srcFolder, params, throwIfNotExist = true)
        val (dstFileOps, dstFiles) = loadTree("Destination", params.dstFolder, params, throwIfNotExist = true)

        try {
            val exclusionMatcher = ExclusionMatcher(params.exclude)
            
            // 1. Identify duplicates
            // Map destination files by Name (basename) and MD5 for quick lookup
            // Key: basename to Set of MD5s
            val dstMap = mutableMapOf<String, MutableSet<String>>()
            dstFiles.filter { !it.isDirectory && it.md5 != null }.forEach {
                val basename = it.name.substringAfterLast('/')
                dstMap.getOrPut(basename) { mutableSetOf() }.add(it.md5!!)
            }

            val toDeleteFiles = mutableSetOf<FileInfo>()
            val ignoredFiles = mutableSetOf<FileInfo>()
            val folders = mutableSetOf<FileInfo>()
            val filesToKeep = mutableSetOf<FileInfo>()

            for (file in srcFiles) {
                if (file.isDirectory) {
                    folders.add(file)
                    continue
                }

                val basename = file.name.substringAfterLast('/')
                if (exclusionMatcher.isExcluded(basename, file.name)) {
                    ignoredFiles.add(file)
                    continue
                }

                val isDuplicate = file.md5 != null && dstMap[basename]?.contains(file.md5) == true
                if (isDuplicate) {
                    toDeleteFiles.add(file)
                } else {
                    filesToKeep.add(file)
                }
            }

            // 2. Compaction logic
            // A folder can be deleted if it contains ONLY:
            // - files marked for deletion
            // - ignored files
            // - empty folders (or folders that will also be deleted)
            
            val filesToKeepPaths = filesToKeep.map { it.name }.toSet()
            
            // Helper function to check if a folder or any of its subfolders contains files to keep
            fun hasFilesToKeep(folderPath: String): Boolean {
                return filesToKeepPaths.any { it.startsWith(if (folderPath.isEmpty()) "" else "$folderPath/") }
            }

            val folderDeletionPlan = mutableSetOf<FileInfo>()
            val fileDeletionPlan = toDeleteFiles.toMutableSet()

            // Sort folders by length descending to process deepest folders first
            val sortedFolders = folders.sortedByDescending { it.name }
            val deletedFoldersPaths = mutableSetOf<String>()

            for (folder in sortedFolders) {
                if (!hasFilesToKeep(folder.name)) {
                    // All files in this folder (and subfolders) are either to be deleted or ignored.
                    folderDeletionPlan.add(folder)
                    deletedFoldersPaths.add(folder.name)
                    
                    // Remove files from this folder from the file deletion plan, 
                    // because they will be deleted with the folder.
                    fileDeletionPlan.removeIf { it.name.startsWith("${folder.name}/") }
                }
            }
            
            // Remove folders that are subfolders of already marked for deletion folders
            folderDeletionPlan.removeIf { folder ->
                deletedFoldersPaths.any { parentPath -> 
                    folder.name.startsWith("$parentPath/")
                }
            }

            if (fileDeletionPlan.isEmpty() && folderDeletionPlan.isEmpty()) {
                println("No duplicates found to delete.")
                return
            }

            // 3. Sorting & Printing Plan
            val finalPlan = (fileDeletionPlan + folderDeletionPlan).sortedBy { it.name }

            println("PLAN OF ACTIONS (to be deleted from SOURCE):")
            println("-".repeat(80))
            for (item in finalPlan) {
                val type = if (item.isDirectory) "[FOLDER]" else "[FILE]  "
                val size = if (item.isDirectory) "" else " (${formatFileSize(item.size)})"
                val link = srcFileOps.generateWebLink(item.name, params.srcFolder)
                val linkStr = if (link.isNotEmpty()) " -> $link" else ""
                println("  $type ${item.name}$size$linkStr")
            }
            println("-".repeat(80))
            println("Total items to delete: ${finalPlan.size}")
            println("Total size to free: ${formatFileSize(fileDeletionPlan.sumOf { it.size })}")
            println("-".repeat(80))

            if (params.dryRun) {
                println("Dry-run mode: no changes applied.")
                return
            }

            print("Do you want to proceed with deletion from SOURCE? (y/n): ")
            val scanner = Scanner(System.`in`)
            val response = scanner.next()

            if (response.lowercase() == "y") {
                println("Executing deletion from source...")
                srcFileOps.deleteFiles(finalPlan.toSet(), params.srcFolder, dryRun = false)
                println("Done.")
            } else {
                println("Operation cancelled.")
            }

        } finally {
            srcFileOps.close()
            dstFileOps.close()
        }
    }
}
