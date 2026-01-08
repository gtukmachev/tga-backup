package tga.backup.params

import java.io.File

data class Params(
    val srcRoot: String,
    val dstRoot: String,
    val path: String,
    val dryRun: Boolean,
    val verbose: Boolean,
    val devMode: Boolean,
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
                    |   parallelThreads=$parallelThreads,
                    |   yandexUser='$yandexUser',
                    |   yandexToken='${yandexToken?.let{"***"} ?: ""}'
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

fun Array<String>.readParams() = Params(
    srcRoot = getArg("-sr", "--source-root"),
    dstRoot = getArg("-dr", "--destination-root"),
    path = getArgOptional("-p", "--path") ?: "*",
    dryRun = getBoolArg("--dry-run"),
    verbose = getBoolArg("--verbose"),
    devMode = getBoolArg("-dev"),
    parallelThreads = (getArgOptional("-t", "--threads") ?: "10").toInt(),
    yandexUser = getArgOptional("-yu") ?: System.getenv("BACKUP_YANDEX_USER"),
    yandexToken = getArgOptional("-yt") ?: System.getenv("BACKUP_YANDEX_TOKEN"),
)

fun Array<String>.getArg(vararg args: String): String = getArgOptional(*args) ?: throw ArgumentIsMissed(args.joinToString(", "))

fun Array<String>.getBoolArg(arg: String): Boolean = indexOf(arg) != -1

fun Array<String>.getArgOptional(vararg args: String): String? {
    for (arg in args) {
        val i = indexOf(arg)
        if (i != -1 && i < (size - 1)) return get(i + 1)
    }
    return null
}

class ArgumentIsMissed(arg: String) : Exception("Argument $arg is expected!")