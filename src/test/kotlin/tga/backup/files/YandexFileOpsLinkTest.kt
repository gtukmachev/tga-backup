package tga.backup.files

import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tga.backup.yandex.YandexResumableUploader

class YandexFileOpsLinkTest {

    @Test
    fun `test generateWebLink with Russian characters`() {
        val uploader = YandexResumableUploader("token", OkHttpClient())
        val fileOps = YandexFileOps(yandex = uploader, profile = "test", useCache = false)
        
        val path = "/Привет Мир/test file"
        val link = fileOps.generateWebLink(path)
        
        assertThat(link).isEqualTo("https://disk.yandex.ru/client/disk/Привет%20Мир/test%20file")
    }

    @Test
    fun `test generateWebLink without leading slash`() {
        val uploader = YandexResumableUploader("token", OkHttpClient())
        val fileOps = YandexFileOps(yandex = uploader, profile = "test", useCache = false)
        
        val path = "folder/subfolder"
        val link = fileOps.generateWebLink(path)
        
        assertThat(link).isEqualTo("https://disk.yandex.ru/client/disk/folder/subfolder")
    }

    @Test
    fun `test generateWebLink with rootPath`() {
        val uploader = YandexResumableUploader("token", OkHttpClient())
        val fileOps = YandexFileOps(yandex = uploader, profile = "test", useCache = false)

        assertThat(fileOps.generateWebLink("file.txt", "yandex://backup/root"))
            .isEqualTo("https://disk.yandex.ru/client/disk/backup/root/file.txt")

        assertThat(fileOps.generateWebLink("/file.txt", "yandex://backup/root/"))
            .isEqualTo("https://disk.yandex.ru/client/disk/backup/root/file.txt")

        assertThat(fileOps.generateWebLink("folder/file.txt", "yandex://backup/"))
            .isEqualTo("https://disk.yandex.ru/client/disk/backup/folder/file.txt")

        assertThat(fileOps.generateWebLink("file.txt", ""))
            .isEqualTo("https://disk.yandex.ru/client/disk/file.txt")

        assertThat(fileOps.generateWebLink("", "yandex://backup"))
            .isEqualTo("https://disk.yandex.ru/client/disk/backup")

        assertThat(fileOps.generateWebLink("/", "/"))
            .isEqualTo("https://disk.yandex.ru/client/disk/")
    }
}
