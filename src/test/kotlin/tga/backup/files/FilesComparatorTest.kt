package tga.backup.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FilesComparatorTest {

    private val root = FileInfo("", true, 10L)

    @Test
    fun `test empty destination - first sync`() {
        val srcFiles = setOf(
            FileInfo("file1.txt", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("folder1", true, 10L),
            FileInfo("folder1/file2.txt", false, 200L).apply { setupMd5("md5-2") }
        )
        val dstFiles = emptySet<FileInfo>()

        val result = compareSrcAndDst(srcFiles, dstFiles)

        assertThat(result.toAddFiles).isEqualTo(srcFiles)
        assertThat(result.toDeleteFiles).isEmpty()
        assertThat(result.toOverrideFiles).isEmpty()
    }

    @Test
    fun `test identical source and destination`() {
        val files = setOf(
            FileInfo("file1.txt", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("folder1", true, 10L),
            FileInfo("folder1/file2.txt", false, 200L).apply { setupMd5("md5-2") }
        )
        
        val result = compareSrcAndDst(files, files)

        assertThat(result.toAddFiles).isEmpty()
        assertThat(result.toDeleteFiles).isEmpty()
        assertThat(result.toOverrideFiles).isEmpty()
    }

    @Test
    fun `test new files in source`() {
        val common = FileInfo("common.txt", false, 100L).apply { setupMd5("md5-common") }
        val srcFiles = setOf(
            common,
            FileInfo("new.txt", false, 50L).apply { setupMd5("md5-new") }
        )
        val dstFiles = setOf(common)

        val result = compareSrcAndDst(srcFiles, dstFiles)

        assertThat(result.toAddFiles).containsExactly(FileInfo("new.txt", false, 50L).apply { setupMd5("md5-new") })
        assertThat(result.toDeleteFiles).isEmpty()
        assertThat(result.toOverrideFiles).isEmpty()
    }

    @Test
    fun `test orphaned files in destination`() {
        val common = FileInfo("common.txt", false, 100L).apply { setupMd5("md5-common") }
        val srcFiles = setOf(common)
        val dstFiles = setOf(
            common,
            FileInfo("orphan.txt", false, 50L).apply { setupMd5("md5-orphan") }
        )

        val result = compareSrcAndDst(srcFiles, dstFiles)

        assertThat(result.toAddFiles).isEmpty()
        assertThat(result.toDeleteFiles).containsExactly(FileInfo("orphan.txt", false, 50L).apply { setupMd5("md5-orphan") })
        assertThat(result.toOverrideFiles).isEmpty()
    }

    @Test
    fun `test changed files - override`() {
        val srcFiles = setOf(FileInfo("file1.txt", false, 100L).apply { setupMd5("md5-new") })
        val dstFiles = setOf(FileInfo("file1.txt", false, 100L).apply { setupMd5("md5-old") })

        val result = compareSrcAndDst(srcFiles, dstFiles)

        assertThat(result.toAddFiles).isEmpty()
        assertThat(result.toDeleteFiles).isEmpty()
        assertThat(result.toOverrideFiles).containsExactly(FileInfo("file1.txt", false, 100L).apply { setupMd5("md5-new") })
    }

    @Test
    fun `test mixed changes`() {
        val srcFiles = setOf(
            FileInfo("to-override-checksum.txt", false, 100L).apply { setupMd5("md5-src") },
            FileInfo("to-override-size.txt", false, 150L).apply { setupMd5("md5-same") },
            FileInfo("to-copy.txt", false, 200L).apply { setupMd5("md5-copy") },
            FileInfo("stay-same.txt", false, 300L).apply { setupMd5("md5-same") },
        )
        val dstFiles = setOf(
            FileInfo("to-override-checksum.txt", false, 100L).apply { setupMd5("md5-dst") },
            FileInfo("to-override-size.txt", false, 151L).apply { setupMd5("md5-same") },
            FileInfo("to-delete.txt", false, 400L).apply { setupMd5("md5-delete") },
            FileInfo("stay-same.txt", false, 300L).apply { setupMd5("md5-same") },
        )

        val result = compareSrcAndDst(srcFiles, dstFiles)

        assertThat(result.toAddFiles).containsExactlyInAnyOrder(FileInfo("to-copy.txt", false, 200L).apply { setupMd5("md5-copy") })
        assertThat(result.toDeleteFiles).containsExactlyInAnyOrder(FileInfo("to-delete.txt", false, 400L).apply { setupMd5("md5-delete") })
        assertThat(result.toOverrideFiles).containsExactlyInAnyOrder(
            FileInfo("to-override-checksum.txt", false, 100L).apply { setupMd5("md5-src") },
            FileInfo("to-override-size.txt", false, 150L).apply { setupMd5("md5-same") },
        )
    }
}
