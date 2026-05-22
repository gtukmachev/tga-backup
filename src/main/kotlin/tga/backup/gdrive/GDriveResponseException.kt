package tga.backup.gdrive

import com.google.api.client.googleapis.json.GoogleJsonResponseException

class GDriveResponseException : Exception {

    val code: Int
    val originalMessage: String

    constructor(message: String, cause: GoogleJsonResponseException) : super(
        """
        Exception message: '$message'
        Response code: [${cause.statusCode}]
        Details: '${cause.details?.message ?: cause.message}'
        """.trimIndent().trim(),
        cause
    ) {
        code = cause.statusCode
        originalMessage = cause.details?.message ?: cause.message ?: ""
    }

    constructor(message: String) : super(message) {
        code = -1
        originalMessage = message
    }

    override fun toString(): String {
        return "GDriveResponseException(code=$code, message='$message', originalMessage='$originalMessage')"
    }
}
