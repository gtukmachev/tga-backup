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

    fun listFiles(folderId: String, pageToken: String? = null, pageSize: Int = 1000): Pair<List<GDriveFile>, String?> {
        try {
            val result = driveService.files().list()
                .setQ("'$folderId' in parents and trashed = false")
                .setPageSize(pageSize)
                .setPageToken(pageToken)
                .setFields("nextPageToken, files(id, name, mimeType, size, md5Checksum, parents)")
                .execute()

            return (result.files ?: emptyList()) to result.nextPageToken
        } catch (e: GoogleJsonResponseException) {
            throw GDriveResponseException("Error listing files in folder '$folderId'", e)
        }
    }

    fun getFileMetadata(fileId: String): GDriveFile {
        try {
            return driveService.files().get(fileId)
                .setFields("id, name, mimeType, size, md5Checksum, parents")
                .execute()
        } catch (e: GoogleJsonResponseException) {
            throw GDriveResponseException("Error getting metadata for file '$fileId'", e)
        }
    }

    fun createFolder(name: String, parentId: String): GDriveFile {
        val folderMetadata = GDriveFile().apply {
            this.name = name
            this.mimeType = FOLDER_MIME_TYPE
            this.parents = listOf(parentId)
        }

        try {
            return driveService.files().create(folderMetadata)
                .setFields("id, name")
                .execute()
        } catch (e: GoogleJsonResponseException) {
            throw GDriveResponseException("Error creating folder '$name' in parent '$parentId'", e)
        }
    }

    fun uploadFile(
        localFile: File,
        parentId: String,
        onProgress: (loaded: Long, total: Long) -> Unit,
    ): GDriveFile {
        val fileMetadata = GDriveFile().apply {
            this.name = localFile.name
            this.parents = listOf(parentId)
        }

        val mediaContent = FileContent(null, localFile)
        val totalSize = localFile.length()

        try {
            val request = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id, name, md5Checksum, size")

            request.mediaHttpUploader.apply {
                isDirectUploadEnabled = false
                setProgressListener { uploader ->
                    val loaded = (uploader.progress * totalSize).toLong()
                    onProgress(loaded, totalSize)
                }
            }

            return request.execute()
        } catch (e: GoogleJsonResponseException) {
            throw GDriveResponseException("Error uploading file '${localFile.name}' to parent '$parentId'", e)
        }
    }

    fun downloadFile(
        fileId: String,
        outputStream: OutputStream,
        totalSize: Long,
        onProgress: (loaded: Long, total: Long) -> Unit,
    ) {
        try {
            val request = driveService.files().get(fileId)

            request.mediaHttpDownloader.apply {
                setProgressListener { downloader ->
                    val loaded = (downloader.progress * totalSize).toLong()
                    onProgress(loaded, totalSize)
                }
            }

            request.executeMediaAndDownloadTo(outputStream)
        } catch (e: GoogleJsonResponseException) {
            throw GDriveResponseException("Error downloading file '$fileId'", e)
        }
    }

    fun deleteFile(fileId: String) {
        try {
            driveService.files().delete(fileId).execute()
        } catch (e: GoogleJsonResponseException) {
            throw GDriveResponseException("Error deleting file '$fileId'", e)
        }
    }

    fun moveFile(fileId: String, oldParentId: String, newParentId: String, newName: String? = null): GDriveFile {
        try {
            val request = driveService.files().update(fileId, GDriveFile().apply {
                if (newName != null) this.name = newName
            })
                .setAddParents(newParentId)
                .setRemoveParents(oldParentId)
                .setFields("id, name, parents")

            return request.execute()
        } catch (e: GoogleJsonResponseException) {
            throw GDriveResponseException("Error moving file '$fileId'", e)
        }
    }

    fun renameFile(fileId: String, newName: String): GDriveFile {
        try {
            return driveService.files().update(fileId, GDriveFile().apply {
                this.name = newName
            })
                .setFields("id, name")
                .execute()
        } catch (e: GoogleJsonResponseException) {
            throw GDriveResponseException("Error renaming file '$fileId' to '$newName'", e)
        }
    }

    fun resolvePathToId(path: String): String {
        if (path.isEmpty() || path == "/") return "root"

        val parts = path.trim('/').split('/')
        var currentId = "root"

        for (part in parts) {
            val result = driveService.files().list()
                .setQ("'$currentId' in parents and name = '$part' and trashed = false")
                .setFields("files(id, mimeType)")
                .execute()

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
    }
}
