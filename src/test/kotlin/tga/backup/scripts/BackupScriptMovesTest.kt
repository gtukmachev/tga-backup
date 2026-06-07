package tga.backup.scripts

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tga.backup.params.Params
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class BackupScriptMovesTest {

    @TempDir lateinit var srcDir: File
    @TempDir lateinit var dstDir: File

    private lateinit var originalStdIn: InputStream

    @BeforeEach
    fun saveStdIn() {
        originalStdIn = System.`in`
    }

    @AfterEach
    fun restoreStdIn() {
        System.setIn(originalStdIn)
    }

    private fun provideInput(text: String) {
        System.setIn(ByteArrayInputStream(text.toByteArray()))
    }

    private fun params(dryRun: Boolean = false) = Params(
        srcRoot = srcDir.absolutePath,
        dstRoot = dstDir.absolutePath,
        dryRun = dryRun,
        parallelThreads = 2,
    )

    private fun createFile(base: File, relativePath: String, content: String): File {
        val file = File(base, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    @Test
    fun `Y answer executes folder renames`() {
        createFile(srcDir, "new-name/file1.txt", "aaa")
        createFile(srcDir, "new-name/file2.txt", "bbb")

        createFile(dstDir, "old-name/file1.txt", "aaa")
        createFile(dstDir, "old-name/file2.txt", "bbb")

        provideInput("Y\n")
        BackupScript(params()).run()

        assertThat(File(dstDir, "new-name/file1.txt")).exists()
        assertThat(File(dstDir, "new-name/file2.txt")).exists()
        assertThat(File(dstDir, "old-name")).doesNotExist()
    }

    @Test
    fun `Y answer executes file renames`() {
        createFile(srcDir, "new-name.txt", "content")
        createFile(dstDir, "old-name.txt", "content")

        provideInput("Y\n")
        BackupScript(params()).run()

        assertThat(File(dstDir, "new-name.txt")).exists()
        assertThat(File(dstDir, "old-name.txt")).doesNotExist()
    }

    @Test
    fun `Y answer executes file moves`() {
        createFile(srcDir, "folder/file.txt", "content")
        createFile(dstDir, "file.txt", "content")

        provideInput("Y\n")
        BackupScript(params()).run()

        assertThat(File(dstDir, "folder/file.txt")).exists()
        assertThat(File(dstDir, "file.txt")).doesNotExist()
    }

    @Test
    fun `m answer executes folder renames`() {
        createFile(srcDir, "new-name/file1.txt", "aaa")
        createFile(srcDir, "new-name/file2.txt", "bbb")

        createFile(dstDir, "old-name/file1.txt", "aaa")
        createFile(dstDir, "old-name/file2.txt", "bbb")

        provideInput("m\n")
        BackupScript(params()).run()

        assertThat(File(dstDir, "new-name/file1.txt")).exists()
        assertThat(File(dstDir, "new-name/file2.txt")).exists()
        assertThat(File(dstDir, "old-name")).doesNotExist()
    }

    @Test
    fun `dry run does not execute moves`() {
        createFile(srcDir, "new-name/file1.txt", "aaa")
        createFile(dstDir, "old-name/file1.txt", "aaa")

        provideInput("Y\n")
        BackupScript(params(dryRun = true)).run()

        assertThat(File(dstDir, "old-name/file1.txt")).exists()
        assertThat(File(dstDir, "new-name")).doesNotExist()
    }
}
