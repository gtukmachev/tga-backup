package tga.backup.yandex

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class YandexResumableUploader(
    private val token: String,
    private val http: OkHttpClient
) {

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
            if (!response.isSuccessful) throw YandexResponseException("Error getting resources", response)
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
                throw YandexResponseException("Error creating folder", response)
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
                throw YandexResponseException("Error deleting resource", response)
            }
        }
    }

    // Main method
    fun uploadFile(localFile: File, remotePath: String, onProgress: ProgressCallback) {
        // 1. Get the link (or use the old one if it's still alive, but better to get a fresh one)
        val uploadUrl = getUploadLink(remotePath)

        println("Link received. Checking upload status...")

        // 2. Upload loop (in case we need to send in chunks,
        // but here we just send the remainder with one PATCH request)

        // Find out how many bytes are already there
        val serverOffset = getServerOffset(uploadUrl)

        if (serverOffset >= localFile.length()) {
            println("File is already fully uploaded!")
            return
        }

        if (serverOffset > 0) {
            println("Partial upload detected: ${(serverOffset / 1024 / 1024)} MB. Resuming...")
        } else {
            println("Starting upload from scratch...")
        }

        // 3. Execute PATCH request from the required offset
        performPatch(uploadUrl, localFile, serverOffset, onProgress)

        println("\nUpload completed successfully!")
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
            if (!response.isSuccessful) throw YandexResponseException("Error getting upload link", response)
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

        val body = ResumableRequestBody(file, contentType, offset, onProgress)

        val request = Request.Builder()
            .url(url)
            .header("Tus-Resumable", "1.0.0")
            .header("Upload-Offset", offset.toString()) // Tell the server which byte to start from
            .patch(body) // PATCH method
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw YandexResponseException("File loading error (PATCH)", response)
            }
        }
    }
}