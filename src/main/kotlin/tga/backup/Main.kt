package tga.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import tga.backup.files.*
import tga.backup.log.alignRight
import tga.backup.log.formatFileSize
import tga.backup.log.formatNumber
import tga.backup.log.logWrap
import tga.backup.logo.printLogo
import tga.backup.params.Params
import tga.backup.params.readParams
import tga.backup.utils.ConsoleMultiThreadWorkers
import java.io.File
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {  }

private fun logPhase(phaseName: String) {
    val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    logger.warn { "[$timestamp] Phase: $phaseName" }
}

private fun logPhaseDuration(phaseName: String, durationMs: Long) {
    val durationSec = durationMs / 1000.0
    logger.warn { "Phase '$phaseName' completed in %.2f seconds".format(durationSec) }
}

fun main(args: Array<String>) {
    try {
        System.setProperty("java.rmi.server.hostname", "localhost")

        printLogo()

        logPhase("Parameter Parsing & Validation")
        val params = args.readParams()

        println("Current folder = '${File(".").canonicalFile.path}'")
        println(params)

        val srcFileOps = buildFileOpsByURL(params.srcFolder, params)
        val dstFileOps = buildFileOpsByURL(params.dstFolder, params)

        logPhase("Source Scanning")
        val srcScanStart = System.currentTimeMillis()
        println("\nListing source files:")
        val srcFiles = if (params.remoteCache && params.srcFolder.contains("://")) {
            val cacheFilePath = getCacheFilePath(params.profile, params.srcFolder)
            readRemoteCache(cacheFilePath) ?: srcFileOps.getFilesSet(params.srcFolder, throwIfNotExist = true)
        } else {
            val files = srcFileOps.getFilesSet(params.srcFolder, throwIfNotExist = true)
            if (params.srcFolder.contains("://")) {
                val cacheFilePath = getCacheFilePath(params.profile, params.srcFolder)
                writeRemoteCache(cacheFilePath, files)
            }
            files
        }
        if (params.verbose) logFilesList("Source", srcFiles)
        logPhaseDuration("Source Scanning", System.currentTimeMillis() - srcScanStart)

        logPhase("Destination Scanning")
        val dstScanStart = System.currentTimeMillis()
        val rootDstFolder = FileInfo("", true, 10L)
        val dstFiles = if (params.remoteCache && params.dstFolder.contains("://")) {
            val cacheFilePath = getCacheFilePath(params.profile, params.dstFolder)
            (readRemoteCache(cacheFilePath) ?: dstFileOps.getFilesSet(
                params.dstFolder,
                throwIfNotExist = false
            )) - rootDstFolder
        } else {
            val files = dstFileOps.getFilesSet(params.dstFolder, throwIfNotExist = false) - rootDstFolder
            if (params.dstFolder.contains("://")) {
                val cacheFilePath = getCacheFilePath(params.profile, params.dstFolder)
                writeRemoteCache(cacheFilePath, files + rootDstFolder)
            }
            files
        }
        if (params.verbose) logFilesList("Destination", dstFiles)
        logPhaseDuration("Destination Scanning", System.currentTimeMillis() - dstScanStart)

        logPhase("Comparison & Plan Building")
        val comparisonStart = System.currentTimeMillis()
        val actions = compareSrcAndDst(srcFiles = srcFiles, dstFiles = dstFiles, excludePatterns = params.exclude)
        logPhaseDuration("Comparison & Plan Building", System.currentTimeMillis() - comparisonStart)

        val excludedFiles = srcFiles.filter { it.readException != null }

        if (actions.toAddFiles.isEmpty() && actions.toDeleteFiles.isEmpty() && actions.toOverrideFiles.isEmpty()) {
            logger.info { "The source and destination are already exactly the same. No actions required." }
            srcFileOps.close()
            dstFileOps.close()
            return
        }

        logFilesList("\nTo Copy ('${params.srcFolder}' ---> '${params.dstFolder}')", actions.toAddFiles)
        if (!params.noOverriding) {
            logFilesList("\nTo Override ('${params.srcFolder}' ---> '${params.dstFolder}')", actions.toOverrideFiles)
        }
        logMovesList("\nTo Move (in '${params.dstFolder}')", actions.toMoveFiles)
        logMovesList("\nTo Rename (in '${params.dstFolder}')", actions.toRenameFiles, isRenamed = true)
        logMovesList("\nFolders to Move (in '${params.dstFolder}')", actions.toMoveFolders)
        logMovesList("\nFolders to Rename (in '${params.dstFolder}')", actions.toRenameFolders, isRenamed = true)

        if (!params.noDeletion) {
            logFilesList("\nTo Delete (in '${params.dstFolder}')", actions.toDeleteFiles)
        }

        val yellow = "\u001b[33m"
        val reset = "\u001b[0m"

        if (params.noOverriding && actions.toOverrideFiles.isNotEmpty()) {
            println("${yellow}WARNING: The 'no-overriding' parameter is specified. The overriding phase will be skipped.${reset}")
        }

        if (params.noDeletion && actions.toDeleteFiles.isNotEmpty()) {
            println("${yellow}WARNING: The 'no-deletion' parameter is specified. The deletion phase will be skipped.${reset}")
        }

        printSummary(actions)

        val anyMoves =
            actions.toMoveFiles.isNotEmpty() || actions.toRenameFiles.isNotEmpty() || actions.toMoveFolders.isNotEmpty() || actions.toRenameFolders.isNotEmpty()

        if (anyMoves) {
            println("${yellow}Moving/Renaming actions detected. You can execute only them (skip copying/deleting) by typing 'm'.${reset}")
        }

        print("Continue (Y/N/m)?>")
        val continueAnswer = readln()

        if (continueAnswer !in setOf("Y", "y", "m", "M")) {
            srcFileOps.close()
            dstFileOps.close()
            return
        }

        logPhase("Execution Phase")
        val executionStart = System.currentTimeMillis()
        val results = mutableListOf<Result<Unit>>()
        try {
            if (continueAnswer in setOf("m", "M")) {
                logPhase("Moving/Renaming")
                val moveStart = System.currentTimeMillis()
                results += runMoving(dstFileOps, params, actions)
                logPhaseDuration("Moving/Renaming", System.currentTimeMillis() - moveStart)
            } else {
                if (actions.toAddFiles.isNotEmpty()) {
                    logPhase("Copying Files")
                    val copyStart = System.currentTimeMillis()
                    results += runCopying(srcFileOps, dstFileOps, params, actions.toAddFiles, override = false)
                    logPhaseDuration("Copying Files", System.currentTimeMillis() - copyStart)
                }

                if (!params.noOverriding && actions.toOverrideFiles.isNotEmpty()) {
                    logPhase("Overriding Files")
                    val overrideStart = System.currentTimeMillis()
                    results += runCopying(srcFileOps, dstFileOps, params, actions.toOverrideFiles, override = true)
                    logPhaseDuration("Overriding Files", System.currentTimeMillis() - overrideStart)
                }

                if (!params.noDeletion && actions.toDeleteFiles.isNotEmpty()) {
                    logPhase("Deleting Files")
                    val deleteStart = System.currentTimeMillis()
                    results += runDeleting(dstFileOps, params, actions.toDeleteFiles)
                    logPhaseDuration("Deleting Files", System.currentTimeMillis() - deleteStart)
                }
            }
        } finally {
            srcFileOps.close()
            dstFileOps.close()
        }
        logPhaseDuration("Execution Phase", System.currentTimeMillis() - executionStart)

        if (excludedFiles.isNotEmpty()) {
            println("\nEXCLUDED FILES (due to read errors):")
            excludedFiles.forEach { println("- ${it.name}") }

            println("\nEXCLUDED FILES WITH STACKTRACES:")
            excludedFiles.forEach {
                println("\n\nFile: ${it.name}")
                it.readException?.printStackTrace()
            }
        }

        logPhase("Final Summary")
        printFinalSummary(results)
        logPhase("Synchronization Complete")
    } catch (t: Throwable) {
        logger.error(t) { "Backup failed with an exception." }
        exitProcess(-1)
    }
}

fun printFinalSummary(results: List<Result<Unit>>) {
    val successCount = results.count { it.isSuccess }
    val errorCount = results.count { it.isFailure }

    println("\nFinal Result:")
    println("Successfully processed: $successCount")
    println("Errors:                 $errorCount")

    if (errorCount > 0) {
        println("\nDetailed errors:")
        results.filter { it.isFailure }.forEach {
            println("- ${it.exceptionOrNull()?.message ?: "Unknown error"}")
        }
    }
}

fun runCopying(srcFileOps: FileOps, dstFileOps: FileOps, params: Params, toCopy: Set<FileInfo>, override: Boolean): List<Result<Unit>> {
    if (toCopy.isEmpty()) return emptyList()

    val actionName = if (override) "Overriding" else "Copying"

    println("\nCreating folders for $actionName:....")
    try {
        dstFileOps.createFolders(toCopy, params.dstFolder, params.dryRun)
    } finally {
        println(".... folders creation finished\n")
    }

    println("\n$actionName files:....")
    val workers = ConsoleMultiThreadWorkers<Unit>(params.parallelThreads)
    try {
        return dstFileOps.copyFiles(srcFileOps, params.srcFolder, toCopy, params.dstFolder, params.dryRun, override, workers)
    } finally {
        println(".... $actionName is finished\n")
    }
}


fun runDeleting(dstFileOps: FileOps, params: Params, toDelete: Set<FileInfo>): List<Result<Unit>> {
    if (toDelete.isEmpty()) return emptyList()

    if (params.noDeletion) {
        println("\nSkipping deletion phase due to 'no-deletion' parameter.")
        return emptyList()
    }

    println("\nDeleting:....")
    try {
        return if (toDelete.isNotEmpty())
            dstFileOps.deleteFiles(toDelete, params.dstFolder, params.dryRun, params.noDeletion)
        else
            emptyList()
    } finally {
        println(".... Deleting is finished\n")
    }
}

fun runMoving(dstFileOps: FileOps, params: Params, actions: SyncActionCases): List<Result<Unit>> {
    val results = mutableListOf<Result<Unit>>()
    val separator = dstFileOps.filesSeparator

    fun moveItems(items: Set<Pair<FileInfo, String>>, type: String) {
        if (items.isEmpty()) return
        println("\n$type:....")
        items.sortedBy { it.second }.forEach { (src, dst) ->
            val fromPath = "${params.dstFolder}${separator}${src.name}"
            val toPath = "${params.dstFolder}${separator}${dst}"
            try {
                logWrap("Moving $type: $fromPath  --->  $toPath") {
                    if (!params.dryRun) dstFileOps.moveFileOrFolder(fromPath, toPath)
                }
                results.add(Result.success(Unit))
            } catch (e: Throwable) {
                results.add(Result.failure(e))
            }
        }
        println(".... $type finished\n")
    }

    moveItems(actions.toRenameFolders, "Folders Renaming")
    moveItems(actions.toMoveFolders, "Folders Moving")
    moveItems(actions.toRenameFiles, "Files Renaming")
    moveItems(actions.toMoveFiles, "Files Moving")

    return results
}


fun logFilesList(prefix: String, filesList: Set<FileInfo>) {
    if (filesList.isEmpty()) {
        // println("$prefix: <EMPTY>")
    } else {
        println("$prefix: \n")
        val l = formatNumber(filesList.size).length
        filesList.sorted().forEachIndexed { i, f ->
            print("${formatNumber(i).padStart(l)}. [${formatFileSize(f.size, 6)}] ")
            println(f.name)
        }
    }
}

fun logMovesList(prefix: String, movesList: Set<Pair<FileInfo, String>>, isRenamed: Boolean = false) {
    if (movesList.isEmpty()) return
    println("$prefix: \n")
    val yellow = "\u001b[33m"
    val reset = "\u001b[0m"

    val l = formatNumber(movesList.size).length
    movesList.sortedBy { it.second }.forEachIndexed { i, (src, dst) ->
        print("${formatNumber(i).padStart(l)}. ")
        if (isRenamed) print("${yellow}(renamed) ${reset}")
        println("${src.name}  --->  $dst")
    }
}

fun printSummary(actions: SyncActionCases) {
    val toAddFiles = actions.toAddFiles.filter { !it.isDirectory }
    val toOverrideFiles = actions.toOverrideFiles.filter { !it.isDirectory }
    val toDeleteFiles = actions.toDeleteFiles.filter { !it.isDirectory }

    val toAddFolders = actions.toAddFiles.filter { it.isDirectory }
    val toOverrideFolders = actions.toOverrideFiles.filter { it.isDirectory }
    val toDeleteFolders = actions.toDeleteFiles.filter { it.isDirectory }

    val toMoveFilesCount = actions.toMoveFiles.size
    val toRenameFilesCount = actions.toRenameFiles.size
    val toMoveFoldersCount = actions.toMoveFolders.size
    val toRenameFoldersCount = actions.toRenameFolders.size

    val foldersData = alignRight(
        "Folders count".length,
        formatNumber(toAddFolders.size),
        formatNumber(toOverrideFolders.size),
        formatNumber(toDeleteFolders.size),
        formatNumber(toAddFolders.size + toOverrideFolders.size),
        formatNumber(toMoveFoldersCount),
        formatNumber(toRenameFoldersCount),
    )
    val toAddFoldersStr = foldersData[0]
    val toOverrideFoldersStr = foldersData[1]
    val toDeleteFoldersStr = foldersData[2]
    val totalFoldersStr = foldersData[3]
    val toMoveFoldersStr = foldersData[4]
    val toRenameFoldersStr = foldersData[5]

    val filesData = alignRight(
        "Files count".length,
        formatNumber(toAddFiles.size),
        formatNumber(toOverrideFiles.size),
        formatNumber(toDeleteFiles.size),
        formatNumber(toAddFiles.size + toOverrideFiles.size),
        formatNumber(toMoveFilesCount),
        formatNumber(toRenameFilesCount),
    )
    val toAddFilesStr = filesData[0]
    val toOverrideFilesStr = filesData[1]
    val toDeleteFilesStr = filesData[2]
    val totalFilesStr = filesData[3]
    val toMoveFilesStr = filesData[4]
    val toRenameFilesStr = filesData[5]

    val toAddSize       = toAddFiles.sumOf { it.size }
    val toOverrideSize  = toOverrideFiles.sumOf { it.size }
    val toDeleteSize    = toDeleteFiles.sumOf { it.size }
    val toMoveSize      = actions.toMoveFiles.sumOf { it.first.size }
    val toRenameSize    = actions.toRenameFiles.sumOf { it.first.size }

    val sizesData = alignRight(
        "Total Size".length,
        formatFileSize(toAddSize),
        formatFileSize(toOverrideSize),
        formatFileSize(toDeleteSize),
        formatFileSize(toAddSize + toOverrideSize),
        formatFileSize(toMoveSize),
        formatFileSize(toRenameSize),
    )
    val toAddSizeStr = sizesData[0]
    val toOverrideSizeStr = sizesData[1]
    val toDeleteSizeStr = sizesData[2]
    val totalSizeStr = sizesData[3]
    val toMoveSizeStr = sizesData[4]
    val toRenameSizeStr = sizesData[5]

    val lineStr = "-".repeat("| Action    |  |  |  |".length + toAddFoldersStr.length + toAddFilesStr.length + toAddSizeStr.length)

    println("\nSummary:")
    println(lineStr)
    println("| Action    | Folders count | Files count | Total Size |")
    println(lineStr)
    println("| Copy      | $toAddFoldersStr | $toAddFilesStr | $toAddSizeStr |")
    println("| Override  | $toOverrideFoldersStr | $toOverrideFilesStr | $toOverrideSizeStr |")
    println(lineStr)
    println("| To upload | $totalFoldersStr | $totalFilesStr | $totalSizeStr |")
    println(lineStr)
    println("| Move      | $toMoveFoldersStr | $toMoveFilesStr | $toMoveSizeStr |")
    println("| Rename    | $toRenameFoldersStr | $toRenameFilesStr | $toRenameSizeStr |")
    println(lineStr)
    println("| To Delete | $toDeleteFoldersStr | $toDeleteFilesStr | $toDeleteSizeStr |")
    println(lineStr)
    println("")
}

