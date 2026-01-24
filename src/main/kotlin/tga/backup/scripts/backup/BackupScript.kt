package tga.backup.scripts.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import tga.backup.files.*
import tga.backup.log.*
import tga.backup.params.ArgumentIsMissed
import tga.backup.params.Params
import tga.backup.scripts.Script
import tga.backup.utils.ConsoleMultiThreadWorkers

private val logger = KotlinLogging.logger {  }

class BackupScript(params: Params) : Script(params) {

    init {
        if (params.srcRoot.isBlank()) throw ArgumentIsMissed("-sr (--source-root)")
        if (params.dstRoot.isBlank()) throw ArgumentIsMissed("-dr (--destination-root)")
    }

    override fun run() {
        val (srcFileOps, srcFiles) = loadTree("Source", params.srcFolder, params, throwIfNotExist = true)
        val (dstFileOps, dstFiles) = loadTree("Destination", params.dstFolder, params, throwIfNotExist = false) { 
            it - FileInfo("", true, 10L)
        }

        try {
            logPhase("Comparison & Plan Building")
            val comparisonStart = System.currentTimeMillis()
            val actions = compareSrcAndDst(srcFiles = srcFiles, dstFiles = dstFiles, excludePatterns = params.exclude)
            logPhaseDuration("Comparison & Plan Building", System.currentTimeMillis() - comparisonStart)

            val excludedFiles = srcFiles.filter { it.readException != null }

            if (actions.toAddFiles.isEmpty() && actions.toDeleteFiles.isEmpty() && actions.toOverrideFiles.isEmpty()) {
                logger.info { "The source and destination are already exactly the same. No actions required." }
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
                return
            }

            logPhase("Execution Phase")
            val executionStart = System.currentTimeMillis()
            val results = mutableListOf<Result<Unit>>()

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
            logPhase("Backup Complete")
        } finally {
            srcFileOps.close()
            dstFileOps.close()
        }
    }

    private fun printFinalSummary(results: List<Result<Unit>>) {
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

    private fun runCopying(srcFileOps: FileOps, dstFileOps: FileOps, params: Params, toCopy: Set<FileInfo>, override: Boolean): List<Result<Unit>> {
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


    private fun runDeleting(dstFileOps: FileOps, params: Params, toDelete: Set<FileInfo>): List<Result<Unit>> {
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

    private fun runMoving(dstFileOps: FileOps, params: Params, actions: SyncActionCases): List<Result<Unit>> {
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


    private fun logMovesList(prefix: String, movesList: Set<Pair<FileInfo, String>>, isRenamed: Boolean = false) {
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

    private fun printSummary(actions: SyncActionCases) {
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

}
