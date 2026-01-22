package tga.backup.duplicates

import tga.backup.files.buildFileOpsByURL
import tga.backup.log.formatFileSize
import tga.backup.params.Params

fun runDuplicatesMode(params: Params) {
    println("=".repeat(80))
    println("TGA BACKUP UTILITY - DUPLICATES MODE")
    println("=".repeat(80))
    println()
    println(params)
    println()

    // Phase 1: Scan target directory
    println("Phase 1: Scanning target directory...")
    val startScan = System.currentTimeMillis()
    
    val targetFileOps = buildFileOpsByURL(params.targetFolder, params)
    val files = targetFileOps.getFilesSet(params.targetFolder, throwIfNotExist = true)
    
    val scanDuration = System.currentTimeMillis() - startScan
    println("Scan completed in ${scanDuration}ms")
    println("Total files found: ${files.count { !it.isDirectory }}")
    println("Total directories found: ${files.count { it.isDirectory }}")
    println()

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
                    println("    - $folder")
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

    // Close file operations
    targetFileOps.close()
}
