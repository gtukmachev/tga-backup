package tga.backup.params

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class ParamsTest {

    @Test
    fun `test readParams with short names`() {
        val args = arrayOf("-sr", "src_root", "-dr", "dst_root", "-p", "relative/path", "-t", "5")
        val params = args.readParams()

        assertThat(params.srcRoot).isEqualTo("src_root")
        assertThat(params.dstRoot).isEqualTo("dst_root")
        assertThat(params.path).isEqualTo("relative/path")
        assertThat(params.parallelThreads).isEqualTo(5)
    }

    @Test
    fun `test readParams with long names`() {
        val args = arrayOf("--source-root", "src_root", "--destination-root", "dst_root", "--path", "relative/path", "--threads", "20")
        val params = args.readParams()

        assertThat(params.srcRoot).isEqualTo("src_root")
        assertThat(params.dstRoot).isEqualTo("dst_root")
        assertThat(params.path).isEqualTo("relative/path")
        assertThat(params.parallelThreads).isEqualTo(20)
    }

    @Test
    fun `test readParams with default path`() {
        val args = arrayOf("-sr", "src_root", "-dr", "dst_root")
        val params = args.readParams()

        assertThat(params.path).isEqualTo("*")
        assertThat(params.srcFolder).isEqualTo("src_root")
        assertThat(params.dstFolder).isEqualTo("dst_root")
    }

    @Test
    fun `test normalizePath local`() {
        val sep = File.separator
        assertThat(normalizePath("/root", "path")).isEqualTo("/root${sep}path")
        assertThat(normalizePath("/root/", "path")).isEqualTo("/root${sep}path")
        assertThat(normalizePath("/root", "/path")).isEqualTo("/root${sep}path")
        assertThat(normalizePath("/root/", "/path")).isEqualTo("/root${sep}path")
        assertThat(normalizePath("C:\\root", "path")).isEqualTo("C:\\root${sep}path")
    }

    @Test
    fun `test normalizePath yandex`() {
        assertThat(normalizePath("yandex://root", "path")).isEqualTo("yandex://root/path")
        assertThat(normalizePath("yandex://root/", "path")).isEqualTo("yandex://root/path")
        assertThat(normalizePath("yandex://root", "/path")).isEqualTo("yandex://root/path")
        assertThat(normalizePath("yandex://root/", "/path")).isEqualTo("yandex://root/path")
    }

    @Test
    fun `test normalizePath with asterisk`() {
        assertThat(normalizePath("yandex://root", "*")).isEqualTo("yandex://root")
        assertThat(normalizePath("/local/root", "*")).isEqualTo("/local/root")
    }

    @Test
    fun `test normalizePath with empty string`() {
        assertThat(normalizePath("yandex://root", "")).isEqualTo("yandex://root")
        assertThat(normalizePath("/local/root", "")).isEqualTo("/local/root")
    }
}
