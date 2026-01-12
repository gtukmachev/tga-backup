package tga.backup.files

data class SyncActionCases(
    val toAddFiles: Set<FileInfo>,
    val toDeleteFiles: Set<FileInfo>,
    val toOverrideFiles: Set<FileInfo>,
    val toMoveFiles: Set<Pair<FileInfo, String>> = emptySet(), // (sourceInfoInDst, targetRelativeName)
    val toRenameFiles: Set<Pair<FileInfo, String>> = emptySet(),
    val toMoveFolders: Set<Pair<FileInfo, String>> = emptySet(),
    val toRenameFolders: Set<Pair<FileInfo, String>> = emptySet(),
)

fun compareSrcAndDst(srcFiles: Set<FileInfo>, dstFiles: Set<FileInfo>, excludePatterns: List<String> = emptyList()): SyncActionCases {
    val excludeRegexes = excludePatterns.map { Regex(it) }
    fun isExcluded(fileName: String): Boolean {
        val baseName = fileName.substringAfterLast('/')
        return excludeRegexes.any { it.matches(baseName) }
    }
    val srcFilesFiltered = srcFiles.filter { it.readException == null }

    val srcFileNames = srcFilesFiltered.map { it.name }.toSet()
    val allSrcFileNames = srcFiles.map { it.name }.toSet()
    val dstFileNames = dstFiles.map { it.name }.toSet()

    val initialToAddNames = srcFileNames - dstFileNames
    val initialToDeleteNames = dstFileNames - allSrcFileNames
    val toOverrideNames = srcFileNames.intersect(dstFileNames)

    val initialToAdd = srcFilesFiltered.filter { it.name in initialToAddNames }.toSet()
    val initialToDelete = dstFiles.filter { it.name in initialToDeleteNames }.toSet()

    val srcFilesMap = srcFilesFiltered.associateBy { it.name }
    val dstFilesMap = dstFiles.associateBy { it.name }
    val toOverride = toOverrideNames
        .filter { srcFilesMap[it] != dstFilesMap[it] }
        .map { srcFilesMap[it]!! }
        .toSet()

    // 1. Detect file moves/renames
    val toAddFiles = initialToAdd.toMutableSet()
    val toDeleteFiles = initialToDelete.toMutableSet()

    val toMoveFiles = mutableSetOf<Pair<FileInfo, String>>()
    val toRenameFiles = mutableSetOf<Pair<FileInfo, String>>()

    val deletedFilesByMd5AndSize = toDeleteFiles.filter { !it.isDirectory && it.md5 != null }
        .groupBy { it.md5!! to it.size }
        .mapValues { it.value.toMutableList() }

    val addedFiles = toAddFiles.filter { !it.isDirectory && it.md5 != null }.sortedBy { it.name }

    for (srcFile in addedFiles) {
        val key = srcFile.md5!! to srcFile.size
        val candidates = deletedFilesByMd5AndSize[key]
        if (candidates != null && candidates.isNotEmpty()) {
            // Find best candidate: 1. Same name, 2. First available
            val bestMatch = candidates.find { it.name.substringAfterLast('/') == srcFile.name.substringAfterLast('/') }
                ?: candidates.first()

            candidates.remove(bestMatch)
            toAddFiles.remove(srcFile)
            toDeleteFiles.remove(bestMatch)

            if (bestMatch.name.substringAfterLast('/') == srcFile.name.substringAfterLast('/') ) {
                toMoveFiles.add(bestMatch to srcFile.name)
            } else {
                toRenameFiles.add(bestMatch to srcFile.name)
            }
        }
    }

    // 2. Detect folder moves/renames
    val toMoveFolders = mutableSetOf<Pair<FileInfo, String>>()
    val toRenameFolders = mutableSetOf<Pair<FileInfo, String>>()

    // A folder is moved/renamed if:
    // 1. It's in toDeleteFolders
    // 2. A folder with same "content" is in toAddFolders
    // "Content" here means all files inside it are also moved/renamed to the new folder
    val toDeleteFolders = toDeleteFiles.filter { it.isDirectory }.toMutableSet()
    val toAddFolders = toAddFiles.filter { it.isDirectory }.toMutableSet()

    val fileMovesAndRenames = (toMoveFiles + toRenameFiles).toMap() // oldName -> newName

    // Sort folders by depth to handle nested folders correctly (from deeper to shallower)
    val sortedDeleteFolders = toDeleteFolders.sortedByDescending { it.name.count { c -> c == '/' || c == '\\' } }

    for (delFolder in sortedDeleteFolders) {
        val delFolderPath = delFolder.name + (if (delFolder.name.isEmpty()) "" else "/")
        
        // Find if there's a folder in toAddFolders that contains all "moved" files from this folder
        // AND this folder doesn't have any files that were NOT moved to that new folder.
        
        // This is tricky. Let's simplify:
        // A folder is a candidate if its immediate children files are all moved to the same new location.
        // Ignore excluded files when checking
        val filesInDelFolder = dstFiles.filter { it.name.startsWith(delFolderPath) && !it.isDirectory && it.name.substring(delFolderPath.length).indexOfAny(charArrayOf('/','\\')) == -1 && !isExcluded(it.name) }
        if (filesInDelFolder.isEmpty()) continue

        val firstFile = filesInDelFolder.first()
        val newFirstFileName = fileMovesAndRenames[firstFile] ?: continue
        
        // Potential new folder path
        val suffix = firstFile.name.substring(delFolderPath.length)
        val newFolderPath = newFirstFileName.substring(0, newFirstFileName.length - suffix.length)
        val newFolderName = if (newFolderPath.endsWith("/")) newFolderPath.substring(0, newFolderPath.length - 1) else newFolderPath
        
        val addFolder = toAddFolders.find { it.name == newFolderName }
        if (addFolder != null) {
            // Check if ALL files (recursive) in delFolder are moved to addFolder
            // Ignore excluded files when checking
            val allFilesInDelFolder = dstFiles.filter { it.name.startsWith(delFolderPath) && !it.isDirectory && !isExcluded(it.name) }
            val allMovedToCorrectPlace = allFilesInDelFolder.all { 
                val relativePath = it.name.substring(delFolderPath.length)
                fileMovesAndRenames[it] == addFolder.name + "/" + relativePath
            }

            if (allMovedToCorrectPlace) {
                // It's a folder move/rename!
                if (delFolder.name.substringAfterLast('/') == addFolder.name.substringAfterLast('/')) {
                    toMoveFolders.add(delFolder to addFolder.name)
                } else {
                    toRenameFolders.add(delFolder to addFolder.name)
                }
                toDeleteFolders.remove(delFolder)
                toAddFolders.remove(addFolder)

                toDeleteFiles.remove(delFolder)
                toAddFiles.remove(addFolder)
                
                // Remove all nested files/folders from plans as they are now covered by folder move
                toMoveFiles.removeIf { it.first.name.startsWith(delFolderPath) }
                toRenameFiles.removeIf { it.first.name.startsWith(delFolderPath) }
                toDeleteFiles.removeIf { it.name.startsWith(delFolderPath) }
                toAddFiles.removeIf { it.name.startsWith(addFolder.name + "/") }
            }
        }
    }
    
    // Actually, let's just use the sets we've been maintaining
    return SyncActionCases(
        toAddFiles = toAddFiles.toSet(),
        toDeleteFiles = toDeleteFiles.toSet(),
        toOverrideFiles = toOverride,
        toMoveFiles = toMoveFiles,
        toRenameFiles = toRenameFiles,
        toMoveFolders = toMoveFolders,
        toRenameFolders = toRenameFolders
    )
}

private fun String.indexOfAny(chars: CharArray): Int {
    for (i in indices) {
        if (this[i] in chars) return i
    }
    return -1
}