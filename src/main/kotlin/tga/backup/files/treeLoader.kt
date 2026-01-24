package tga.backup.files

import tga.backup.log.logFilesList
import tga.backup.log.logPhase
import tga.backup.log.logPhaseDuration
import tga.backup.params.Params

fun loadTree(
    name: String,
    folder: String,
    params: Params,
    throwIfNotExist: Boolean = true,
    additionalProcessing: (Set<FileInfo>) -> Set<FileInfo> = { it }
): Pair<FileOps, Set<FileInfo>> {
    val fileOps = buildFileOpsByURL(folder, params)
    logPhase("$name Scanning")
    val start = System.currentTimeMillis()
    println("\nListing $name files:")
    var files = fileOps.getFilesSet(folder, throwIfNotExist = throwIfNotExist)
    files = additionalProcessing(files)
    if (params.verbose) logFilesList(name, files)
    logPhaseDuration("$name Scanning", System.currentTimeMillis() - start)
    return fileOps to files
}
