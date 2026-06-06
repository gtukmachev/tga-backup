package tga.backup.terminal

import java.io.PrintStream
import java.util.concurrent.TimeUnit

object Terminal {
    private val capabilities: TerminalCapabilities by lazy { TerminalDetector().detect() }

    val isInteractive: Boolean get() = capabilities.isInteractive
    val supportsAnsi: Boolean get() = capabilities.supportsAnsi
    val width: Int get() = capabilities.width
    val isDarkTheme: Boolean get() = capabilities.isDarkTheme

    fun setupUtf8Console() {
        if (System.getProperty("os.name").lowercase().startsWith("win")) {
            System.setOut(PrintStream(System.out, true, "UTF-8"))
            System.setErr(PrintStream(System.err, true, "UTF-8"))
            try {
                ProcessBuilder("cmd", "/c", "chcp 65001 >nul")
                    .inheritIO()
                    .start()
                    .waitFor(2, TimeUnit.SECONDS)
            } catch (_: Exception) {}
        }
    }
}
