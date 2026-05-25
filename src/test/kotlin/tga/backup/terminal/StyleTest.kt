package tga.backup.terminal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StyleTest {

    private val ESC = 27.toChar()

    @Nested
    inner class StyleFunction {
        @Test
        fun `returns plain text when supportsAnsi is false`() {
            val result = style("hello", Color.SUCCESS, supportsAnsi = false)
            assertThat(result).isEqualTo("hello")
        }

        @Test
        fun `wraps text with color code when supportsAnsi is true`() {
            val result = style("hello", Color.SUCCESS, supportsAnsi = true, isDarkTheme = true)
            assertThat(result).isEqualTo("${ESC}[32mhello${ESC}[0m")
        }

        @Test
        fun `applies bold attribute`() {
            val result = style("hello", bold = true, supportsAnsi = true, isDarkTheme = true)
            assertThat(result).isEqualTo("${ESC}[1mhello${ESC}[0m")
        }

        @Test
        fun `applies dim attribute`() {
            val result = style("hello", dim = true, supportsAnsi = true, isDarkTheme = true)
            assertThat(result).isEqualTo("${ESC}[2mhello${ESC}[0m")
        }

        @Test
        fun `combines bold and color`() {
            val result = style("hello", Color.ERROR, bold = true, supportsAnsi = true, isDarkTheme = true)
            assertThat(result).isEqualTo("${ESC}[1;31mhello${ESC}[0m")
        }

        @Test
        fun `uses dark theme codes by default`() {
            val result = style("hi", Color.INFO, supportsAnsi = true, isDarkTheme = true)
            assertThat(result).isEqualTo("${ESC}[36mhi${ESC}[0m")
        }

        @Test
        fun `uses light theme codes when isDarkTheme is false`() {
            val result = style("hi", Color.INFO, supportsAnsi = true, isDarkTheme = false)
            assertThat(result).isEqualTo("${ESC}[34mhi${ESC}[0m")
        }

        @Test
        fun `returns text unchanged when no attributes specified`() {
            val result = style("hello", supportsAnsi = true, isDarkTheme = true)
            assertThat(result).isEqualTo("hello")
        }
    }

    @Nested
    inner class StripAnsiFunction {
        @Test
        fun `removes ANSI codes from styled text`() {
            val styled = "${ESC}[32mhello${ESC}[0m"
            assertThat(stripAnsi(styled)).isEqualTo("hello")
        }

        @Test
        fun `returns plain text unchanged`() {
            assertThat(stripAnsi("hello")).isEqualTo("hello")
        }

        @Test
        fun `handles multiple ANSI sequences`() {
            val styled = "${ESC}[1;32mhello${ESC}[0m ${ESC}[31mworld${ESC}[0m"
            assertThat(stripAnsi(styled)).isEqualTo("hello world")
        }
    }

    @Nested
    inner class VisibleLengthFunction {
        @Test
        fun `returns correct length for plain text`() {
            assertThat(visibleLength("hello")).isEqualTo(5)
        }

        @Test
        fun `ignores ANSI escape sequences`() {
            val styled = "${ESC}[32mhello${ESC}[0m"
            assertThat(visibleLength(styled)).isEqualTo(5)
        }
    }

    @Nested
    inner class TruncateToWidthFunction {
        @Test
        fun `returns text unchanged when within limit`() {
            assertThat(truncateToWidth("hello", 10)).isEqualTo("hello")
        }

        @Test
        fun `truncates with ellipsis when exceeding limit`() {
            val result = truncateToWidth("hello world", 6)
            assertThat(stripAnsi(result)).isEqualTo("hello…")
        }

        @Test
        fun `handles text with embedded ANSI codes`() {
            val styled = "${ESC}[32mhello world${ESC}[0m"
            val result = truncateToWidth(styled, 6)
            assertThat(stripAnsi(result)).isEqualTo("hello…")
            assertThat(result).contains("${ESC}[32m")
        }

        @Test
        fun `returns empty string when maxWidth is zero`() {
            assertThat(truncateToWidth("hello", 0)).isEmpty()
        }

        @Test
        fun `truncates to single character plus ellipsis`() {
            val result = truncateToWidth("hello", 2)
            assertThat(stripAnsi(result)).isEqualTo("h…")
        }
    }

    @Nested
    inner class IconsTest {
        @Test
        fun `returns unicode icons when supportsAnsi is true`() {
            val savedValue = Icons.supportsAnsi
            try {
                Icons.supportsAnsi = true
                assertThat(Icons.CHECK).isEqualTo("✔")
                assertThat(Icons.CROSS).isEqualTo("✖")
                assertThat(Icons.WARNING).isEqualTo("⚠")
                assertThat(Icons.ARROW).isEqualTo("→")
                assertThat(Icons.BULLET).isEqualTo("•")
            } finally {
                Icons.supportsAnsi = savedValue
            }
        }

        @Test
        fun `returns ASCII fallbacks when supportsAnsi is false`() {
            val savedValue = Icons.supportsAnsi
            try {
                Icons.supportsAnsi = false
                assertThat(Icons.CHECK).isEqualTo("[OK]")
                assertThat(Icons.CROSS).isEqualTo("[FAIL]")
                assertThat(Icons.WARNING).isEqualTo("[!]")
                assertThat(Icons.ARROW).isEqualTo("->")
                assertThat(Icons.BULLET).isEqualTo("*")
            } finally {
                Icons.supportsAnsi = savedValue
            }
        }
    }
}
