package tga.backup.yandex

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class YandexResumableUploader(
    private val token: String,
    private val http: OkHttpClient
) {

    private val gson = Gson()

    // Главный метод
    fun uploadFile(localFile: File, remotePath: String, onProgress: ProgressCallback) {
        // 1. Получаем ссылку (или используем старую, если она еще жива, но лучше получить свежую)
        val uploadUrl = getUploadLink(remotePath)

        println("Ссылка получена. Проверяем состояние загрузки...")

        // 2. Цикл загрузки (на случай если нужно будет слать чанками,
        // но здесь мы просто шлем остаток одним PATCH запросом)

        // Узнаем, сколько байт уже там
        val serverOffset = getServerOffset(uploadUrl)

        if (serverOffset >= localFile.length()) {
            println("Файл уже полностью загружен!")
            return
        }

        if (serverOffset > 0) {
            println("Обнаружена частичная загрузка: ${(serverOffset / 1024 / 1024)} MB. Докачиваем...")
        } else {
            println("Начинаем загрузку с нуля...")
        }

        // 3. Выполняем PATCH запрос с нужного места
        performPatch(uploadUrl, localFile, serverOffset, onProgress)

        println("\nЗагрузка завершена успешно!")
    }

    // Получаем URL для загрузки
    private fun getUploadLink(path: String): String {
        val request = Request.Builder()
            .url("https://cloud-api.yandex.net/v1/disk/resources/upload?path=$path&overwrite=true")
            .header("Authorization", "OAuth $token")
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Ошибка получения ссылки: ${response.code}")
            val json = gson.fromJson(response.body?.charStream(), JsonObject::class.java)
            return json.get("href").asString
        }
    }

    // Спрашиваем у сервера HEAD запрос: сколько байт принято?
    private fun getServerOffset(url: String): Long {
        val request = Request.Builder()
            .url(url)
            .header("Tus-Resumable", "1.0.0") // Обязательный заголовок протокола Tus
            .head()
            .build()

        http.newCall(request).execute().use { response ->
            // Если файл еще не начинали грузить, заголовка может не быть или он 0
            val offsetHeader = response.header("Upload-Offset")
            return offsetHeader?.toLongOrNull() ?: 0L
        }
    }

    // Отправляем данные методом PATCH
    private fun performPatch(url: String, file: File, offset: Long, onProgress: ProgressCallback) {
        // Tus требует этот Content-Type
        val contentType = "application/offset+octet-stream".toMediaType()

        val body = ResumableRequestBody(file, contentType, offset, onProgress)

        val request = Request.Builder()
            .url(url)
            .header("Tus-Resumable", "1.0.0")
            .header("Upload-Offset", offset.toString()) // Говорим серверу, с какого байта начинаем
            .patch(body) // Метод PATCH
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw YandexResponseException("File loading error (PATCH)", response)
            }
        }
    }
}