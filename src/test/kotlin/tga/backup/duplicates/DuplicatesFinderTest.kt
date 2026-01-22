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

        val result = findDuplicates(files)

        assertThat(result.fileGroups).isEmpty()
        assertThat(result.folderGroups).isEmpty()
    }

    @Test
    fun `should find duplicates when files have same MD5`() {
        val file1 = FileInfo("file1.txt", false, 100).apply { setupMd5("aaa") }
        val file2 = FileInfo("file2.txt", false, 100).apply { setupMd5("aaa") }
        val file3 = FileInfo("file3.txt", false, 200).apply { setupMd5("bbb") }
        val files = setOf(file1, file2, file3)

        val result = findDuplicates(files)

        assertThat(result.fileGroups).hasSize(1)
        assertThat(result.fileGroups[0].md5).isEqualTo("aaa")
        assertThat(result.fileGroups[0].files).hasSize(2)
        assertThat(result.fileGroups[0].files.map { it.name }).containsExactly("file1.txt", "file2.txt")
    }

    @Test
    fun `should find duplicate folders and remove their files from fileGroups`() {
        // Folder 1
        val f1_1 = FileInfo("folder1/file1.txt", false, 100).apply { setupMd5("aaa") }
        val f1_2 = FileInfo("folder1/file2.txt", false, 200).apply { setupMd5("bbb") }
        
        // Folder 2 (exact duplicate of Folder 1)
        val f2_1 = FileInfo("folder2/file1.txt", false, 100).apply { setupMd5("aaa") }
        val f2_2 = FileInfo("folder2/file2.txt", false, 200).apply { setupMd5("bbb") }

        // Individual duplicate files (not in duplicate folders)
        val lonely1 = FileInfo("lonely1.txt", false, 300).apply { setupMd5("ccc") }
        val lonely2 = FileInfo("lonely2.txt", false, 300).apply { setupMd5("ccc") }

        val files = setOf(f1_1, f1_2, f2_1, f2_2, lonely1, lonely2)

        val result = findDuplicates(files)

        // Should find 1 folder group
        assertThat(result.folderGroups).hasSize(1)
        assertThat(result.folderGroups[0].folders).containsExactly("folder1", "folder2")
        assertThat(result.folderGroups[0].filesCount).isEqualTo(2)
        assertThat(result.folderGroups[0].totalSize).isEqualTo(300)

        // Should find 1 file group (only for lonely files)
        // Files from folder1 and folder2 should be filtered out
        assertThat(result.fileGroups).hasSize(1)
        assertThat(result.fileGroups[0].md5).isEqualTo("ccc")
        assertThat(result.fileGroups[0].files.map { it.name }).containsExactly("lonely1.txt", "lonely2.txt")
    }

    @Test
    fun `should handle nested folders correctly`() {
        // Root folder files
        val r1 = FileInfo("file_root.txt", false, 10).apply { setupMd5("root") }
        
        // Subfolder A
        val a1 = FileInfo("sub/A/file.txt", false, 100).apply { setupMd5("aaa") }
        
        // Subfolder B (duplicate of A)
        val b1 = FileInfo("sub/B/file.txt", false, 100).apply { setupMd5("aaa") }

        val files = setOf(r1, a1, b1)

        val result = findDuplicates(files)

        assertThat(result.folderGroups).hasSize(1)
        assertThat(result.folderGroups[0].folders).containsExactly("sub/A", "sub/B")
        assertThat(result.fileGroups).isEmpty()
    }

    @Test
    fun `should calculate wasted space correctly for folders`() {
        val f1_1 = FileInfo("f1/a.txt", false, 100).apply { setupMd5("aaa") }
        val f2_1 = FileInfo("f2/a.txt", false, 100).apply { setupMd5("aaa") }
        val f3_1 = FileInfo("f3/a.txt", false, 100).apply { setupMd5("aaa") }
        
        val files = setOf(f1_1, f2_1, f3_1)
        val result = findDuplicates(files)

        assertThat(result.folderGroups).hasSize(1)
        assertThat(result.folderGroups[0].totalSize).isEqualTo(100)
        assertThat(result.folderGroups[0].wastedSpace).isEqualTo(200) // 100 * (3 - 1)
    }

    @Test
    fun `DuplicatesSummary should calculate statistics correctly`() {
        val f1_1 = FileInfo("f1/a.txt", false, 100).apply { setupMd5("aaa") }
        val f2_1 = FileInfo("f2/a.txt", false, 100).apply { setupMd5("aaa") }
        
        val lonely1 = FileInfo("l1.txt", false, 50).apply { setupMd5("bbb") }
        val lonely2 = FileInfo("l2.txt", false, 50).apply { setupMd5("bbb") }

        val result = findDuplicates(setOf(f1_1, f2_1, lonely1, lonely2))
        val summary = DuplicatesSummary.from(result)

        assertThat(summary.totalFolderGroups).isEqualTo(1)
        assertThat(summary.totalGroups).isEqualTo(1)
        assertThat(summary.totalWastedSpace).isEqualTo(150) // 100 (folder) + 50 (file)
    }
}
