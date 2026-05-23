package tga.backup.gdrive

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as GDriveFile
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.FileReader
import java.io.OutputStream

class GDriveClient(
    private val credentialsPath: String,
    private val tokenStorePath: String,
) {
    private val logger = KotlinLogging.logger {}
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport()

    private val driveService: Drive by lazy { buildDriveService() }

    private fun buildDriveService(): Drive {
        val credential = authorize()
        return Drive.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("tga-backup")
            .build()
    }

    private fun authorize(): Credential {
        val clientSecrets = FileReader(credentialsPath).use { reader ->
            GoogleClientSecrets.load(jsonFactory, reader)
        }

        val flow = GoogleAuthorizationCodeFlow.Builder(
            httpTransport, jsonFactory, clientSecrets,
            listOf(DriveScopes.DRIVE)
        )
            .setDataStoreFactory(FileDataStoreFactory(File(tokenStorePath)))
            .setAccessType("offline")
            .build()

        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    private val retryableCodes = setOf(403, 429, 500, 503)

    private fun <T> withRetry(
        description: String,
        maxRetries: Int = 3,
        onRetry: ((String) -> Unit)? = null,
        action: () -> T,
    ): T {
        var lastException: GoogleJsonResponseException? = null
        for (attempt in 0..maxRetries) {
            try {
                return action()
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode !in retryableCodes || attempt == maxRetries) {
                    throw GDriveResponseException(description, e)
                }
                lastException = e
                val delaySeconds = 1L shl attempt
                onRetry?.invoke("Retry ${attempt + 1}/$maxRetries ($description, HTTP ${e.statusCode}, waiting ${delaySeconds}s)...")
                Thread.sleep(delaySeconds * 1000)
            }
        }
        throw GDriveResponseException(description, lastException!!)
    }

    fun listFiles(
        folderId: String,
        pageToken: String? = null,
        pageSize: Int = 1000,
        onRetry: ((String) -> Unit)? = null,
    ): Pair<List<GDriveFile>, String?> {
        return withRetry("Error listing files in folder '$folderId'", onRetry = onRetry) {
            val result = driveService.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setPageSize(pageSize)
                .setPageToken(pageToken)
                .setFields("nextPageToken, files(id, name, mimeType, size, md5Checksum, parents)")
                .execute()

            (result.files ?: emptyList()) to result.nextPageToken
        }
    }

    fun getFileMetadata(fileId: String, onRetry: ((String) -> Unit)? = null): GDriveFile {
        return withRetry("Error getting metadata for file '$fileId'", onRetry = onRetry) {
            driveService.files().get(fileId)
                .setFields("id, name, mimeType, size, md5Checksum, parents")
                .execute()
        }
    }

    fun createFolder(name: String, parentId: String, onRetry: ((String) -> Unit)? = null): GDriveFile {
        val folderMetadata = GDriveFile().apply {
            this.name = name
            this.mimeType = FOLDER_MIME_TYPE
            this.parents = listOf(parentId)
        }

        return withRetry("Error creating folder '$name' in parent '$parentId'", onRetry = onRetry) {
            driveService.files().create(folderMetadata)
                .setFields("id, name")
                .execute()
        }
    }

    fun uploadFile(
        localFile: File,
        parentId: String,
        onProgress: (loaded: Long, total: Long) -> Unit,
        onRetry: ((String) -> Unit)? = null,
    ): GDriveFile {
        val fileMetadata = GDriveFile().apply {
            this.name = localFile.name
            this.parents = listOf(parentId)
        }

        val mediaContent = FileContent(null, localFile)
        val totalSize = localFile.length()

        return withRetry("Error uploading file '${localFile.name}' to parent '$parentId'", onRetry = onRetry) {
            val request = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, name, md5Checksum, size")

            request.mediaHttpUploader.apply {
                isDirectUploadEnabled = false
                setProgressListener { uploader ->
                    val loaded = (uploader.progress * totalSize).toLong()
                    onProgress(loaded, totalSize)
                }
            }

            request.execute()
        }
    }

    fun downloadFile(
        fileId: String,
        outputStream: OutputStream,
        totalSize: Long,
        onProgress: (loaded: Long, total: Long) -> Unit,
        onRetry: ((String) -> Unit)? = null,
    ) {
        withRetry("Error downloading file '$fileId'", onRetry = onRetry) {
            val request = driveService.files().get(fileId)

            request.mediaHttpDownloader.apply {
                setProgressListener { downloader ->
                    val loaded = (downloader.progress * totalSize).toLong()
                    onProgress(loaded, totalSize)
                }
            }

            request.executeMediaAndDownloadTo(outputStream)
        }
    }

    fun deleteFile(fileId: String, onRetry: ((String) -> Unit)? = null) {
        withRetry("Error deleting file '$fileId'", onRetry = onRetry) {
            driveService.files().delete(fileId).execute()
        }
    }

    fun moveFile(fileId: String, oldParentId: String, newParentId: String, newName: String? = null, onRetry: ((String) -> Unit)? = null): GDriveFile {
        return withRetry("Error moving file '$fileId'", onRetry = onRetry) {
            val request = driveService.files().update(fileId, GDriveFile().apply {
                if (newName != null) this.name = newName
            })
                .setAddParents(newParentId)
                .setRemoveParents(oldParentId)
                .setFields("id, name, parents")

            request.execute()
        }
    }

    fun renameFile(fileId: String, newName: String, onRetry: ((String) -> Unit)? = null): GDriveFile {
        return withRetry("Error renaming file '$fileId' to '$newName'", onRetry = onRetry) {
            driveService.files().update(fileId, GDriveFile().apply {
                this.name = newName
            })
                .setFields("id, name")
                .execute()
        }
    }

    fun resolvePathToId(path: String, onRetry: ((String) -> Unit)? = null): String {
        if (path.isEmpty() || path == "/") return "root"

        val parts = path.trim('/').split('/')
        var currentId = "root"

        for (part in parts) {
            val result = withRetry("Error resolving path segment '$part'", onRetry = onRetry) {
                driveService.files().list()
                    .setQ("'$currentId' in parents and name = '$part' and trashed = false")
                    .setFields("files(id, mimeType)")
                    .execute()
            }

            val found = result.files?.firstOrNull()
                ?: throw GDriveResponseException("Path segment '$part' not found in folder '$currentId'")

            currentId = found.id
        }

        return currentId
    }

    fun close() {
        httpTransport.shutdown()
    }

    companion object {
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val GOOGLE_APPS_MIME_PREFIX = "application/vnd.google-apps."

        private val DOWNLOADABLE_GOOGLE_APPS_TYPES = setOf(FOLDER_MIME_TYPE)

        fun isGoogleNativeFile(mimeType: String): Boolean {
            return mimeType.startsWith(GOOGLE_APPS_MIME_PREFIX) && mimeType !in DOWNLOADABLE_GOOGLE_APPS_TYPES
        }
    }
}
