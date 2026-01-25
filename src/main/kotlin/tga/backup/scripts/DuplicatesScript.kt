package tga.backup.scripts

import tga.backup.duplicates.DuplicatesSummary
import tga.backup.duplicates.findDuplicates
import tga.backup.files.loadTree
import tga.backup.log.formatFileSize
import tga.backup.params.Params

@Suppress("unused")
class DuplicatesScript(params: Params) : Script(params) {

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
        println("TGA BACKUP UTILITY - DUPLICATES MODE")
        println("=".repeat(80))
        println()
        println(effectiveParams)
        println()

        val (targetFileOps, files) = loadTree("Target", effectiveParams.targetFolder, effectiveParams, throwIfNotExist = true)

        try {
            // Report files with read errors
            val filesWithErrors = files.filter { it.readException != null }
            if (filesWithErrors.isNotEmpty()) {
                println("WARNING: ${filesWithErrors.size} files could not be read:")
                filesWithErrors.forEach { fileInfo ->
                    println("  - ${fileInfo.name}: ${fileInfo.readException?.message}")
                }
                println()
            }

            // Phase 2: Find duplicates
            println("Phase 2: Finding duplicates...")
            val startFind = System.currentTimeMillis()

            val duplicatesResult = findDuplicates(files)
            val summary = DuplicatesSummary.from(duplicatesResult)

            val findDuration = System.currentTimeMillis() - startFind
            println("Duplicate detection completed in ${findDuration}ms")
            println()

            // Phase 3: Display results
            if (duplicatesResult.folderGroups.isEmpty() && duplicatesResult.fileGroups.isEmpty()) {
                println("No duplicate files or folders found!")
                println()
            } else {
                println("Phase 3: Duplicate folders and files found")
                println("=".repeat(80))
                println()

                if (duplicatesResult.folderGroups.isNotEmpty()) {
                    println("DUPLICATE FOLDERS")
                    println("-".repeat(40))
                    for ((index, group) in duplicatesResult.folderGroups.withIndex()) {
                        println("Folder Duplicate Group #${index + 1}")
                        println("  Files in folder: ${group.filesCount}")
                        println("  Total folder size: ${formatFileSize(group.totalSize)}")
                        println("  Number of copies: ${group.folders.size}")
                        println("  Wasted space: ${formatFileSize(group.wastedSpace)}")
                        println("  Folders:")
                        for (folder in group.folders) {
                            val link = targetFileOps.generateWebLink(folder)
                            val linkStr = if (link != null) " ($link)" else ""
                            println("    - $folder$linkStr")
                        }
                        println()
                    }
                }

                if (duplicatesResult.partialFolderGroups.isNotEmpty()) {
                    println("PARTIAL DUPLICATE FOLDERS")
                    println("-".repeat(40))
                    for ((index, group) in duplicatesResult.partialFolderGroups.withIndex()) {
                        println("Partial Folder Duplicate Group #${index + 1}")
                        println("  Unique duplicate files: ${group.fileGroups.size}")
                        println("  Total duplicate files size: ${formatFileSize(group.totalDuplicateFilesSize)}")
                        println("  Wasted space: ${formatFileSize(group.wastedSpace)}")
                        println("  Folders:")
                        for (folder in group.folders) {
                            val marker = when {
                                folder.isOriginalCandidate -> " [ORIGINAL candidate]"
                                folder.isFullDuplicate -> " [ALL DUPLICATES]"
                                else -> ""
                            }
                            val link = targetFileOps.generateWebLink(folder.folderPath)
                            val linkStr = if (link != null) " ($link)" else ""
                            println("    - ${folder.folderPath} (contains ${folder.duplicateFilesCount} duplicates, total ${formatFileSize(folder.duplicateFilesSize)})$linkStr$marker")
                        }
                        println()
                    }
                }

                if (duplicatesResult.fileGroups.isNotEmpty()) {
                    println("DUPLICATE FILES")
                    println("-".repeat(40))
                    for ((index, group) in duplicatesResult.fileGroups.withIndex()) {
                        println("File Duplicate Group #${index + 1}")
                        println("  MD5: ${group.md5}")
                        println("  File size: ${formatFileSize(group.totalSize)}")
                        println("  Number of copies: ${group.files.size}")
                        println("  Wasted space: ${formatFileSize(group.wastedSpace)}")
                        println("  Files:")
                        for (file in group.files) {
                            println("    - ${file.name}")
                        }
                        println()
                    }
                }
            }

            // Phase 4: Summary
            println("=".repeat(80))
            println("SUMMARY")
            println("=".repeat(80))
            println("Total duplicate folder groups: ${summary.totalFolderGroups}")
            println("Total partial duplicate folder groups: ${summary.totalPartialFolderGroups}")
            println("Total duplicate file groups: ${summary.totalGroups}")
            println("Total wasted space: ${formatFileSize(summary.totalWastedSpace)}")

            if (summary.largestGroup != null) {
                println()
                println("Largest duplicate file group:")
                println("  MD5: ${summary.largestGroup.md5}")
                println("  Copies: ${summary.largestGroup.files.size}")
                println("  Wasted space: ${formatFileSize(summary.largestGroup.wastedSpace)}")
            }

            println("=".repeat(80))
        } finally {
            targetFileOps.close()
        }
    }
}
