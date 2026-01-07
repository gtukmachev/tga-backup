package tga.backup.yandex

import okhttp3.Response

class YandexResponseException(
    message: String,
    val response: Response,
    val srcFilePath: String? = null,
    val dstFilePath: String? = null
) : Exception(
    """
    Source file: '$srcFilePath'
    Destination file: '$dstFilePath'
    Exception message: '$message' 
    Response code: [${response.code}]
    OriginalMessage: '${response.message}'
    """.trimIndent().trim()
) {
    val code: Int = response.code
    val originalMessage: String = response.message

    override fun toString(): String {
        return "YandexResponseException(code=$code, message='$message', originalMessage='$originalMessage', srcFilePath=$srcFilePath, dstFilePath=$dstFilePath)"
    }
}