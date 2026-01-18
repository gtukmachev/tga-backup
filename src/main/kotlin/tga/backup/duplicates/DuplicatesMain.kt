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
    
    val duplicateGroups = findDuplicates(files)
    val summary = DuplicatesSummary.from(duplicateGroups)
    
    val findDuration = System.currentTimeMillis() - startFind
    println("Duplicate detection completed in ${findDuration}ms")
    println()

    // Phase 3: Display results
    if (duplicateGroups.isEmpty()) {
        println("No duplicate files found!")
        println()
    } else {
        println("Phase 3: Duplicate files found")
        println("=".repeat(80))
        println()

        // Sort groups by wasted space (descending)
        val sortedGroups = duplicateGroups.values.sortedByDescending { it.wastedSpace }

        for ((index, group) in sortedGroups.withIndex()) {
            println("Duplicate Group #${index + 1}")
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

    // Phase 4: Summary
    println("=".repeat(80))
    println("SUMMARY")
    println("=".repeat(80))
    println("Total duplicate groups: ${summary.totalGroups}")
    println("Total duplicate files: ${summary.totalDuplicateFiles}")
    println("Total wasted space: ${formatFileSize(summary.totalWastedSpace)}")
    
    if (summary.largestGroup != null) {
        println()
        println("Largest duplicate group:")
        println("  MD5: ${summary.largestGroup.md5}")
        println("  Copies: ${summary.largestGroup.files.size}")
        println("  Wasted space: ${formatFileSize(summary.largestGroup.wastedSpace)}")
    }
    
    println("=".repeat(80))

    // Close file operations
    targetFileOps.close()
}
