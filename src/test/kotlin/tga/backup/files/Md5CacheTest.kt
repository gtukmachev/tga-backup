package tga.backup.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class Md5CacheTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `test cache creation and retrieval`() {
        val folder = tempDir.toFile()
        val file = File(folder, "test.txt")
        file.writeText("hello world")
        
        val fileInfo = FileInfo(
            name = "test.txt",
            isDirectory = false,
            size = file.length(),
            creationTime = 123456L,
            lastModifiedTime = file.lastModified()
        )
        
        val cache = Md5Cache(folder)
        val md5 = "5eb63bbbe01eeed093cb22bb8f5acdc3"
        cache.updateMd5(fileInfo, md5)
        cache.save()
        
        // Load new cache instance from same folder
        val cache2 = Md5Cache(folder)
        assertThat(cache2.getMd5(fileInfo)).isEqualTo(md5)
    }

    @Test
    fun `test cache invalidation on size change`() {
        val folder = tempDir.toFile()
        val fileInfo = FileInfo("test.txt", false, 100L, 1000L, 2000L)
        
        val cache = Md5Cache(folder)
        cache.updateMd5(fileInfo, "some-md5")
        
        val changedInfo = fileInfo.copy(size = 101L)
        assertThat(cache.getMd5(changedInfo)).isNull()
    }

    @Test
    fun `test cache invalidation on time change`() {
        val folder = tempDir.toFile()
        val fileInfo = FileInfo("test.txt", false, 100L, 1000L, 2000L)
        
        val cache = Md5Cache(folder)
        cache.updateMd5(fileInfo, "some-md5")
        
        val changedInfo = fileInfo.copy(lastModifiedTime = 2001L)
        assertThat(cache.getMd5(changedInfo)).isNull()
    }
    
    @Test
    fun `test column alignment in cache file`() {
        val folder = tempDir.toFile()
        val cache = Md5Cache(folder)
        
        val file1 = FileInfo("short.txt", false, 10L, 1000L, 2000L)
        val file2 = FileInfo("very-long-filename.txt", false, 1000000L, 1000L, 2000L)
        
        cache.updateMd5(file1, "md5-1")
        cache.updateMd5(file2, "md5-2")
        cache.save()
        
        val cacheFile = File(folder, ".md5")
        val lines = cacheFile.readLines()
        
        assertThat(lines).hasSize(2)
        
        // Check that names are padded to the same length (length of "very-long-filename.txt")
        val name1 = lines[0].split("\t")[0]
        val name2 = lines[1].split("\t")[0]
        assertThat(name1.length).isEqualTo(name2.length)
        assertThat(name1).startsWith("short.txt")
        assertThat(name1).endsWith(" ")
        
        // Check that sizes are padded to the same length (length of "1000000")
        val size1 = lines[0].split("\t")[1]
        val size2 = lines[1].split("\t")[1]
        assertThat(size1.length).isEqualTo(size2.length)
        assertThat(size1).startsWith(" ")
        assertThat(size1.trim()).isEqualTo("10")
        
        // Verify we can still read it back correctly
        val cache2 = Md5Cache(folder)
        assertThat(cache2.getMd5(file1)).isEqualTo("md5-1")
        assertThat(cache2.getMd5(file2)).isEqualTo("md5-2")
    }
}
