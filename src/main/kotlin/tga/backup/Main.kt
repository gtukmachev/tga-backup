package tga.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import tga.backup.files.FileInfo
import tga.backup.files.FileOps
import tga.backup.files.buildFileOpsByURL
import tga.backup.files.compareSrcAndDst
import tga.backup.logo.printLogo
import tga.backup.params.Params
import tga.backup.params.readParams
import java.io.File

private val logger = KotlinLogging.logger {  }

fun main(args: Array<String>) {
    System.setProperty("java.rmi.server.hostname", "localhost")

    printLogo()

    val params = args.readParams()
    println("Current folder = '${File(".").canonicalFile.path}'")
    println(params)

    val srcFileOps = buildFileOpsByURL(params.srcFolder, params)
    val dstFileOps = buildFileOpsByURL(params.dstFolder, params)

    val srcFiles =  srcFileOps.getFilesSet(params.srcFolder)
    if (params.showSource) logFilesList("Source", srcFiles)

    val rootDstFolder =  FileInfo("", true, 10L)
    val dstFiles = dstFileOps.getFilesSet(params.dstFolder) - rootDstFolder
    if (params.showDestination) logFilesList("Destination", dstFiles)

    val actions = compareSrcAndDst(srcFiles = srcFiles, dstFiles = dstFiles)

    if (actions.toAddFiles.isEmpty() && actions.toDeleteFiles.isEmpty() && actions.toOverrideFiles.isEmpty()) {
        logger.info { "The source and destination are already exactly the same. No actions required." }
        return
    }

    logFilesList("\nTo Copy ('${params.srcFolder}' ---> '${params.dstFolder}')", actions.toAddFiles)
    logFilesList("\nTo Override ('${params.srcFolder}' ---> '${params.dstFolder}')", actions.toOverrideFiles)
    logFilesList("\nTo Delete (in '${params.dstFolder}')", actions.toDeleteFiles)

    print("Continue (Y/N)?>")
    val continueAnswer = readln()
    if (continueAnswer !in setOf("Y", "y")) return

    runCopying(srcFileOps, dstFileOps, params, actions.toAddFiles, override = false)
    runCopying(srcFileOps, dstFileOps, params, actions.toOverrideFiles, override = true)
    runDeleting(dstFileOps, params, actions.toDeleteFiles)
}

fun runCopying(srcFileOps: FileOps, dstFileOps: FileOps, params: Params, toCopy: Set<FileInfo>, override: Boolean) {
    if (toCopy.isEmpty()) return

    val actionName = if (override) "Overriding" else "Copying"
    println("\n$actionName:....")
    try {
        if (toCopy.isNotEmpty())
            dstFileOps.copyFiles(srcFileOps, params.srcFolder, toCopy, params.dstFolder, params.dryRun, override)
    } finally {
        println(".... $actionName is finished\n")
    }
}


fun runDeleting(dstFileOps: FileOps, params: Params, toDelete: Set<FileInfo>) {
    if (toDelete.isEmpty()) return

    println("\nDeleting:....")
    try {
        if (toDelete.isNotEmpty())
            dstFileOps.deleteFiles(toDelete, params.dstFolder, params.dryRun)
    } finally {
        println(".... Deleting is finished\n")
    }
}


fun logFilesList(prefix: String, filesList: Set<FileInfo>) {
    if (filesList.isEmpty()) {
        // println("$prefix: <EMPTY>")
    } else {
        println("$prefix: \n")
        val l = filesList.size.toString().length
        filesList.sorted().forEachIndexed { i, f ->
            print("${i.toString().padStart(l)}. [${f.sizeReadable(6)}] ")
            println(f.name)
        }
    }
}
