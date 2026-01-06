package tga.backup.yandex

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream

class ResumableRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val offset: Long, // Сколько байт уже на сервере (пропускаем их)
    private val onProgress: (loaded: Long, total: Long) -> Unit
) : RequestBody() {

    override fun contentType() = contentType

    // OkHttp спросит, какой длины ЭТОТ запрос.
    // Мы отправляем только "хвост" файла.
    override fun contentLength(): Long = file.length() - offset

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(8 * 1024) // Буфер 8 KB
        var inputStream: FileInputStream? = null

        try {
            inputStream = FileInputStream(file)

            // ГЛАВНОЕ: Пропускаем то, что уже загружено
            if (offset > 0) {
                inputStream.skip(offset)
            }

            var uploadedNow = 0L
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploadedNow += read

                // Сообщаем общий прогресс (смещение + то что сейчас передали)
                onProgress(offset + uploadedNow, file.length())
            }
        } finally {
            inputStream?.close()
        }
    }
}