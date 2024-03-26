package tga.backup.params

data class Params(
    val srcFolder: String,
    val dstFolder: String,
    val dryRun: Boolean,
    val showSource: Boolean,
    val showDestination: Boolean,
)

fun Array<String>.readParams() = Params(
    srcFolder = getArg("-s"),
    dstFolder = getArg("-d"),
    dryRun = getBoolArg("--dry-run"),
    showSource      = getBoolArg("--verbose") || getBoolArg("--show-src"),
    showDestination = getBoolArg("--verbose") || getBoolArg("--show-dst"),
)

fun Array<String>.getArg(arg: String): String = getArgOptional(arg) ?: throw ArgumentIsMissed(arg)

fun Array<String>.getBoolArg(arg: String): Boolean = indexOf(arg) != -1

fun Array<String>.getArgOptional(arg: String): String? {
    val i = indexOf(arg)
    if (i == -1 || i == (size - 1)) return null
    return get(i + 1)
}

class ArgumentIsMissed(arg: String) : Exception("Argument $arg expected")