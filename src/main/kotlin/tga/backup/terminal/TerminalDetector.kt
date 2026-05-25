package tga.backup.terminal

data class TerminalCapabilities(
    val isInteractive: Boolean,
    val supportsAnsi: Boolean,
    val width: Int,
    val isDarkTheme: Boolean,
)

class TerminalDetector(
    private val getEnv: (String) -> String? = System::getenv,
    private val getConsole: () -> Any? = { System.console() },
    private val runCommand: (String) -> String? = ::execCommand,
) {
    fun detect(): TerminalCapabilities {
        val isInteractive = getConsole() != null
        val supportsAnsi = detectAnsiSupport(isInteractive)
        val width = detectWidth(isInteractive)
        val isDarkTheme = detectDarkTheme()
        return TerminalCapabilities(isInteractive, supportsAnsi, width, isDarkTheme)
    }

    private fun detectAnsiSupport(isInteractive: Boolean): Boolean {
        if (getEnv("FORCE_COLOR") != null) return isInteractive
        if (!isInteractive) return false
        if (getEnv("NO_COLOR") != null) return false
        if (getEnv("TERM") == "dumb") return false
        val os = System.getProperty("os.name")?.lowercase() ?: ""
        if (os.contains("win")) {
            val version = System.getProperty("os.version")?.toFloatOrNull() ?: 0f
            return version >= 10.0f
        }
        return true
    }

    private fun detectWidth(isInteractive: Boolean): Int {
        if (isInteractive) {
            val sttyOutput = runCommand("stty size 2>/dev/null")
            if (sttyOutput != null) {
                val cols = sttyOutput.trim().split("\\s+".toRegex()).getOrNull(1)?.toIntOrNull()
                if (cols != null && cols > 0) return cols.coerceAtLeast(40)
            }
        }
        val envColumns = getEnv("COLUMNS")?.toIntOrNull()
        if (envColumns != null && envColumns > 0) return envColumns.coerceAtLeast(40)
        return 120
    }

    private fun detectDarkTheme(): Boolean {
        val colorfgbg = getEnv("COLORFGBG") ?: return true
        val bg = colorfgbg.split(";").lastOrNull()?.toIntOrNull() ?: return true
        return bg < 8
    }
}

private fun execCommand(command: String): String? {
    return try {
        val process = ProcessBuilder("/bin/sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode == 0) output else null
    } catch (_: Exception) {
        null
    }
}
