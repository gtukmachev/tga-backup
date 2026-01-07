package tga.backup.files

data class SyncActionCases(
    val toAddFiles: Set<FileInfo>,
    val toDeleteFiles: Set<FileInfo>,
    val toOverrideFiles: Set<FileInfo>,
)

fun compareSrcAndDst(srcFiles: Set<FileInfo>, dstFiles: Set<FileInfo>): SyncActionCases {
    val srcFilesFiltered = srcFiles.filter { it.readException == null }

    val srcFileNames = srcFilesFiltered.map { it.name }.toSet()
    val allSrcFileNames = srcFiles.map { it.name }.toSet()
    val dstFileNames = dstFiles.map { it.name }.toSet()

    val toCopyNames = srcFileNames - dstFileNames
    val toDeleteNames = dstFileNames - allSrcFileNames
    val toOverrideNames = srcFileNames.intersect(dstFileNames)

    val toCopy = srcFilesFiltered.filter { it.name in toCopyNames }.toSet()
    val toDelete = dstFiles.filter { it.name in toDeleteNames }.toSet()

    val srcFilesMap = srcFilesFiltered.associateBy { it.name }
    val dstFilesMap = dstFiles.associateBy { it.name }
    val toOverride = toOverrideNames
        .filter { srcFilesMap[it] != dstFilesMap[it] }
        .map { srcFilesMap[it]!! }
        .toSet()

    return SyncActionCases(toCopy, toDelete, toOverride)
}