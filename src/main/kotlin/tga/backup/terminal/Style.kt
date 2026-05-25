package tga.backup.terminal

private val ESC = 27.toChar()
private val ANSI_REGEX = Regex("\u001b\\[[0-9;]*m")

enum class Color(val darkCode: Int, val lightCode: Int) {
    SUCCESS(32, 32),
    WARNING(33, 33),
    ERROR(31, 91),
    INFO(36, 34),
    MUTED(90, 37),
    ACCENT(96, 34),
}

fun style(
    text: String,
    color: Color? = null,
    bold: Boolean = false,
    dim: Boolean = false,
    supportsAnsi: Boolean = Terminal.supportsAnsi,
    isDarkTheme: Boolean = Terminal.isDarkTheme,
): String {
    if (!supportsAnsi) return text
    val codes = buildList {
        if (bold) add(1)
        if (dim) add(2)
        if (color != null) add(if (isDarkTheme) color.darkCode else color.lightCode)
    }
    if (codes.isEmpty()) return text
    val prefix = "$ESC[${codes.joinToString(";")}m"
    return "$prefix$text$ESC[0m"
}

fun stripAnsi(text: String): String = text.replace(ANSI_REGEX, "")

fun visibleLength(text: String): Int = stripAnsi(text).length

fun truncateToWidth(text: String, maxWidth: Int): String {
    if (maxWidth <= 0) return ""
    if (visibleLength(text) <= maxWidth) return text

    val sb = StringBuilder()
    var visible = 0
    val target = maxWidth - 1
    var i = 0
    while (i < text.length && visible < target) {
        if (text[i] == ESC && i + 1 < text.length && text[i + 1] == '[') {
            val seqStart = i
            i += 2
            while (i < text.length && text[i] != 'm') i++
            if (i < text.length) i++
            sb.append(text, seqStart, i)
        } else {
            sb.append(text[i])
            visible++
            i++
        }
    }
    sb.append("\u2026")
    sb.append("$ESC[0m")
    return sb.toString()
}

object Icons {
    var supportsAnsi: Boolean = Terminal.supportsAnsi
        internal set

    val CHECK: String get() = if (supportsAnsi) "\u2714" else "[OK]"
    val CROSS: String get() = if (supportsAnsi) "\u2716" else "[FAIL]"
    val WARNING: String get() = if (supportsAnsi) "\u26A0" else "[!]"
    val INFO: String get() = if (supportsAnsi) "\u2139" else "[i]"
    val ARROW: String get() = if (supportsAnsi) "\u2192" else "->"
    val BULLET: String get() = if (supportsAnsi) "\u2022" else "*"
    val FOLDER: String get() = if (supportsAnsi) "📁" else "[D]"
    val FILE: String get() = if (supportsAnsi) "📄" else "[F]"
}
