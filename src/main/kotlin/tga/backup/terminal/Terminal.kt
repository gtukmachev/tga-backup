package tga.backup.terminal

import java.io.PrintStream
import java.util.concurrent.TimeUnit

object Terminal {
    private val detector = TerminalDetector()
    private val capabilities: TerminalCapabilities by lazy { detector.detect() }

    private var cachedWidth: Int = 0
    private var widthCacheTime: Long = 0
    private const val WIDTH_CACHE_MS = 2000L

    val isInteractive: Boolean get() = capabilities.isInteractive
    val supportsAnsi: Boolean get() = capabilities.supportsAnsi
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

    val width: Int get() {
        if (!isInteractive) return capabilities.width
        val now = System.currentTimeMillis()
        if (now - widthCacheTime > WIDTH_CACHE_MS) {
            cachedWidth = detector.detectWidth()
            widthCacheTime = now
        }
        return cachedWidth
    }
}
