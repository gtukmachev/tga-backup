package tga.backup.files

import tga.backup.params.Params
import tga.backup.yandex.OkHttpClientBuilder
import tga.backup.yandex.YandexResumableUploader

fun buildFileOpsByURL(url: String, params: Params): FileOps {
    return when {
        url.startsWith("yandex") -> YandexFileOps(buildYandexClient(params), excludePatterns = params.exclude)
        else -> LocalFileOps(excludePatterns = params.exclude)
    }
}

fun buildYandexClient(params: Params): YandexResumableUploader {
    val okHttpClient = OkHttpClientBuilder.provideOkHttpClient(params.parallelThreads)
    val token = params.yandexToken ?: throw RuntimeException("Yandex token is missed!")
    return YandexResumableUploader(token, okHttpClient, params.devMode)
}
