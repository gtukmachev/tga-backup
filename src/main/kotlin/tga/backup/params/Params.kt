package tga.backup.params

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import java.io.File

data class Params(
    val srcRoot: String,
    val dstRoot: String,
    val path: String,
    val dryRun: Boolean,
    val verbose: Boolean,
    val devMode: Boolean,
    val noDeletion: Boolean,
    val noOverriding: Boolean,
    val parallelThreads: Int = 10,
    val yandexUser: String? = null,
    val yandexToken: String? = null,
) {

    val srcFolder: String get() = normalizePath(srcRoot, path)
    val dstFolder: String get() = normalizePath(dstRoot, path)

    override fun toString(): String {
        return """
                    |Params(
                    |   srcRoot='$srcRoot',
                    |   dstRoot='$dstRoot',
                    |   path='$path',
                    |   dryRun=$dryRun,
                    |   verbose=$verbose,
                    |   devMode=$devMode,
                    |   noDeletion=$noDeletion,
                    |   noOverriding=$noOverriding,
                    |   parallelThreads=$parallelThreads,
                    |   yandexUser='$yandexUser',
                    |   yandexToken='${yandexToken?.let { "***" } ?: ""}'
                    |)
                """.trimMargin()
    }
}

fun normalizePath(root: String, relative: String): String {
    if (relative == "*" || relative == "") return root
    val r = root.replace(Regex("[/\\\\]+$"), "")
    val p = relative.replace(Regex("^[/\\\\]+"), "")
    val separator = if (root.contains("://")) "/" else File.separator
    return "$r$separator$p"
}

private val argToConfigMap = mapOf(
    "-sr" to "srcRoot", "--source-root" to "srcRoot",
    "-dr" to "dstRoot", "--destination-root" to "dstRoot",
    "-p" to "path", "--path" to "path",
    "--dry-run" to "dryRun",
    "--verbose" to "verbose",
    "-dev" to "devMode",
    "-nd" to "noDeletion", "--no-deletion" to "noDeletion",
    "-no" to "noOverriding", "--no-overriding" to "noOverriding",
    "-t" to "parallelThreads", "--threads" to "parallelThreads",
    "-yu" to "yandexUser",
    "-yt" to "yandexToken"
)

private val booleanArgs = setOf("--dry-run", "--verbose", "-dev", "-nd", "--no-deletion", "-no", "--no-overriding")

fun Array<String>.readParams(): Params {
    val (profile, argsList) = if (isNotEmpty() && !get(0).startsWith("-")) {
        get(0) to sliceArray(1 until size)
    } else {
        "default" to this
    }

    val cliMap = mutableMapOf<String, Any>()
    var i = 0
    while (i < argsList.size) {
        val arg = argsList[i]
        val configKey = argToConfigMap[arg]
        if (configKey != null) {
            if (booleanArgs.contains(arg)) {
                cliMap[configKey] = true
            } else if (i + 1 < argsList.size) {
                val value = argsList[i + 1]
                cliMap[configKey] = when (configKey) {
                    "parallelThreads" -> value.toInt()
                    else -> value
                }
                i++
            }
        }
        i++
    }

    val updateProfile = argsList.contains("-up") || argsList.contains("--update-profile")

    val cliConfig = ConfigFactory.parseMap(cliMap)

    val profileConfig = if (profile != null) {
        val profileFile = File(System.getProperty("user.home"), ".tga-backup/$profile.conf")
        if (profileFile.exists()) {
            ConfigFactory.parseFile(profileFile)
        } else {
            ConfigFactory.empty()
        }
    } else {
        ConfigFactory.empty()
    }

    val defaultConfig = ConfigFactory.parseResources("application.conf") // loads application.conf only, avoiding system properties clashing with 'path'

    val mergedConfig = cliConfig
        .withFallback(profileConfig)
        .withFallback(defaultConfig)

    val params = Params(
        srcRoot = mergedConfig.getString("srcRoot"),
        dstRoot = mergedConfig.getString("dstRoot"),
        path = mergedConfig.getString("path"),
        dryRun = mergedConfig.getBoolean("dryRun"),
        verbose = mergedConfig.getBoolean("verbose"),
        devMode = mergedConfig.getBoolean("devMode"),
        noDeletion = mergedConfig.getBoolean("noDeletion"),
        noOverriding = mergedConfig.getBoolean("noOverriding"),
        parallelThreads = mergedConfig.getInt("parallelThreads"),
        yandexUser = if (mergedConfig.hasPath("yandexUser")) mergedConfig.getString("yandexUser").let { if (it.isBlank()) null else it } else null,
        yandexToken = if (mergedConfig.hasPath("yandexToken")) mergedConfig.getString("yandexToken").let { if (it.isBlank()) null else it } else null
    )

    if (params.srcRoot.isBlank()) throw ArgumentIsMissed("-sr (--source-root)")
    if (params.dstRoot.isBlank()) throw ArgumentIsMissed("-dr (--destination-root)")

    if (updateProfile && profile != null) {
        updateProfileFile(profile, cliMap, profileConfig, defaultConfig)
    }

    return params
}

private fun updateProfileFile(profileName: String, cliMap: Map<String, Any>, currentProfileConfig: Config, defaultConfig: Config) {
    val profileFile = File(System.getProperty("user.home"), ".tga-backup/$profileName.conf")
    val newConfig = ConfigFactory.parseMap(cliMap)
        .withFallback(currentProfileConfig)
        .withFallback(defaultConfig)
    
    val renderOptions = ConfigRenderOptions.defaults().setOriginComments(false).setJson(false).setFormatted(true)
    val newConfigString = newConfig.root().render(renderOptions)

    if (profileFile.exists()) {
        val currentConfigString = currentProfileConfig.root().render(renderOptions)
        if (currentConfigString == newConfigString) {
            println("Profile '$profileName' is already up to date.")
            return
        }
        println("Changes for profile '$profileName':")
        println("\n--- Current ---")
        println(currentConfigString)
        println("\n--- New ---")
        println(newConfigString)
        print("Do you want to overwrite? (y/N): ")
        val answer = readLine()
        if (answer?.lowercase() != "y") {
            println("Update cancelled.")
            return
        }
    }

    profileFile.parentFile.mkdirs()
    profileFile.writeText(newConfigString)
    println("Profile '$profileName' updated at ${profileFile.absolutePath}")
}

class ArgumentIsMissed(arg: String) : Exception("Argument $arg is expected!")