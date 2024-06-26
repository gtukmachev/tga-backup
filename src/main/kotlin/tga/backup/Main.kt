package tga.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import tga.backup.files.FileInfo
import tga.backup.files.FileOps
import tga.backup.files.buildFileOpsByURL
import tga.backup.logo.printLogo
import tga.backup.params.Params
import tga.backup.params.readParams
import java.io.File

private val logger = KotlinLogging.logger {  }

fun main(args: Array<String>) {

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

    val toCopy = srcFiles - dstFiles
    val toDelete = dstFiles - srcFiles

    if (toCopy.isEmpty() && toDelete.isEmpty()) {
        logger.warn { "The source and destination are already exactly the same. No actions required." }
        return
    }

    logFilesList("\nTo Copy ('${params.srcFolder}' ---> '${params.dstFolder}')", toCopy)
    logFilesList("\nTo Delete (in '${params.dstFolder}')", toDelete)

    print("Continue (Y/N)?>")
    val continueAnswer = readln()
    if (continueAnswer !in setOf("Y", "y")) return

    runCopying(srcFileOps, dstFileOps, params, toCopy)
    runDeleting(dstFileOps, params, toDelete)
}

fun runCopying(srcFileOps: FileOps, dstFileOps: FileOps, params: Params, toCopy: Set<FileInfo>) {
    println("\nCopying:....")
    try {
        if (toCopy.isNotEmpty())
            dstFileOps.copyFiles(srcFileOps, params.srcFolder, toCopy, params.dstFolder, params.dryRun)
    } finally {
        println(".... Copying is finished\n")
    }
}


fun runDeleting(dstFileOps: FileOps, params: Params, toDelete: Set<FileInfo>) {
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
        println("$prefix: <EMPTY>")
    } else {
        println("$prefix: \n")
        val l = filesList.size.toString().length
        filesList.sorted().forEachIndexed { i, f ->
            print("${i.toString().padStart(l)}. [${f.sizeReadable(6)}] ")
            println(f.name)
        }
    }
}
