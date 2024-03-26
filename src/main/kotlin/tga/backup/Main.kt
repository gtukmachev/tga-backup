package tga.backup

import tga.backup.files.FileOps
import tga.backup.logo.printLogo
import tga.backup.params.Params
import tga.backup.params.readParams
import java.io.File

val fileOps = FileOps()

fun main(args: Array<String>) {
    printLogo()

    val params = args.readParams()
    println("Current folder = '${File(".").canonicalFile.path}'")
    println(params)

    val srcFiles = fileOps.getFilesSet(params.srcFolder)
    if (params.showSource) logFilesList("Source", srcFiles)

    val dstFiles = fileOps.getFilesSet(params.dstFolder)
    if (params.showDestination) logFilesList("Destination", dstFiles)

    val toCopy = srcFiles - dstFiles
    val toDelete = dstFiles - srcFiles

    if (toCopy.isEmpty() && toDelete.isEmpty()) {
        println("The destination folder is in sync with the source one.")
    }


    logFilesList("\nTo Copy ('${params.srcFolder}' ---> '${params.dstFolder}')", toCopy)
    logFilesList("\nTo Delete (in '${params.dstFolder}')", toDelete)


    if (toCopy.isNotEmpty() || toDelete.isNotEmpty()) {
        print("Continue (Y/N)?>")
        val continueAnswer = readln()
        if (continueAnswer !in setOf("Y", "y")) return
    }

    runCopying(params, toCopy)
    runDeleting(params,  toDelete)

}

fun runCopying(params: Params, toCopy: Set<String>) {
    println("\nCopying:....")
    try {
        if (toCopy.isNotEmpty())
            fileOps.copyFiles(params.srcFolder, toCopy, params.dstFolder, params.dryRun)
    } finally {
        println(".... Copying is finished\n")
    }
}


fun runDeleting(params: Params, toDelete: Set<String>) {
    println("\nDeleting:....")
    try {
        if (toDelete.isNotEmpty())
            fileOps.deleteFiles(toDelete, params.dstFolder, params.dryRun)
    } finally {
        println(".... Deleting is finished\n")
    }
}


fun logFilesList(prefix: String, filesList: Set<String>) {
    if (filesList.isEmpty()) {
        println("$prefix: <EMPTY>");
    } else {
        println("$prefix: \n${filesList.sorted().joinToString(separator = "\n")}")
    }
}