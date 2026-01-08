package tga.backup.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FileInfoComparisonTest {

    @Test
    fun `FileInfo objects with different timestamps should be equal`() {
        val file1 = FileInfo(name = "test.txt", isDirectory = false, size = 100, creationTime = 1000L, lastModifiedTime = 2000L)
        file1.setupMd5("md5hash")

        val file2 = FileInfo(name = "test.txt", isDirectory = false, size = 100, creationTime = 3000L, lastModifiedTime = 4000L)
        file2.setupMd5("md5hash")

        assertThat(file1).isEqualTo(file2)
        assertThat(file1.hashCode()).isEqualTo(file2.hashCode())
    }

    @Test
    fun `FileInfo objects with different size should NOT be equal`() {
        val file1 = FileInfo(name = "test.txt", isDirectory = false, size = 100)
        val file2 = FileInfo(name = "test.txt", isDirectory = false, size = 200)

        assertThat(file1).isNotEqualTo(file2)
    }

    @Test
    fun `FileInfo objects with different names should NOT be equal`() {
        val file1 = FileInfo(name = "test1.txt", isDirectory = false, size = 100)
        val file2 = FileInfo(name = "test2.txt", isDirectory = false, size = 100)

        assertThat(file1).isNotEqualTo(file2)
    }

    @Test
    fun `FileInfo objects with different md5 should NOT be equal`() {
        val file1 = FileInfo(name = "test.txt", isDirectory = false, size = 100)
        file1.setupMd5("md5_1")

        val file2 = FileInfo(name = "test.txt", isDirectory = false, size = 100)
        file2.setupMd5("md5_2")

        assertThat(file1).isNotEqualTo(file2)
    }

    @Test
    fun `FileInfo objects with different isDirectory should NOT be equal`() {
        val file1 = FileInfo(name = "test", isDirectory = false, size = 0)
        val file2 = FileInfo(name = "test", isDirectory = true, size = 0)

        assertThat(file1).isNotEqualTo(file2)
    }

    @Test
    fun `compareSrcAndDst should not mark files for override if only timestamps differ`() {
        val srcFile = FileInfo(name = "test.txt", isDirectory = false, size = 100, creationTime = 1000L, lastModifiedTime = 2000L)
        srcFile.setupMd5("md5hash")

        val dstFile = FileInfo(name = "test.txt", isDirectory = false, size = 100, creationTime = 3000L, lastModifiedTime = 4000L)
        dstFile.setupMd5("md5hash")

        val result = compareSrcAndDst(setOf(srcFile), setOf(dstFile))

        assertThat(result.toOverrideFiles).isEmpty()
        assertThat(result.toAddFiles).isEmpty()
        assertThat(result.toDeleteFiles).isEmpty()
    }
}
