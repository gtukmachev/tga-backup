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
    fun `test generateWebLink with special characters`() {
        val uploader = YandexResumableUploader("token", OkHttpClient())
        val fileOps = YandexFileOps(yandex = uploader, profile = "test", useCache = false)

        val path = "/folder with spaces & special symbols!"
        val link = fileOps.generateWebLink(path)

        assertThat(link).isEqualTo("https://disk.yandex.ru/client/disk/folder%20with%20spaces%20%26%20special%20symbols!")
    }
}
