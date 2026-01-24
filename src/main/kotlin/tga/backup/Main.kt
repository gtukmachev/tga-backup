package tga.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import tga.backup.duplicates.runDuplicatesMode
import tga.backup.files.FileInfo
import tga.backup.files.buildFileOpsByURL
import tga.backup.log.logFilesList
import tga.backup.log.logPhase
import tga.backup.log.logPhaseDuration
import tga.backup.logo.printLogo
import tga.backup.params.readParams
import tga.backup.scripts.backup.runBackupScript
import java.io.File
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {  }

fun main(args: Array<String>) {
    try {
        System.setProperty("java.rmi.server.hostname", "localhost")

        printLogo()

        logPhase("Parameter Parsing & Validation")
        val params = args.readParams()

        println("Current folder = '${File(".").canonicalFile.path}'")
        println(params)

        // Required trees loading
        val isBackupMode = params.mode == "backup" || params.mode == "sync"
        val isDuplicatesMode = params.mode == "duplicates"

        val srcFileOps = if (isBackupMode && params.srcRoot.isNotEmpty()) buildFileOpsByURL(params.srcFolder, params) else null
        val dstFileOps = if (isBackupMode && params.dstRoot.isNotEmpty()) buildFileOpsByURL(params.dstFolder, params) else null
        val targetFileOps = if (isDuplicatesMode && params.target.isNotEmpty()) buildFileOpsByURL(params.targetFolder, params) else null

        val srcFiles = srcFileOps?.let {
            logPhase("Source Scanning")
            val start = System.currentTimeMillis()
            println("\nListing source files:")
            val files = it.getFilesSet(params.srcFolder, throwIfNotExist = true)
            if (params.verbose) logFilesList("Source", files)
            logPhaseDuration("Source Scanning", System.currentTimeMillis() - start)
            files
        }

        val dstFiles = dstFileOps?.let {
            logPhase("Destination Scanning")
            val start = System.currentTimeMillis()
            val rootDstFolder = FileInfo("", true, 10L)
            val files = it.getFilesSet(params.dstFolder, throwIfNotExist = false) - rootDstFolder
            if (params.verbose) logFilesList("Destination", files)
            logPhaseDuration("Destination Scanning", System.currentTimeMillis() - start)
            files
        }

        val targetFiles = targetFileOps?.let {
            logPhase("Target Scanning")
            val start = System.currentTimeMillis()
            val files = it.getFilesSet(params.targetFolder, throwIfNotExist = true)
            if (params.verbose) logFilesList("Target", files)
            logPhaseDuration("Target Scanning", System.currentTimeMillis() - start)
            files
        }

        // Route to appropriate mode
        when (params.mode) {
            "duplicates" -> {
                if (targetFileOps == null || targetFiles == null) {
                    throw IllegalArgumentException("Target folder is required for duplicates mode")
                }
                try {
                    runDuplicatesMode(params, targetFileOps, targetFiles)
                } finally {
                    targetFileOps.close()
                }
            }
            "backup", "sync" -> {
                if (srcFileOps == null || dstFileOps == null || srcFiles == null || dstFiles == null) {
                    throw IllegalArgumentException("Source and destination folders are required for backup mode")
                }
                try {
                    runBackupScript(params, srcFileOps, dstFileOps, srcFiles, dstFiles)
                } finally {
                    srcFileOps.close()
                    dstFileOps.close()
                }
            }
            else -> {
                throw IllegalArgumentException("Unknown mode: ${params.mode}. Supported modes: backup, duplicates")
            }
        }

    } catch (t: Throwable) {
        logger.error(t) { "Operation failed with an exception." }
        exitProcess(-1)
    }
}

