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
    fun `test readParams with no-deletion flag`() {
        val args1 = arrayOf("-sr", "src", "-dr", "dst", "-nd")
        assertThat(args1.readParams().noDeletion).isTrue()

        val args2 = arrayOf("-sr", "src", "-dr", "dst", "--no-deletion")
        assertThat(args2.readParams().noDeletion).isTrue()

        val args3 = arrayOf("-sr", "src", "-dr", "dst")
        assertThat(args3.readParams().noDeletion).isFalse()
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

    @Test
    fun `test profile update contains all parameters`() {
        val profileName = "test-profile-${System.currentTimeMillis()}"
        val profileFile = File(System.getProperty("user.home"), ".tga-backup/$profileName.conf")
        try {
            val args = arrayOf(profileName, "-sr", "src", "-dr", "dst", "-up")
            
            // To avoid interactive prompt during test, we ensure the file doesn't exist
            if (profileFile.exists()) profileFile.delete()
            
            args.readParams()
            
            assertThat(profileFile).exists()
            val content = profileFile.readText()
            
            // Check for some parameters that are NOT in the command line but should be in the file (from defaults)
            assertThat(content).contains("path")
            assertThat(content).contains("dryRun")
            assertThat(content).contains("parallelThreads")
            assertThat(content).contains("verbose")
            
            // Check for parameters that ARE in the command line
            assertThat(content).contains("srcRoot")
            assertThat(content).contains("dstRoot")
            assertThat(content).contains("src")
            assertThat(content).contains("dst")
            
        } finally {
            if (profileFile.exists()) profileFile.delete()
        }
    }
}
