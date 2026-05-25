package tga.backup.terminal

object Terminal {
    private val capabilities: TerminalCapabilities = TerminalDetector().detect()

    val isInteractive: Boolean get() = capabilities.isInteractive
    val supportsAnsi: Boolean get() = capabilities.supportsAnsi
    val width: Int get() = capabilities.width
    val isDarkTheme: Boolean get() = capabilities.isDarkTheme
}
