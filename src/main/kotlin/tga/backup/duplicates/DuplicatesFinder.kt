package tga.backup.duplicates

import tga.backup.files.FileInfo

data class DuplicateGroup(
    val md5: String,
    val files: List<FileInfo>,
    val totalSize: Long
) {
    val wastedSpace: Long get() = totalSize * (files.size - 1)
}

/**
 * Finds duplicate files based on MD5 hash.
 * 
 * @param files Set of FileInfo to analyze
 * @return Map of MD5 hash to list of duplicate files with that hash
 */
fun findDuplicates(files: Set<FileInfo>): Map<String, DuplicateGroup> {
    // Filter out directories and files without MD5
    val filesWithMd5 = files
        .asSequence()
        .filter { !it.isDirectory && it.md5 != null }
    
    // Group by MD5 hash
    val groupedByMd5 = filesWithMd5.groupBy { it.md5!! }
    
    // Filter groups with more than one file (duplicates)
    val duplicateGroups = groupedByMd5
        .filter { (_, fileList) -> fileList.size > 1 }
        .mapValues { (md5, fileList) ->
            val sortedFiles = fileList.sortedBy { it.name }
            DuplicateGroup(
                md5 = md5,
                files = sortedFiles,
                totalSize = sortedFiles.first().size
            )
        }
    
    return duplicateGroups
}

/**
 * Calculates summary statistics for duplicate groups.
 */
data class DuplicatesSummary(
    val totalGroups: Int,
    val totalDuplicateFiles: Int,
    val totalWastedSpace: Long,
    val largestGroup: DuplicateGroup?
) {
    companion object {
        fun from(duplicateGroups: Map<String, DuplicateGroup>): DuplicatesSummary {
            val groups = duplicateGroups.values.toList()
            return DuplicatesSummary(
                totalGroups = groups.size,
                totalDuplicateFiles = groups.sumOf { it.files.size - 1 },
                totalWastedSpace = groups.sumOf { it.wastedSpace },
                largestGroup = groups.maxByOrNull { it.wastedSpace }
            )
        }
    }
}
