package tga.backup.duplicates

import tga.backup.files.FileInfo

data class DuplicateGroup(
    val md5: String,
    val files: List<FileInfo>,
    val totalSize: Long
) {
    val wastedSpace: Long get() = totalSize * (files.size - 1)
}

data class DuplicateFolderGroup(
    val folderFingerprint: String,
    val folders: List<String>, // paths to folders
    val filesCount: Int,
    val totalSize: Long
) {
    val wastedSpace: Long get() = totalSize * (folders.size - 1)
}

data class PartialDuplicateFolderInfo(
    val folderPath: String,
    val duplicateFilesCount: Int,
    val duplicateFilesSize: Long,
    val totalFilesCount: Int,
    val isOriginalCandidate: Boolean = false
) {
    val isFullDuplicate: Boolean get() = duplicateFilesCount == totalFilesCount
}

data class PartialDuplicateFolderGroup(
    val folders: List<PartialDuplicateFolderInfo>,
    val fileGroups: List<DuplicateGroup>
) {
    val totalDuplicateFilesSize: Long get() = fileGroups.sumOf { it.totalSize * it.files.size }
    val wastedSpace: Long get() = fileGroups.sumOf { it.wastedSpace }
}

data class DuplicatesResult(
    val folderGroups: List<DuplicateFolderGroup>,
    val partialFolderGroups: List<PartialDuplicateFolderGroup>,
    val fileGroups: List<DuplicateGroup>
)

/**
 * Finds duplicate files and folders.
 * 
 * @param allFiles Set of FileInfo to analyze
 * @return DuplicatesResult containing folder and file duplicate groups
 */
fun findDuplicates(allFiles: Set<FileInfo>): DuplicatesResult {
    // 1. Find all potential file duplicates
    val filesWithMd5 = allFiles
        .filter { !it.isDirectory && it.md5 != null }
    
    val groupedByMd5 = filesWithMd5.groupBy { it.md5!! }
    val initialFileGroups = groupedByMd5
        .filter { (_, fileList) -> fileList.size > 1 }
        .mapValues { (md5, fileList) ->
            val sortedFiles = fileList.sortedBy { it.name }
            DuplicateGroup(
                md5 = md5,
                files = sortedFiles,
                totalSize = sortedFiles.first().size
            )
        }

    // 2. Find folder duplicates
    // Group files by their parent directory
    val folderContent = mutableMapOf<String, MutableList<FileInfo>>()
    for (file in filesWithMd5) {
        val parent = file.name.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) {
            folderContent.getOrPut(parent) { mutableListOf() }.add(file)
        }
    }

    // Calculate fingerprint for each folder
    // Fingerprint: sorted list of "relativeName:md5"
    val folderFingerprints = folderContent.mapValues { (folderPath, files) ->
        val fingerprint = files.sortedBy { it.name }
            .joinToString(";") { file ->
                val relativeName = file.name.removePrefix("$folderPath/")
                "$relativeName:${file.md5}"
            }
        val totalSize = files.sumOf { it.size }
        fingerprint to totalSize
    }

    // Group folders by fingerprint
    val groupedFolders = folderFingerprints.entries
        .groupBy({ it.value.first }, { it.key })
        .filter { it.value.size > 1 && it.key.isNotEmpty() }

    val folderGroups = groupedFolders.map { (fingerprint, paths) ->
        val firstPath = paths.first()
        val totalSize = folderFingerprints[firstPath]!!.second
        DuplicateFolderGroup(
            folderFingerprint = fingerprint,
            folders = paths.sorted(),
            filesCount = folderContent[firstPath]!!.size,
            totalSize = totalSize
        )
    }.sortedByDescending { it.wastedSpace }

    // 3. Find partially duplicate folders
    // We only consider files that are NOT in full duplicate folders
    val filesInFullDuplicateFolders = mutableSetOf<String>()
    for (group in folderGroups) {
        for (folderPath in group.folders) {
            folderContent[folderPath]?.forEach { filesInFullDuplicateFolders.add(it.name) }
        }
    }

    val remainingFileGroups = initialFileGroups.values
        .map { group -> group.copy(files = group.files.filter { it.name !in filesInFullDuplicateFolders }) }
        .filter { it.files.size > 1 }

    // Map each duplicate file to its group
    val fileToGroup = mutableMapOf<String, DuplicateGroup>()
    for (group in remainingFileGroups) {
        for (file in group.files) {
            fileToGroup[file.name] = group
        }
    }

    // Map each folder to the duplicate groups it contains
    val folderToGroups = mutableMapOf<String, MutableSet<String>>() // folderPath -> set of MD5s
    for (group in remainingFileGroups) {
        for (file in group.files) {
            val parent = file.name.substringBeforeLast('/', "")
            folderToGroups.getOrPut(parent) { mutableSetOf() }.add(group.md5)
        }
    }

    // Build connected components of folders
    // Two folders are connected if they share at least one duplicate file group
    val visitedFolders = mutableSetOf<String>()
    val partialFolderGroups = mutableListOf<PartialDuplicateFolderGroup>()

    for (startFolder in folderToGroups.keys) {
        if (startFolder.isEmpty()) continue // ignore files in root for partial folder detection
        if (startFolder in visitedFolders) continue

        val componentFolders = mutableSetOf<String>()
        val queue = mutableListOf(startFolder)
        val componentVisitedFolders = mutableSetOf(startFolder)

        while (queue.isNotEmpty()) {
            val folder = queue.removeAt(0)
            componentFolders.add(folder)

            val md5sInFolder = folderToGroups[folder] ?: emptySet()
            for (md5 in md5sInFolder) {
                val group = remainingFileGroups.find { it.md5 == md5 }!!
                for (file in group.files) {
                    val otherFolder = file.name.substringBeforeLast('/', "")
                    if (otherFolder.isNotEmpty() && otherFolder !in componentVisitedFolders) {
                        componentVisitedFolders.add(otherFolder)
                        queue.add(otherFolder)
                    }
                }
            }
        }

        // Mark all folders in this component as visited globally
        visitedFolders.addAll(componentFolders)

        if (componentFolders.size > 1) {
            // Find all file groups that are present in these folders
            val md5sInComponent = componentFolders.flatMap { folderToGroups[it] ?: emptySet() }.toSet()
            val groupsInComponent = remainingFileGroups.filter { it.md5 in md5sInComponent }

            // Check if ALL files of these groups are within the component folders
            val allFilesAreInComponent = groupsInComponent.all { group ->
                group.files.all { file ->
                    val fileFolder = file.name.substringBeforeLast('/', "")
                    fileFolder in componentFolders
                }
            }

            if (allFilesAreInComponent) {
                val folderInfosWithoutOriginals = componentFolders.map { folderPath ->
                    val allFilesInFolder = folderContent[folderPath]!!
                    val duplicateFilesInFolder = allFilesInFolder.filter { file ->
                        groupsInComponent.any { g -> g.files.any { f -> f.name == file.name } }
                    }
                    PartialDuplicateFolderInfo(
                        folderPath = folderPath,
                        duplicateFilesCount = duplicateFilesInFolder.size,
                        duplicateFilesSize = duplicateFilesInFolder.sumOf { it.size },
                        totalFilesCount = allFilesInFolder.size
                    )
                }

                // Identify original candidates
                val finalFolderInfos = folderInfosWithoutOriginals.map { info ->
                    val isOriginal = if (info.isFullDuplicate) {
                        // A folder is an ORIGINAL candidate if:
                        // 1. It is a full duplicate.
                        // 2. There is no other folder in this component that is an EXACT duplicate of it.
                        // 3. It is NOT a subset of another full duplicate folder in the same component.
                        
                        val thisMd5s = folderContent[info.folderPath]!!.mapNotNull { it.md5 }.toSet()
                        
                        val otherFullDuplicates = folderInfosWithoutOriginals
                            .filter { it.folderPath != info.folderPath && it.isFullDuplicate }
                        
                        val hasExactDuplicate = otherFullDuplicates.any { other ->
                            val otherMd5s = folderContent[other.folderPath]!!.mapNotNull { it.md5 }.toSet()
                            thisMd5s == otherMd5s
                        }

                        val isSubsetOfAnother = otherFullDuplicates.any { other ->
                            val otherMd5s = folderContent[other.folderPath]!!.mapNotNull { it.md5 }.toSet()
                            otherMd5s.size > thisMd5s.size && otherMd5s.containsAll(thisMd5s)
                        }

                        !hasExactDuplicate && !isSubsetOfAnother
                    } else {
                        false
                    }
                    info.copy(isOriginalCandidate = isOriginal)
                }.sortedWith(
                    compareByDescending<PartialDuplicateFolderInfo> { it.isOriginalCandidate }
                        .thenByDescending { it.isFullDuplicate }
                        .thenBy { it.folderPath }
                )

                partialFolderGroups.add(PartialDuplicateFolderGroup(finalFolderInfos, groupsInComponent.sortedBy { it.md5 }))
            }
        }
    }

    // 4. Final filtering: remove files that are part of folderGroups or partialFolderGroups
    val filesInHandledFolderGroups = filesInFullDuplicateFolders.toMutableSet()
    for (group in partialFolderGroups) {
        for (fileGroup in group.fileGroups) {
            for (file in fileGroup.files) {
                filesInHandledFolderGroups.add(file.name)
            }
        }
    }

    val finalFileGroups = initialFileGroups.values.mapNotNull { group ->
        val remainingFiles = group.files.filter { it.name !in filesInHandledFolderGroups }
        if (remainingFiles.size > 1) {
            group.copy(files = remainingFiles)
        } else {
            null
        }
    }.sortedByDescending { it.wastedSpace }

    return DuplicatesResult(folderGroups, partialFolderGroups.sortedByDescending { it.wastedSpace }, finalFileGroups)
}

/**
 * Calculates summary statistics for duplicate groups.
 */
data class DuplicatesSummary(
    val totalGroups: Int,
    val totalFolderGroups: Int,
    val totalPartialFolderGroups: Int,
    val totalDuplicateFiles: Int,
    val totalWastedSpace: Long,
    val largestGroup: DuplicateGroup?
) {
    companion object {
        fun from(result: DuplicatesResult): DuplicatesSummary {
            val fileGroups = result.fileGroups
            val folderGroups = result.folderGroups
            val partialGroups = result.partialFolderGroups
            
            return DuplicatesSummary(
                totalGroups = fileGroups.size,
                totalFolderGroups = folderGroups.size,
                totalPartialFolderGroups = partialGroups.size,
                totalDuplicateFiles = fileGroups.sumOf { it.files.size - 1 },
                totalWastedSpace = fileGroups.sumOf { it.wastedSpace } + 
                                   folderGroups.sumOf { it.wastedSpace } + 
                                   partialGroups.sumOf { it.wastedSpace },
                largestGroup = fileGroups.maxByOrNull { it.wastedSpace }
            )
        }
    }
}
