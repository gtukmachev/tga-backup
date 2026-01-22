package tga.backup.params

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import java.io.File

data class Params(
    val profile: String = "default",
    val mode: String = "sync",
    val srcRoot: String = "",
    val dstRoot: String = "",
    val path: String = "",
    val target: String = "",
    val dryRun: Boolean = false,
    val verbose: Boolean = false,
    val devMode: Boolean = false,
    val noDeletion: Boolean = false,
    val noOverriding: Boolean = false,
    val parallelThreads: Int = 10,
    val yandexUser: String? = null,
    val yandexToken: String? = null,
    val exclude: List<String> = emptyList(),
    val remoteCache: Boolean = false,
) {

    val srcFolder: String get() = normalizePath(srcRoot, path)
    val dstFolder: String get() = normalizePath(dstRoot, path)
    val targetFolder: String get() = when (target) {
        "src" -> srcFolder
        "dst" -> dstFolder
        else -> throw IllegalArgumentException("Invalid target: $target. Must be 'src' or 'dst'")
    }

    override fun toString(): String {
        return """
                    |Params(
                    |   profile='$profile',
                    |   mode='$mode',
                    |   srcRoot='$srcRoot',
                    |   dstRoot='$dstRoot',
                    |   path='$path',
                    |   target='$target',
                    |   dryRun=$dryRun,
                    |   verbose=$verbose,
                    |   devMode=$devMode,
                    |   noDeletion=$noDeletion,
                    |   noOverriding=$noOverriding,
                    |   parallelThreads=$parallelThreads,
                    |   yandexUser='$yandexUser',
                    |   yandexToken='${yandexToken?.let { "***" } ?: ""}',
                    |   exclude=$exclude,
                    |   remoteCache=$remoteCache
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
    "-m" to "mode", "--mode" to "mode",
    "-sr" to "srcRoot", "--source-root" to "srcRoot",
    "-dr" to "dstRoot", "--destination-root" to "dstRoot",
    "-p" to "path", "--path" to "path",
    "-ta" to "target", "--target" to "target",
    "--dry-run" to "dryRun",
    "--verbose" to "verbose",
    "-dev" to "devMode",
    "-nd" to "noDeletion", "--no-deletion" to "noDeletion",
    "-no" to "noOverriding", "--no-overriding" to "noOverriding",
    "-t" to "parallelThreads", "--threads" to "parallelThreads",
    "-yu" to "yandexUser",
    "-yt" to "yandexToken",
    "-x" to "exclude", "--exclude" to "exclude",
    "-rm" to "remoteCache", "--remote-cache" to "remoteCache"
)

private val booleanArgs = setOf("--dry-run", "--verbose", "-dev", "-nd", "--no-deletion", "-no", "--no-overriding", "-rm", "--remote-cache")

private val specialArgs = setOf("-up", "--update-profile")

fun Array<String>.readParams(): Params {
    val (profile, argsList) = if (isNotEmpty() && !get(0).startsWith("-")) {
        get(0) to sliceArray(1 until size)
    } else {
        "default" to this
    }

    val cliMap = mutableMapOf<String, Any>()
    val excludeList = mutableListOf<String>()
    var i = 0
    while (i < argsList.size) {
        val arg = argsList[i]
        
        val configKey = argToConfigMap[arg]
        if (configKey != null) {
            if (booleanArgs.contains(arg)) {
                cliMap[configKey] = true
            } else if (i + 1 < argsList.size) {
                val value = argsList[i + 1]
                when (configKey) {
                    "exclude" -> excludeList.add(value)
                    "parallelThreads" -> cliMap[configKey] = value.toInt()
                    else -> cliMap[configKey] = value
                }
                i++
            }
        } else {
            // Argument is not recognized - check if it's a special arg, otherwise throw
            if (!specialArgs.contains(arg)) {
                throw UnrecognizedArgument(arg)
            }
        }
        i++
    }
    if (excludeList.isNotEmpty()) {
        cliMap["exclude"] = excludeList
    }

    val updateProfile = argsList.contains("-up") || argsList.contains("--update-profile")

    val cliConfig = ConfigFactory.parseMap(cliMap)

    val profileFile = File(System.getProperty("user.home"), ".tga-backup/$profile.conf")
    val profileConfig = if (profileFile.exists()) {
        ConfigFactory.parseFile(profileFile)
    } else {
        ConfigFactory.empty()
    }

    val defaultConfig = ConfigFactory.parseResources("application.conf") // loads application.conf only, avoiding system properties clashing with 'path'

    val mergedConfig = cliConfig
        .withFallback(profileConfig)
        .withFallback(defaultConfig)

    val mode = if (mergedConfig.hasPath("mode")) mergedConfig.getString("mode") else "sync"
    val target = if (mergedConfig.hasPath("target")) mergedConfig.getString("target") else "src"
    
    // For duplicates mode, set default srcRoot to current directory if not specified
    val srcRoot = if (mergedConfig.hasPath("srcRoot")) mergedConfig.getString("srcRoot") else ""
    val dstRoot = if (mergedConfig.hasPath("dstRoot")) mergedConfig.getString("dstRoot") else ""
    val path = if (mergedConfig.hasPath("path")) mergedConfig.getString("path") else ""
    
    val params = Params(
        profile = profile,
        mode = mode,
        srcRoot = srcRoot,
        dstRoot = dstRoot,
        path = path,
        target = target,
        dryRun = mergedConfig.getBoolean("dryRun"),
        verbose = mergedConfig.getBoolean("verbose"),
        devMode = mergedConfig.getBoolean("devMode"),
        noDeletion = mergedConfig.getBoolean("noDeletion"),
        noOverriding = mergedConfig.getBoolean("noOverriding"),
        parallelThreads = mergedConfig.getInt("parallelThreads"),
        yandexUser = if (mergedConfig.hasPath("yandexUser")) mergedConfig.getString("yandexUser").let { if (it.isBlank()) null else it } else null,
        yandexToken = if (mergedConfig.hasPath("yandexToken")) mergedConfig.getString("yandexToken").let { if (it.isBlank()) null else it } else null,
        exclude = if (mergedConfig.hasPath("exclude")) mergedConfig.getStringList("exclude") else emptyList(),
        remoteCache = mergedConfig.getBoolean("remoteCache")
    )

    // Mode-specific validation
    when (params.mode) {
        "sync" -> {
            if (params.srcRoot.isBlank()) throw ArgumentIsMissed("-sr (--source-root)")
            if (params.dstRoot.isBlank()) throw ArgumentIsMissed("-dr (--destination-root)")
        }
        "duplicates" -> {
            // For duplicates mode, we need at least one root specified
            val targetRoot = when (params.target) {
                "src" -> params.srcRoot
                "dst" -> params.dstRoot
                else -> throw IllegalArgumentException("Invalid target: ${params.target}. Must be 'src' or 'dst'")
            }
            if (targetRoot.isBlank()) {
                // Default to current directory
                val defaultRoot = System.getProperty("user.dir")
                return params.copy(
                    srcRoot = if (params.target == "src") defaultRoot else params.srcRoot,
                    dstRoot = if (params.target == "dst") defaultRoot else params.dstRoot
                )
            }
        }
        else -> throw IllegalArgumentException("Unknown mode: ${params.mode}. Supported modes: sync, duplicates")
    }

    if (updateProfile) {
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

class UnrecognizedArgument(arg: String) : Exception("Unrecognized argument: $arg")