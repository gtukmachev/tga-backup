package tga.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import tga.backup.files.*
import tga.backup.log.alignRight
import tga.backup.log.formatFileSize
import tga.backup.log.formatNumber
import tga.backup.logo.printLogo
import tga.backup.params.ArgumentIsMissed
import tga.backup.params.Params
import tga.backup.params.readParams
import tga.backup.utils.ConsoleMultiThreadWorkers
import java.io.File

private val logger = KotlinLogging.logger {  }

fun main(args: Array<String>) {
    System.setProperty("java.rmi.server.hostname", "localhost")

    printLogo()

    val params = try {
        args.readParams()
    } catch (e: ArgumentIsMissed) {
        logger.error { e.message }
        return
    }
    println("Current folder = '${File(".").canonicalFile.path}'")
    println(params)

    val srcFileOps = buildFileOpsByURL(params.srcFolder, params)
    val dstFileOps = buildFileOpsByURL(params.dstFolder, params)

    println("\nListing source files:")
    val srcFiles =  srcFileOps.getFilesSet(params.srcFolder, throwIfNotExist = true)
    if (params.verbose) logFilesList("Source", srcFiles)

    val rootDstFolder =  FileInfo("", true, 10L)
    val dstFiles = dstFileOps.getFilesSet(params.dstFolder, throwIfNotExist = false) - rootDstFolder
    if (params.verbose) logFilesList("Destination", dstFiles)

    val actions = compareSrcAndDst(srcFiles = srcFiles, dstFiles = dstFiles)

    val excludedFiles = srcFiles.filter { it.readException != null }

    if (actions.toAddFiles.isEmpty() && actions.toDeleteFiles.isEmpty() && actions.toOverrideFiles.isEmpty()) {
        logger.info { "The source and destination are already exactly the same. No actions required." }
        srcFileOps.close()
        dstFileOps.close()
        return
    }

    logFilesList("\nTo Copy ('${params.srcFolder}' ---> '${params.dstFolder}')", actions.toAddFiles)
    logFilesList("\nTo Override ('${params.srcFolder}' ---> '${params.dstFolder}')", actions.toOverrideFiles)
    logFilesList("\nTo Delete (in '${params.dstFolder}')", actions.toDeleteFiles)

    if (params.noDeletion && actions.toDeleteFiles.isNotEmpty()) {
        val yellow = "\u001b[33m"
        val reset = "\u001b[0m"
        println("${yellow}WARNING: The 'no-deletion' parameter is specified. The deletion phase will be skipped.${reset}")
    }

    printSummary(actions)

    print("Continue (Y/N)?>")
    val continueAnswer = readln()
    if (continueAnswer !in setOf("Y", "y")) {
        srcFileOps.close()
        dstFileOps.close()
        return
    }

    val results = mutableListOf<Result<Unit>>()
    try {
        results += runCopying(srcFileOps, dstFileOps, params, actions.toAddFiles, override = false)
        results += runCopying(srcFileOps, dstFileOps, params, actions.toOverrideFiles, override = true)
        results += runDeleting(dstFileOps, params, actions.toDeleteFiles)
    } finally {
        srcFileOps.close()
        dstFileOps.close()
    }

    if (excludedFiles.isNotEmpty()) {
        println("\nEXCLUDED FILES (due to read errors):")
        excludedFiles.forEach { println("- ${it.name}") }

        println("\nEXCLUDED FILES WITH STACKTRACES:")
        excludedFiles.forEach {
            println("\n\nFile: ${it.name}")
            it.readException?.printStackTrace()
        }
    }

    printFinalSummary(results)
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

fun printSummary(actions: SyncActionCases) {
    val toAddFiles = actions.toAddFiles.filter { !it.isDirectory }
    val toOverrideFiles = actions.toOverrideFiles.filter { !it.isDirectory }
    val toDeleteFiles = actions.toDeleteFiles.filter { !it.isDirectory }

    val toAddFolders = actions.toAddFiles.filter { it.isDirectory }
    val toOverrideFolders = actions.toOverrideFiles.filter { it.isDirectory }
    val toDeleteFolders = actions.toDeleteFiles.filter { it.isDirectory }

    val (toAddFoldersStr, toOverrideFoldersStr, toDeleteFoldersStr, totalFoldersStr) = alignRight(
        "Folders count".length,
        formatNumber(toAddFolders.size),
        formatNumber(toOverrideFolders.size),
        formatNumber(toDeleteFolders.size),
        formatNumber(toAddFolders.size + toOverrideFolders.size),

    )

    val (toAddFilesStr, toOverrideFilesStr, toDeleteFilesStr, totalFilesStr) = alignRight(
        "Files count".length,
        formatNumber(toAddFiles.size),
        formatNumber(toOverrideFiles.size),
        formatNumber(toDeleteFiles.size),
        formatNumber(toAddFiles.size + toOverrideFiles.size),
    )

    val toAddSize       = toAddFiles.sumOf { it.size }
    val toOverrideSize  = toOverrideFiles.sumOf { it.size }
    val toDeleteSize    = toDeleteFiles.sumOf { it.size }

    val (toAddSizeStr, toOverrideSizeStr, toDeleteSizeStr, totalSizeStr) = alignRight(
        "Total Size".length,
        formatFileSize(toAddSize),
        formatFileSize(toOverrideSize),
        formatFileSize(toDeleteSize),
        formatFileSize(toAddSize + toOverrideSize),
    )

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
    println("| To Delete | $toDeleteFoldersStr | $toDeleteFilesStr | $toDeleteSizeStr |")
    println(lineStr)
    println("")
}

