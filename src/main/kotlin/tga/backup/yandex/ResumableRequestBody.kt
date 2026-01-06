package tga.backup.yandex

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream

typealias ProgressCallback = (loaded: Long, total: Long) -> Unit

class ResumableRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val offset: Long, // How many bytes are already on the server (skip them)
    private val onProgress: ProgressCallback
) : RequestBody() {

    override fun contentType() = contentType

    // OkHttp will ask for the length of THIS request.
    // We only send the "tail" of the file.
    override fun contentLength(): Long = file.length() - offset

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(8 * 1024) // 8 KB buffer
        var inputStream: FileInputStream? = null

        try {
            inputStream = FileInputStream(file)

            // IMPORTANT: Skip what is already uploaded
            if (offset > 0) {
                inputStream.skip(offset)
            }

            var uploadedNow = 0L
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                uploadedNow += read

                // Report total progress (offset + what we just transferred)
                onProgress(offset + uploadedNow, file.length())
            }
        } finally {
            inputStream?.close()
        }
    }
}