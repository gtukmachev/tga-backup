package tga.backup.params

data class Params(
    val srcFolder: String,
    val dstFolder: String,
    val dryRun: Boolean,
    val verbose: Boolean,
    val devMode: Boolean,
    val parallelThreads: Int = 10,
    val yandexUser: String? = null,
    val yandexToken: String? = null,
) {

    override fun toString(): String {
        return """
                    |Params(
                    |   srcFolder='$srcFolder',
                    |   dstFolder='$dstFolder',
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

fun Array<String>.readParams() = Params(
    srcFolder = getArg("-s"),
    dstFolder = getArg("-d"),
    dryRun = getBoolArg("--dry-run"),
    verbose = getBoolArg("--verbose"),
    devMode = getBoolArg("-dev"),
    parallelThreads = getArgOptional("-p")?.toInt() ?: 10,
    yandexUser = getArgOptional("-yu") ?: System.getenv("BACKUP_YANDEX_USER"),
    yandexToken = getArgOptional("-yt") ?: System.getenv("BACKUP_YANDEX_TOKEN"),
)

fun Array<String>.getArg(arg: String): String = getArgOptional(arg) ?: throw ArgumentIsMissed(arg)

fun Array<String>.getBoolArg(arg: String): Boolean = indexOf(arg) != -1

fun Array<String>.getArgOptional(arg: String): String? {
    val i = indexOf(arg)
    if (i == -1 || i == (size - 1)) return null
    return get(i + 1)
}

class ArgumentIsMissed(arg: String) : Exception("Argument $arg is expected!")