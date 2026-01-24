package tga.backup.log

import tga.backup.files.FileInfo

fun <T> logWrap(prefix: String, eatErrors: Boolean = false, body: () -> T): T? {
    print(prefix)
    return try {
        body().also { print("...ok") }
    } catch (t: Throwable) {
        print("...${t.toLog()}")
        if (!eatErrors) throw t
        null
    } finally {
        println()
    }

}

fun Throwable.toLog() = "${this::class.java.simpleName}: '${this.message}'"

fun formatNumber(number: Number): String {
    val s = number.toString()
    val sb = StringBuilder()
    for (i in s.indices) {
        sb.append(s[i])
        val posFromEnd = s.length - i - 1
        if (posFromEnd > 0 && posFromEnd % 3 == 0) {
            sb.append("`")
        }
    }
    return sb.toString()
}

private const val GB: Long = 1024 * 1024 * 1024
private const val MB: Long = 1024 * 1024
private const val KB: Long = 1024

fun formatFileSize(size: Long, minLength: Int? = null): String {
    val strSize =  when {
        size > GB -> "${formatNumber(size / GB)} g"
        size > MB -> "${formatNumber(size / MB)} m"
        size > KB -> "${formatNumber(size / KB)} k"
        else -> "${formatNumber(size)} b"
    }

    return when {
        (minLength != null && strSize.length < minLength) -> strSize.padStart(minLength)
        else -> strSize
    }

}

fun alignRight(minLength: Int, vararg strs: String): Array<String> {
    val maxLen = maxOf(minLength, strs.maxOf { it.length })
    return strs
        .map { it.padStart(maxLen) }
        .toTypedArray()
}

fun formatNumbersAndAlignRight(minLength: Int, vararg nums: Number) = alignRight(minLength, *nums.map { formatNumber(it) }.toTypedArray())

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    fun format(num: Long, unit: String) = "${num.toString().padStart(2)}$unit"

    return when {
        days > 0 -> "${format(days, "d")} ${format(hours, "h")} ${format(minutes, "m")} ${format(seconds, "s")}"
        hours > 0 -> "${format(hours, "h")} ${format(minutes, "m")} ${format(seconds, "s")}"
        minutes > 0 -> "${format(minutes, "m")} ${format(seconds, "s")}"
        else -> format(seconds, "s")
    }
}

private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger {  }

fun logPhase(phaseName: String) {
    val timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    logger.warn { "[$timestamp] Phase: $phaseName" }
}

fun logPhaseDuration(phaseName: String, durationMs: Long) {
    val durationSec = durationMs / 1000.0
    logger.warn { "Phase '$phaseName' completed in %.2f seconds".format(durationSec) }
}

fun logFilesList(prefix: String, filesList: Set<FileInfo>) {
    if (filesList.isEmpty()) {
        // println("$prefix: <EMPTY>")
    } else {
        println("$prefix: \n")
        val l = formatNumber(filesList.size).length
        filesList.sorted().forEachIndexed { i, f ->
            print("${formatNumber(i).padStart(l)}. [${formatFileSize(f.size, 6)}] ")
            println(f.name)
        }
    }
}

