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

    @Test
    fun `should find partial duplicate folder groups`() {
        // Folder A
        val a1 = FileInfo("folderA/file1.txt", false, 100).apply { setupMd5("md5_1") }
        val a2 = FileInfo("folderA/file2.txt", false, 200).apply { setupMd5("md5_2") }
        val uniqueA = FileInfo("folderA/uniqueA.txt", false, 50).apply { setupMd5("md5_uniqueA") }

        // Folder B
        val b1 = FileInfo("folderB/file1.txt", false, 100).apply { setupMd5("md5_1") }
        val b3 = FileInfo("folderB/file3.txt", false, 300).apply { setupMd5("md5_3") }

        // Folder C
        val c2 = FileInfo("folderC/file2.txt", false, 200).apply { setupMd5("md5_2") }
        val c3 = FileInfo("folderC/file3.txt", false, 300).apply { setupMd5("md5_3") }

        val files = setOf(a1, a2, uniqueA, b1, b3, c2, c3)

        val result = findDuplicates(files)

        // All duplicate files are shared between A, B, and C.
        // md5_1 is in A and B.
        // md5_2 is in A and C.
        // md5_3 is in B and C.
        // They should form one partial duplicate group.
        assertThat(result.folderGroups).isEmpty()
        assertThat(result.partialFolderGroups).hasSize(1)
        
        val group = result.partialFolderGroups[0]
        assertThat(group.folders.map { it.folderPath }).containsExactlyInAnyOrder("folderA", "folderB", "folderC")
        assertThat(group.fileGroups.map { it.md5 }).containsExactlyInAnyOrder("md5_1", "md5_2", "md5_3")
        
        // Check folder infos
        val folderA = group.folders.find { it.folderPath == "folderA" }!!
        assertThat(folderA.duplicateFilesCount).isEqualTo(2) // file1, file2
        assertThat(folderA.duplicateFilesSize).isEqualTo(300)
        assertThat(folderA.totalFilesCount).isEqualTo(3) // file1, file2 + uniqueA
        assertThat(folderA.isFullDuplicate).isFalse()

        val folderB = group.folders.find { it.folderPath == "folderB" }!!
        assertThat(folderB.totalFilesCount).isEqualTo(2)
        assertThat(folderB.isFullDuplicate).isTrue()

        val folderC = group.folders.find { it.folderPath == "folderC" }!!
        assertThat(folderC.totalFilesCount).isEqualTo(2)
        assertThat(folderC.isFullDuplicate).isTrue()

        // Check sorting: Full duplicates should be first
        // In this case, folderB and folderC are full duplicates AND they are duplicates of each other (md5_1, md5_3 in B, md5_2, md5_3 in C? No, wait)
        // Let's re-read:
        // folderB: md5_1, md5_3
        // folderC: md5_2, md5_3
        // They are NOT duplicates of each other. So both should be ORIGINAL candidates!
        assertThat(group.folders[0].isOriginalCandidate).isTrue()
        assertThat(group.folders[1].isOriginalCandidate).isTrue()
        assertThat(group.folders[2].isOriginalCandidate).isFalse()

        // Wasted space calculation:
        // md5_1 (size 100, 2 copies) -> 100 wasted
        // md5_2 (size 200, 2 copies) -> 200 wasted
        // md5_3 (size 300, 2 copies) -> 300 wasted
        // Total: 600
        assertThat(group.wastedSpace).isEqualTo(600)
        
        // fileGroups should be empty because all duplicates are in the partial group
        assertThat(result.fileGroups).isEmpty()
    }

    @Test
    fun `should identify original candidate in partial group (one vs many scenario)`() {
        // Original folder: contains file1, file2, file3
        val o1 = FileInfo("original/f1.txt", false, 100).apply { setupMd5("md5_1") }
        val o2 = FileInfo("original/f2.txt", false, 200).apply { setupMd5("md5_2") }
        val o3 = FileInfo("original/f3.txt", false, 300).apply { setupMd5("md5_3") }

        // Spread folder A: contains file1, file2
        val a1 = FileInfo("spreadA/f1.txt", false, 100).apply { setupMd5("md5_1") }
        val a2 = FileInfo("spreadA/f2.txt", false, 200).apply { setupMd5("md5_2") }

        // Spread folder B: contains file3
        val b3 = FileInfo("spreadB/f3.txt", false, 300).apply { setupMd5("md5_3") }

        val files = setOf(o1, o2, o3, a1, a2, b3)
        val result = findDuplicates(files)

        assertThat(result.partialFolderGroups).hasSize(1)
        val group = result.partialFolderGroups[0]
        
        // original should be the only original candidate
        val originalInfo = group.folders.find { it.folderPath == "original" }!!
        assertThat(originalInfo.isOriginalCandidate).isTrue()
        
        val aInfo = group.folders.find { it.folderPath == "spreadA" }!!
        assertThat(aInfo.isOriginalCandidate).isFalse() // It is full duplicate, but it's a subset of "original"

        val bInfo = group.folders.find { it.folderPath == "spreadB" }!!
        assertThat(bInfo.isOriginalCandidate).isFalse() // It is full duplicate, but it's a subset of "original"
    }

    @Test
    fun `should NOT form partial group if a duplicate file exists outside the group`() {
        // Folder A
        val a1 = FileInfo("folderA/file1.txt", false, 100).apply { setupMd5("md5_1") }
        val aUnique = FileInfo("folderA/unique.txt", false, 50).apply { setupMd5("md5_uniqueA") }
        
        // Folder B
        val b1 = FileInfo("folderB/file1.txt", false, 100).apply { setupMd5("md5_1") }
        val bUnique = FileInfo("folderB/unique.txt", false, 60).apply { setupMd5("md5_uniqueB") }
        
        // File 1 outside
        val outside1 = FileInfo("outside1.txt", false, 100).apply { setupMd5("md5_1") }

        val files = setOf(a1, aUnique, b1, bUnique, outside1)

        val result = findDuplicates(files)

        assertThat(result.folderGroups).isEmpty()
        assertThat(result.partialFolderGroups).isEmpty()
        assertThat(result.fileGroups).hasSize(1)
        assertThat(result.fileGroups[0].files.map { it.name }).containsExactlyInAnyOrder("folderA/file1.txt", "folderB/file1.txt", "outside1.txt")
    }
}
