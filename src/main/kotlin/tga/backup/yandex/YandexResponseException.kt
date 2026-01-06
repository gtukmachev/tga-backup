package tga.backup.yandex

import okhttp3.Response

class YandexResponseException(message: String, val response: Response) : Exception("$message [code=${response.code}] originalMessage: ['${response.message}']") {
    val code: Int = response.code
    val originalMessage: String = response.message

    override fun toString(): String {
        return "YandexResponseException(code=$code, message='$message', originalMessage='$originalMessage')"
    }
}