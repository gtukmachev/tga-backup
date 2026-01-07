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
    fun `test no save if no changes`() {
        val folder = tempDir.toFile()
        val cacheFile = File(folder, ".md5")
        
        val cache = Md5Cache(folder)
        cache.save()
        assertThat(cacheFile).doesNotExist()
        
        val fileInfo = FileInfo("test.txt", false, 100L, 1000L, 2000L)
        cache.updateMd5(fileInfo, "md5")
        cache.save()
        assertThat(cacheFile).exists()
        val lastMod = cacheFile.lastModified()
        
        Thread.sleep(100)
        
        val cache2 = Md5Cache(folder)
        cache2.save()
        assertThat(cacheFile.lastModified()).isEqualTo(lastMod)
    }
}
