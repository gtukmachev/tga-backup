package tga.backup.yandex

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class YandexResumableUploader(
    private val token: String,
    private val http: OkHttpClient,
    private val devMode: Boolean = false,
) {

    private val logger = KotlinLogging.logger {  }
    private val gson = Gson()

    fun getResources(path: String, limit: Int, offset: Int): JsonObject {
        val url = "https://cloud-api.yandex.net/v1/disk/resources".toHttpUrl().newBuilder()
            .addQueryParameter("path", path)
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("fields", "name,type,path,size,md5,_embedded.items.name,_embedded.items.type,_embedded.items.path,_embedded.items.size,_embedded.items.md5,_embedded.total")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "OAuth $token")
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw YandexResponseException("Error getting resources", response, dstFilePath = path)
            return gson.fromJson(response.body?.charStream(), JsonObject::class.java)
        }
    }

    fun makeFolder(path: String) {
        val url = "https://cloud-api.yandex.net/v1/disk/resources".toHttpUrl().newBuilder()
            .addQueryParameter("path", path)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "OAuth $token")
            .put(okhttp3.internal.EMPTY_REQUEST)
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 409) {
                throw YandexResponseException("Error creating folder", response, dstFilePath = path)
            }
        }
    }

    fun delete(path: String, permanently: Boolean = false) {
        val url = "https://cloud-api.yandex.net/v1/disk/resources".toHttpUrl().newBuilder()
            .addQueryParameter("path", path)
            .addQueryParameter("permanently", permanently.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "OAuth $token")
            .delete()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) {
                throw YandexResponseException("Error deleting resource", response, dstFilePath = path)
            }
        }
    }

    fun move(from: String, to: String) {
        val url = "https://cloud-api.yandex.net/v1/disk/resources/move".toHttpUrl().newBuilder()
            .addQueryParameter("from", from)
            .addQueryParameter("path", to)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "OAuth $token")
            .post(okhttp3.internal.EMPTY_REQUEST)
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw YandexResponseException("Error moving resource", response, dstFilePath = from)
            }
        }
    }

    // Main method
    fun uploadFile(localFile: File, remotePath: String, onProgress: ProgressCallback) {
        // 1. Get the link (or use the old one if it's still alive, but better to get a fresh one)
        val uploadUrl = getUploadLink(remotePath)

        logger.debug { "Link received. Checking upload status..." }

        // 2. Upload loop (in case we need to send in chunks,
        // but here we just send the remainder with one PATCH request)

        // Find out how many bytes are already there
        val serverOffset = getServerOffset(uploadUrl)

        if (serverOffset >= localFile.length()) {
            logger.debug { "File is already fully uploaded!" }
            return
        }

        if (serverOffset > 0) {
            logger.debug { "Partial upload detected: ${(serverOffset / 1024 / 1024)} MB. Resuming..." }
            // 3. Execute PATCH request from the required offset
            performPatch(uploadUrl, localFile, serverOffset, onProgress)
        } else {
            logger.debug { "Starting upload from scratch..." }
            // 3. Execute PUT request for the full file
            performPut(uploadUrl, localFile, onProgress)
        }

        logger.debug { "\nUpload completed successfully!" }
    }

    // Send data using the PUT method (for fresh uploads)
    private fun performPut(url: String, file: File, onProgress: ProgressCallback) {
        val contentType = "application/octet-stream".toMediaType()
        val body = ResumableRequestBody(file, contentType, 0L, devMode, onProgress)

        val request = Request.Builder()
            .url(url)
            .put(body)
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw YandexResponseException("File loading error (PUT)", response, srcFilePath = file.path)
            }
        }
    }

    // Get URL for uploading
    fun getUploadLink(path: String): String {
        val url = "https://cloud-api.yandex.net/v1/disk/resources/upload".toHttpUrl().newBuilder()
            .addQueryParameter("path", path)
            .addQueryParameter("overwrite", "true")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "OAuth $token")
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw YandexResponseException("Error getting upload link", response, dstFilePath = path)
            val json = gson.fromJson(response.body?.charStream(), JsonObject::class.java)
            return json.get("href").asString
        }
    }

    // Ask the server via HEAD request: how many bytes were accepted?
    private fun getServerOffset(url: String): Long {
        val request = Request.Builder()
            .url(url)
            .header("Tus-Resumable", "1.0.0") // Required Tus protocol header
            .head()
            .build()

        http.newCall(request).execute().use { response ->
            // If the file hasn't started uploading yet, the header might be missing or 0
            val offsetHeader = response.header("Upload-Offset")
            return offsetHeader?.toLongOrNull() ?: 0L
        }
    }

    // Send data using the PATCH method
    private fun performPatch(url: String, file: File, offset: Long, onProgress: ProgressCallback) {
        // Tus requires this Content-Type
        val contentType = "application/offset+octet-stream".toMediaType()

        val body = ResumableRequestBody(file, contentType, offset, devMode, onProgress)

        val request = Request.Builder()
            .url(url)
            .header("Tus-Resumable", "1.0.0")
            .header("Upload-Offset", offset.toString()) // Tell the server which byte to start from
            .patch(body) // PATCH method
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw YandexResponseException("File loading error (PATCH)", response, srcFilePath = file.path)
            }
        }
    }

    fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
        http.cache?.close()
    }
}