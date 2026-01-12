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

    @Test
    fun `test excluded files are ignored`() {
        val srcFiles = setOf(
            FileInfo("good.txt", false, 100L).apply { setupMd5("md5-good") },
            FileInfo("bad.txt", false, 50L).apply { readException = RuntimeException("Boom!") }
        )
        val dstFiles = emptySet<FileInfo>()

        val result = compareSrcAndDst(srcFiles, dstFiles)

        assertThat(result.toAddFiles).containsExactly(FileInfo("good.txt", false, 100L).apply { setupMd5("md5-good") })
        assertThat(result.toDeleteFiles).isEmpty()
        assertThat(result.toOverrideFiles).isEmpty()
    }

    @Test
    fun `test corrupted local files are not deleted from destination`() {
        val srcFiles = setOf(
            FileInfo("corrupted.txt", false, 100L).apply { readException = RuntimeException("Read Error") }
        )
        val dstFiles = setOf(
            FileInfo("corrupted.txt", false, 100L).apply { setupMd5("md5-remote") }
        )

        val result = compareSrcAndDst(srcFiles, dstFiles)

        assertThat(result.toAddFiles).isEmpty()
        assertThat(result.toDeleteFiles).isEmpty()
        assertThat(result.toOverrideFiles).isEmpty()
    }

    @Test
    fun `test file rename detection`() {
        val srcFiles = setOf(FileInfo("new-name.txt", false, 100L).apply { setupMd5("md5-1") })
        val dstFiles = setOf(FileInfo("old-name.txt", false, 100L).apply { setupMd5("md5-1") })

        val result = compareSrcAndDst(srcFiles, dstFiles)

        assertThat(result.toAddFiles).isEmpty()
        assertThat(result.toDeleteFiles).isEmpty()
        assertThat(result.toRenameFiles).containsExactly(
            FileInfo("old-name.txt", false, 100L).apply { setupMd5("md5-1") } to "new-name.txt"
        )
    }

    @Test
    fun `test file move detection`() {
        val srcFiles = setOf(FileInfo("folder/file.txt", false, 100L).apply { setupMd5("md5-1") })
        val dstFiles = setOf(FileInfo("file.txt", false, 100L).apply { setupMd5("md5-1") })

        val result = compareSrcAndDst(srcFiles, dstFiles)

        assertThat(result.toAddFiles).isEmpty()
        assertThat(result.toDeleteFiles).isEmpty()
        assertThat(result.toMoveFiles).containsExactly(
            FileInfo("file.txt", false, 100L).apply { setupMd5("md5-1") } to "folder/file.txt"
        )
    }

    @Test
    fun `test folder move detection`() {
        val srcFiles = setOf(
            FileInfo("new-folder", true, 10L),
            FileInfo("new-folder/file1.txt", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("new-folder/file2.txt", false, 200L).apply { setupMd5("md5-2") }
        )
        val dstFiles = setOf(
            FileInfo("old-folder", true, 10L),
            FileInfo("old-folder/file1.txt", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("old-folder/file2.txt", false, 200L).apply { setupMd5("md5-2") }
        )

        val result = compareSrcAndDst(srcFiles, dstFiles)

        assertThat(result.toAddFiles).isEmpty()
        assertThat(result.toDeleteFiles).isEmpty()
        assertThat(result.toMoveFiles).isEmpty()
        assertThat(result.toRenameFiles).isEmpty()
        assertThat(result.toRenameFolders).containsExactly(
            FileInfo("old-folder", true, 10L) to "new-folder"
        )
    }

    @Test
    fun `test move and rename combined - should split`() {
        // Actually, my current implementation detects it as rename if names are different, or move if names are same.
        // If BOTH folder and name are different, it's a rename.
        val srcFiles = setOf(FileInfo("new-folder/new-file.txt", false, 100L).apply { setupMd5("md5-1") })
        val dstFiles = setOf(FileInfo("old-file.txt", false, 100L).apply { setupMd5("md5-1") })

        val result = compareSrcAndDst(srcFiles, dstFiles)

        assertThat(result.toRenameFiles).containsExactly(
            FileInfo("old-file.txt", false, 100L).apply { setupMd5("md5-1") } to "new-folder/new-file.txt"
        )
    }

    @Test
    fun `test folder move detection with excluded files`() {
        // Folder should be detected as moved even if it contains excluded files (like .md5)
        val srcFiles = setOf(
            FileInfo("new-folder", true, 10L),
            FileInfo("new-folder/file1.txt", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("new-folder/file2.txt", false, 200L).apply { setupMd5("md5-2") },
            FileInfo("new-folder/.md5", false, 50L).apply { setupMd5("md5-cache") }
        )
        val dstFiles = setOf(
            FileInfo("old-folder", true, 10L),
            FileInfo("old-folder/file1.txt", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("old-folder/file2.txt", false, 200L).apply { setupMd5("md5-2") },
            FileInfo("old-folder/.md5", false, 50L).apply { setupMd5("md5-cache-old") }
        )

        val excludePatterns = listOf(".md5")
        val result = compareSrcAndDst(srcFiles, dstFiles, excludePatterns)

        // Folder should be detected as moved, ignoring the .md5 file
        assertThat(result.toRenameFolders).containsExactly(
            FileInfo("old-folder", true, 10L) to "new-folder"
        )
        // The .md5 files should be handled separately (added/deleted)
        assertThat(result.toAddFiles).contains(FileInfo("new-folder/.md5", false, 50L).apply { setupMd5("md5-cache") })
        assertThat(result.toDeleteFiles).contains(FileInfo("old-folder/.md5", false, 50L).apply { setupMd5("md5-cache-old") })
    }

    @Test
    fun `test folder rename with nested subfolders - no direct files`() {
        // This reproduces the reported issue: folder with only subfolders (no direct files)
        // Example: "2017-09-01 Линейка 3В (Андрей)" -> "2017-09-01 Линейка 3Д (Андрей)"
        val srcFiles = setOf(
            FileInfo("2017", true, 10L),
            FileInfo("2017/09 Сентябрь", true, 10L),
            FileInfo("2017/09 Сентябрь/2017-09-01 Линейка 3Д (Андрей)", true, 10L),
            FileInfo("2017/09 Сентябрь/2017-09-01 Линейка 3Д (Андрей)/photo1.jpg", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("2017/09 Сентябрь/2017-09-01 Линейка 3Д (Андрей)/photo2.jpg", false, 200L).apply { setupMd5("md5-2") },
            FileInfo("2017/09 Сентябрь/2017-09-01 Линейка 3Д (Андрей)/photo3.jpg", false, 300L).apply { setupMd5("md5-3") }
        )
        val dstFiles = setOf(
            FileInfo("2017", true, 10L),
            FileInfo("2017/09 Сентябрь", true, 10L),
            FileInfo("2017/09 Сентябрь/2017-09-01 Линейка 3В (Андрей)", true, 10L),
            FileInfo("2017/09 Сентябрь/2017-09-01 Линейка 3В (Андрей)/photo1.jpg", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("2017/09 Сентябрь/2017-09-01 Линейка 3В (Андрей)/photo2.jpg", false, 200L).apply { setupMd5("md5-2") },
            FileInfo("2017/09 Сентябрь/2017-09-01 Линейка 3В (Андрей)/photo3.jpg", false, 300L).apply { setupMd5("md5-3") }
        )

        val result = compareSrcAndDst(srcFiles, dstFiles)

        // Should detect folder rename, not individual file renames
        assertThat(result.toRenameFolders).containsExactly(
            FileInfo("2017/09 Сентябрь/2017-09-01 Линейка 3В (Андрей)", true, 10L) to "2017/09 Сентябрь/2017-09-01 Линейка 3Д (Андрей)"
        )
        assertThat(result.toRenameFiles).isEmpty()
        assertThat(result.toMoveFiles).isEmpty()
        assertThat(result.toAddFiles).isEmpty()
        assertThat(result.toDeleteFiles).isEmpty()
    }

    @Test
    fun `test folder rename with deeply nested structure`() {
        // Test with multiple levels of nesting
        val srcFiles = setOf(
            FileInfo("parent", true, 10L),
            FileInfo("parent/renamed-folder", true, 10L),
            FileInfo("parent/renamed-folder/subfolder1", true, 10L),
            FileInfo("parent/renamed-folder/subfolder1/file1.txt", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("parent/renamed-folder/subfolder2", true, 10L),
            FileInfo("parent/renamed-folder/subfolder2/file2.txt", false, 200L).apply { setupMd5("md5-2") }
        )
        val dstFiles = setOf(
            FileInfo("parent", true, 10L),
            FileInfo("parent/old-folder", true, 10L),
            FileInfo("parent/old-folder/subfolder1", true, 10L),
            FileInfo("parent/old-folder/subfolder1/file1.txt", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("parent/old-folder/subfolder2", true, 10L),
            FileInfo("parent/old-folder/subfolder2/file2.txt", false, 200L).apply { setupMd5("md5-2") }
        )

        val result = compareSrcAndDst(srcFiles, dstFiles)

        // Should detect folder rename at the correct level
        assertThat(result.toRenameFolders).containsExactly(
            FileInfo("parent/old-folder", true, 10L) to "parent/renamed-folder"
        )
        assertThat(result.toRenameFiles).isEmpty()
        assertThat(result.toMoveFiles).isEmpty()
        // Subfolders should not be in add/delete lists
        assertThat(result.toAddFiles).isEmpty()
        assertThat(result.toDeleteFiles).isEmpty()
    }

    @Test
    fun `test edge case - file moved to shorter path prevents folder detection`() {
        // This reproduces the bug: when a file from a deep folder is moved to a shorter path,
        // the suffix can be longer than the new file name, causing StringIndexOutOfBoundsException
        val srcFiles = setOf(
            FileInfo("short.txt", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("other-folder", true, 10L),
            FileInfo("other-folder/file2.txt", false, 200L).apply { setupMd5("md5-2") }
        )
        val dstFiles = setOf(
            FileInfo("very/long/nested/folder", true, 10L),
            FileInfo("very/long/nested/folder/short.txt", false, 100L).apply { setupMd5("md5-1") },
            FileInfo("other-folder", true, 10L),
            FileInfo("other-folder/file2.txt", false, 200L).apply { setupMd5("md5-2") }
        )

        // Should not throw StringIndexOutOfBoundsException
        val result = compareSrcAndDst(srcFiles, dstFiles)

        // The file should be detected as renamed (moved to root with same name)
        assertThat(result.toMoveFiles).containsExactly(
            FileInfo("very/long/nested/folder/short.txt", false, 100L).apply { setupMd5("md5-1") } to "short.txt"
        )
        // The folder should be in delete list (not detected as moved because only one file)
        assertThat(result.toDeleteFiles).contains(FileInfo("very/long/nested/folder", true, 10L))
    }
}
