package tga.backup.scripts

import tga.backup.files.ExclusionMatcher
import tga.backup.files.FileInfo
import tga.backup.files.loadTree
import tga.backup.log.formatFileSize
import tga.backup.params.Params
import java.util.*

class CleanupScript(params: Params) : Script(params) {

    private val effectiveParams: Params

    init {
        val targetRoot = when (params.target) {
            "src" -> params.srcRoot
            "dst" -> params.dstRoot
            else -> throw IllegalArgumentException("Invalid target: ${params.target}. Must be 'src' or 'dst'")
        }
        effectiveParams = if (targetRoot.isBlank()) {
            val defaultRoot = System.getProperty("user.dir")
            params.copy(
                srcRoot = if (params.target == "src") defaultRoot else params.srcRoot,
                dstRoot = if (params.target == "dst") defaultRoot else params.dstRoot
            )
        } else {
            params
        }
    }

    override fun run() {
        println("=".repeat(80))
        println("TGA BACKUP UTILITY - CLEANUP MODE")
        println("=".repeat(80))
        println()
        println(effectiveParams)
        println()

        val (targetFileOps, files) = loadTree("Target", effectiveParams.targetFolder, effectiveParams, throwIfNotExist = true)

        try {
            val exclusionMatcher = ExclusionMatcher(effectiveParams.exclude)
            
            val ignoredFiles = mutableSetOf<FileInfo>()
            val folders = mutableSetOf<FileInfo>()
            val validFiles = mutableSetOf<FileInfo>()

            for (file in files) {
                if (file.isDirectory) {
                    folders.add(file)
                } else {
                    val baseName = file.name.substringAfterLast('/')
                    if (exclusionMatcher.isExcluded(baseName, file.name)) {
                        ignoredFiles.add(file)
                    } else {
                        validFiles.add(file)
                    }
                }
            }

            // Identify empty folders: a folder is empty if it has no valid files (directly or indirectly)
            // and all its subfolders are also empty.
            // Or simply: a folder is empty if it contains only ignored files (directly or indirectly).
            
            val validFilePaths = validFiles.map { it.name }
            val foldersToKeep = mutableSetOf<String>()
            
            for (filePath in validFilePaths) {
                var parent = filePath.substringBeforeLast('/', "")
                while (parent.isNotEmpty()) {
                    foldersToKeep.add(parent)
                    parent = parent.substringBeforeLast('/', "")
                }
            }
            
            val emptyFolders = folders.filter { it.name !in foldersToKeep }.toSet()
            
            if (ignoredFiles.isEmpty() && emptyFolders.isEmpty()) {
                println("No ignored files or empty folders found.")
                return
            }

            println("PLAN OF ACTIONS:")
            println("-".repeat(80))
            if (ignoredFiles.isNotEmpty()) {
                println("Ignored files to delete (${ignoredFiles.size}, total size: ${formatFileSize(ignoredFiles.sumOf { it.size })}):")
                ignoredFiles.sortedBy { it.name }.forEach { println("  [FILE]   ${it.name}") }
            }
            if (emptyFolders.isNotEmpty()) {
                println("Empty folders to delete (${emptyFolders.size}):")
                emptyFolders.sortedByDescending { it.name }.forEach { println("  [FOLDER] ${it.name}") }
            }
            println("-".repeat(80))

            if (effectiveParams.dryRun) {
                println("Dry-run mode: no changes applied.")
                return
            }

            print("Do you want to proceed with deletion? (y/n): ")
            val scanner = Scanner(System.`in`)
            val response = scanner.next()

            if (response.lowercase() == "y") {
                println("Executing deletion...")
                
                // 1. Delete ignored files
                if (ignoredFiles.isNotEmpty()) {
                    println("Deleting ignored files...")
                    targetFileOps.deleteFiles(ignoredFiles, effectiveParams.targetFolder, dryRun = false)
                }
                
                // 2. Delete empty folders (must be sorted descending to delete children before parents)
                if (emptyFolders.isNotEmpty()) {
                    println("Deleting empty folders...")
                    targetFileOps.deleteFiles(emptyFolders, effectiveParams.targetFolder, dryRun = false)
                }
                
                println("Done.")
            } else {
                println("Operation cancelled.")
            }

        } finally {
            targetFileOps.close()
        }
    }
}
