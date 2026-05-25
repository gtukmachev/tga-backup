package tga.backup.terminal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TerminalDetectorTest {

    private fun detector(
        env: Map<String, String> = emptyMap(),
        hasConsole: Boolean = true,
        sttyOutput: String? = null,
        systemProperties: Map<String, String> = emptyMap(),
    ) = TerminalDetector(
        getEnv = { env[it] },
        getConsole = { if (hasConsole) Object() else null },
        runCommand = { sttyOutput },
        getSystemProperty = { systemProperties[it] },
    )

    @Nested
    inner class IsInteractive {
        @Test
        fun `returns true when console is available`() {
            val caps = detector(hasConsole = true).detect()
            assertThat(caps.isInteractive).isTrue()
        }

        @Test
        fun `returns false when console is null`() {
            val caps = detector(hasConsole = false).detect()
            assertThat(caps.isInteractive).isFalse()
        }
    }

    @Nested
    inner class SupportsAnsi {
        @Test
        fun `returns true for interactive terminal with no restrictions`() {
            val caps = detector(hasConsole = true).detect()
            assertThat(caps.supportsAnsi).isTrue()
        }

        @Test
        fun `returns false when not interactive`() {
            val caps = detector(hasConsole = false).detect()
            assertThat(caps.supportsAnsi).isFalse()
        }

        @Test
        fun `returns false when NO_COLOR is set`() {
            val caps = detector(env = mapOf("NO_COLOR" to "1"), hasConsole = true).detect()
            assertThat(caps.supportsAnsi).isFalse()
        }

        @Test
        fun `returns false when TERM is dumb`() {
            val caps = detector(env = mapOf("TERM" to "dumb"), hasConsole = true).detect()
            assertThat(caps.supportsAnsi).isFalse()
        }

        @Test
        fun `FORCE_COLOR enables ansi when interactive`() {
            val caps = detector(
                env = mapOf("FORCE_COLOR" to "1", "NO_COLOR" to "1"),
                hasConsole = true,
            ).detect()
            assertThat(caps.supportsAnsi).isTrue()
        }

        @Test
        fun `FORCE_COLOR does not enable ansi when not interactive`() {
            val caps = detector(
                env = mapOf("FORCE_COLOR" to "1"),
                hasConsole = false,
            ).detect()
            assertThat(caps.supportsAnsi).isFalse()
        }

        @Test
        fun `returns true on Windows 10 or later`() {
            val caps = detector(
                hasConsole = true,
                systemProperties = mapOf("os.name" to "Windows 10", "os.version" to "10.0"),
            ).detect()
            assertThat(caps.supportsAnsi).isTrue()
        }

        @Test
        fun `returns false on old Windows`() {
            val caps = detector(
                hasConsole = true,
                systemProperties = mapOf("os.name" to "Windows 7", "os.version" to "6.1"),
            ).detect()
            assertThat(caps.supportsAnsi).isFalse()
        }

        @Test
        fun `returns true on non-Windows unix-like OS`() {
            val caps = detector(
                hasConsole = true,
                systemProperties = mapOf("os.name" to "Mac OS X"),
            ).detect()
            assertThat(caps.supportsAnsi).isTrue()
        }
    }

    @Nested
    inner class Width {
        @Test
        fun `parses width from stty output`() {
            val caps = detector(sttyOutput = "24 80", hasConsole = true).detect()
            assertThat(caps.width).isEqualTo(80)
        }

        @Test
        fun `parses width from stty output with extra whitespace`() {
            val caps = detector(sttyOutput = "  50  120  ", hasConsole = true).detect()
            assertThat(caps.width).isEqualTo(120)
        }

        @Test
        fun `falls back to COLUMNS when stty fails`() {
            val caps = detector(
                env = mapOf("COLUMNS" to "100"),
                hasConsole = true,
                sttyOutput = null,
            ).detect()
            assertThat(caps.width).isEqualTo(100)
        }

        @Test
        fun `falls back to COLUMNS when not interactive`() {
            val caps = detector(
                env = mapOf("COLUMNS" to "90"),
                hasConsole = false,
                sttyOutput = "24 80",
            ).detect()
            assertThat(caps.width).isEqualTo(90)
        }

        @Test
        fun `falls back to 120 when nothing available`() {
            val caps = detector(hasConsole = false, sttyOutput = null).detect()
            assertThat(caps.width).isEqualTo(120)
        }

        @Test
        fun `clamps to minimum 40`() {
            val caps = detector(sttyOutput = "24 20", hasConsole = true).detect()
            assertThat(caps.width).isEqualTo(40)
        }

        @Test
        fun `clamps COLUMNS to minimum 40`() {
            val caps = detector(
                env = mapOf("COLUMNS" to "10"),
                hasConsole = false,
            ).detect()
            assertThat(caps.width).isEqualTo(40)
        }
    }

    @Nested
    inner class IsDarkTheme {
        @Test
        fun `defaults to true when COLORFGBG is not set`() {
            val caps = detector().detect()
            assertThat(caps.isDarkTheme).isTrue()
        }

        @Test
        fun `detects dark theme when background is less than 8`() {
            val caps = detector(env = mapOf("COLORFGBG" to "15;0")).detect()
            assertThat(caps.isDarkTheme).isTrue()
        }

        @Test
        fun `detects light theme when background is 8 or more`() {
            val caps = detector(env = mapOf("COLORFGBG" to "0;15")).detect()
            assertThat(caps.isDarkTheme).isFalse()
        }

        @Test
        fun `defaults to true when COLORFGBG has invalid format`() {
            val caps = detector(env = mapOf("COLORFGBG" to "garbage")).detect()
            assertThat(caps.isDarkTheme).isTrue()
        }

        @Test
        fun `handles three-value COLORFGBG format`() {
            val caps = detector(env = mapOf("COLORFGBG" to "15;0;0")).detect()
            assertThat(caps.isDarkTheme).isTrue()
        }
    }
}
