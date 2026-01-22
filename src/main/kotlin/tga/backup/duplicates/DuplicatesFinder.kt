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

data class DuplicatesResult(
    val folderGroups: List<DuplicateFolderGroup>,
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

    // 3. Remove files that are part of duplicate folders from fileGroups
    val filesInDuplicateFolders = mutableSetOf<String>()
    for (group in folderGroups) {
        // Keep files in the FIRST folder of each group (they are not "wasted" in the context of folders)
        // Actually, the requirement is to simplify the set. 
        // If a folder is a duplicate, all its files in all instances are already "represented" by the folder duplicate.
        for (folderPath in group.folders) {
            folderContent[folderPath]?.forEach { filesInDuplicateFolders.add(it.name) }
        }
    }

    val filteredFileGroups = initialFileGroups.values.mapNotNull { group ->
        val remainingFiles = group.files.filter { it.name !in filesInDuplicateFolders }
        if (remainingFiles.size > 1) {
            group.copy(files = remainingFiles)
        } else {
            null
        }
    }.sortedByDescending { it.wastedSpace }

    return DuplicatesResult(folderGroups, filteredFileGroups)
}

/**
 * Calculates summary statistics for duplicate groups.
 */
data class DuplicatesSummary(
    val totalGroups: Int,
    val totalFolderGroups: Int,
    val totalDuplicateFiles: Int,
    val totalWastedSpace: Long,
    val largestGroup: DuplicateGroup?
) {
    companion object {
        fun from(result: DuplicatesResult): DuplicatesSummary {
            val fileGroups = result.fileGroups
            val folderGroups = result.folderGroups
            
            return DuplicatesSummary(
                totalGroups = fileGroups.size,
                totalFolderGroups = folderGroups.size,
                totalDuplicateFiles = fileGroups.sumOf { it.files.size - 1 },
                totalWastedSpace = fileGroups.sumOf { it.wastedSpace } + folderGroups.sumOf { it.wastedSpace },
                largestGroup = fileGroups.maxByOrNull { it.wastedSpace }
            )
        }
    }
}
