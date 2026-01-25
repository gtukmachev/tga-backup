package tga.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import tga.backup.log.logPhase
import tga.backup.logo.printLogo
import tga.backup.params.Params
import tga.backup.params.readParams
import tga.backup.scripts.Script
import java.io.File
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {  }

fun main(args: Array<String>) {
    try {
        System.setProperty("java.rmi.server.hostname", "localhost")

        printLogo()
        println("Current folder = '${File(".").canonicalFile.path}'")

        logPhase("Parameter Parsing & Validation")
        val params = readParams(args)
        println(params)

        val script = instantiateScript(params)
        script.run()

    } catch (t: Throwable) {
        val rootCause = generateSequence(t) { it.cause }.last()
        logger.error(rootCause) { "Operation failed: ${rootCause.message}" }
        exitProcess(-1)
    }
}

private fun instantiateScript(params: Params): Script {
    val camelCaseName = if (params.mode == "sync") "Backup" 
    else params.mode
        .split('-', '_')
        .joinToString("") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }

    val scriptClassName = "tga.backup.scripts.$camelCaseName" + "Script"

    return try {
        val scriptClass = Class.forName(scriptClassName)
        val constructor = scriptClass.getConstructor(Params::class.java)
        constructor.newInstance(params) as Script
    } catch (e: ClassNotFoundException) {
        throw IllegalArgumentException("Unknown mode: ${params.mode}. (Expected class $scriptClassName not found)", e)
    } catch (e: Exception) {
        throw RuntimeException("Failed to instantiate script for mode: ${params.mode}", e)
    }
}

