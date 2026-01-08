package tga.backup.log

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoggingTest {

    @Test
    fun testFormatTime() {
        assertThat(formatTime(500)).isEqualTo(" 0s")
        assertThat(formatTime(1000)).isEqualTo(" 1s")
        assertThat(formatTime(59000)).isEqualTo("59s")
        assertThat(formatTime(60000)).isEqualTo(" 1m  0s")
        assertThat(formatTime(61000)).isEqualTo(" 1m  1s")
        assertThat(formatTime(3600000)).isEqualTo(" 1h  0m  0s")
        assertThat(formatTime(3661000)).isEqualTo(" 1h  1m  1s")
        assertThat(formatTime(86400000)).isEqualTo(" 1d  0h  0m  0s")
        assertThat(formatTime(86400000 + 3600000 + 60000 + 1000)).isEqualTo(" 1d  1h  1m  1s")
        assertThat(formatTime(99 * 86400000L)).isEqualTo("99d  0h  0m  0s")
    }
}
