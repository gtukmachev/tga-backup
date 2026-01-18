package tga.backup.duplicates

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tga.backup.files.FileInfo

class DuplicatesFinderTest {

    @Test
    fun `should find no duplicates when all files have unique MD5`() {
        val file1 = FileInfo("file1.txt", false, 100).apply { setupMd5("aaa") }
        val file2 = FileInfo("file2.txt", false, 200).apply { setupMd5("bbb") }
        val file3 = FileInfo("file3.txt", false, 300).apply { setupMd5("ccc") }
        val files = setOf(file1, file2, file3)

        val duplicates = findDuplicates(files)

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `should find duplicates when files have same MD5`() {
        val file1 = FileInfo("file1.txt", false, 100).apply { setupMd5("aaa") }
        val file2 = FileInfo("file2.txt", false, 100).apply { setupMd5("aaa") }
        val file3 = FileInfo("file3.txt", false, 200).apply { setupMd5("bbb") }
        val files = setOf(file1, file2, file3)

        val duplicates = findDuplicates(files)

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates["aaa"]).isNotNull
        assertThat(duplicates["aaa"]!!.files).hasSize(2)
        assertThat(duplicates["aaa"]!!.files.map { it.name }).containsExactly("file1.txt", "file2.txt")
    }

    @Test
    fun `should find multiple duplicate groups`() {
        val file1 = FileInfo("file1.txt", false, 100).apply { setupMd5("aaa") }
        val file2 = FileInfo("file2.txt", false, 100).apply { setupMd5("aaa") }
        val file3 = FileInfo("file3.txt", false, 200).apply { setupMd5("bbb") }
        val file4 = FileInfo("file4.txt", false, 200).apply { setupMd5("bbb") }
        val file5 = FileInfo("file5.txt", false, 200).apply { setupMd5("bbb") }
        val file6 = FileInfo("file6.txt", false, 300).apply { setupMd5("ccc") }
        val files = setOf(file1, file2, file3, file4, file5, file6)

        val duplicates = findDuplicates(files)

        assertThat(duplicates).hasSize(2)
        assertThat(duplicates["aaa"]!!.files).hasSize(2)
        assertThat(duplicates["bbb"]!!.files).hasSize(3)
    }

    @Test
    fun `should ignore directories`() {
        val dir1 = FileInfo("dir1", true, 0).apply { setupMd5("aaa") }
        val dir2 = FileInfo("dir2", true, 0).apply { setupMd5("aaa") }
        val file1 = FileInfo("file1.txt", false, 100).apply { setupMd5("bbb") }
        val files = setOf(dir1, dir2, file1)

        val duplicates = findDuplicates(files)

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `should ignore files without MD5`() {
        val file1 = FileInfo("file1.txt", false, 100)
        val file2 = FileInfo("file2.txt", false, 100)
        val file3 = FileInfo("file3.txt", false, 200).apply { setupMd5("aaa") }
        val files = setOf(file1, file2, file3)

        val duplicates = findDuplicates(files)

        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `should calculate wasted space correctly`() {
        val file1 = FileInfo("file1.txt", false, 100).apply { setupMd5("aaa") }
        val file2 = FileInfo("file2.txt", false, 100).apply { setupMd5("aaa") }
        val file3 = FileInfo("file3.txt", false, 100).apply { setupMd5("aaa") }
        val files = setOf(file1, file2, file3)

        val duplicates = findDuplicates(files)

        assertThat(duplicates["aaa"]!!.totalSize).isEqualTo(100)
        assertThat(duplicates["aaa"]!!.wastedSpace).isEqualTo(200) // 100 * (3 - 1)
    }

    @Test
    fun `should sort files by name within duplicate group`() {
        val file1 = FileInfo("zebra.txt", false, 100).apply { setupMd5("aaa") }
        val file2 = FileInfo("alpha.txt", false, 100).apply { setupMd5("aaa") }
        val file3 = FileInfo("beta.txt", false, 100).apply { setupMd5("aaa") }
        val files = setOf(file1, file2, file3)

        val duplicates = findDuplicates(files)

        assertThat(duplicates["aaa"]!!.files.map { it.name })
            .containsExactly("alpha.txt", "beta.txt", "zebra.txt")
    }

    @Test
    fun `should handle files with different names but same MD5`() {
        val file1 = FileInfo("path/to/file1.txt", false, 100).apply { setupMd5("aaa") }
        val file2 = FileInfo("another/path/file2.txt", false, 100).apply { setupMd5("aaa") }
        val file3 = FileInfo("completely/different/name.doc", false, 100).apply { setupMd5("aaa") }
        val files = setOf(file1, file2, file3)

        val duplicates = findDuplicates(files)

        assertThat(duplicates).hasSize(1)
        assertThat(duplicates["aaa"]!!.files).hasSize(3)
    }

    @Test
    fun `DuplicatesSummary should calculate statistics correctly`() {
        val file1 = FileInfo("file1.txt", false, 100).apply { setupMd5("aaa") }
        val file2 = FileInfo("file2.txt", false, 100).apply { setupMd5("aaa") }
        val file3 = FileInfo("file3.txt", false, 200).apply { setupMd5("bbb") }
        val file4 = FileInfo("file4.txt", false, 200).apply { setupMd5("bbb") }
        val file5 = FileInfo("file5.txt", false, 200).apply { setupMd5("bbb") }
        
        val duplicateGroups = mapOf(
            "aaa" to DuplicateGroup("aaa", listOf(file1, file2), 100),
            "bbb" to DuplicateGroup("bbb", listOf(file3, file4, file5), 200)
        )

        val summary = DuplicatesSummary.from(duplicateGroups)

        assertThat(summary.totalGroups).isEqualTo(2)
        assertThat(summary.totalDuplicateFiles).isEqualTo(3) // (2-1) + (3-1)
        assertThat(summary.totalWastedSpace).isEqualTo(500) // 100*(2-1) + 200*(3-1)
        assertThat(summary.largestGroup).isNotNull
        assertThat(summary.largestGroup!!.md5).isEqualTo("bbb")
    }

    @Test
    fun `DuplicatesSummary should handle empty duplicate groups`() {
        val duplicateGroups = emptyMap<String, DuplicateGroup>()

        val summary = DuplicatesSummary.from(duplicateGroups)

        assertThat(summary.totalGroups).isEqualTo(0)
        assertThat(summary.totalDuplicateFiles).isEqualTo(0)
        assertThat(summary.totalWastedSpace).isEqualTo(0)
        assertThat(summary.largestGroup).isNull()
    }
}
