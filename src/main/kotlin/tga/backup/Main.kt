package tga.backup

import tga.backup.files.FileOps
import tga.backup.logo.printLogo
import tga.backup.params.readParams
import java.io.File

fun main(args: Array<String>) {
    printLogo()

    val params = args.readParams()
    println("Current folder = '${File(".").canonicalFile.path}'")
    println(params)

    val fileOps = FileOps()

    val srcFiles = fileOps.getFilesSet(params.srcFolder)
    val dstFiles = fileOps.getFilesSet(params.dstFolder)

    logFilesList("Source", srcFiles)
    logFilesList("Destination", dstFiles)

    val toCopy = srcFiles - dstFiles
    val toDelete = dstFiles - srcFiles

    logFilesList("To Copy ('${params.srcFolder}' ---> '${params.dstFolder}')", toCopy)
    logFilesList("To Delete (in '${params.dstFolder}')", toDelete)

    println("\nCopying:....")
    try {
        fileOps.copyFiles(params.srcFolder, toCopy, params.dstFolder, params.dryRun)
    } finally {
        println(".... Copying is finished\n")
    }

    println("\nDeleting:....")
    try {
        fileOps.deleteFiles(toDelete, params.dstFolder, params.dryRun)
    } finally {
        println(".... Deleting is finished\n")
    }

}

fun logFilesList(prefix: String, filesList: Set<String>) {
    if (filesList.isEmpty()) {
        println("$prefix: <EMPTY>");
    } else {
        println("$prefix: \n${filesList.sorted().joinToString(separator = "\n")}\n")
    }
}