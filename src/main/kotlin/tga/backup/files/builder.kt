package tga.backup.files

import tga.backup.gdrive.GDriveClient
import tga.backup.params.Params
import tga.backup.yandex.OkHttpClientBuilder
import tga.backup.yandex.YandexResumableUploader
import java.io.File

fun buildFileOpsByURL(url: String, params: Params): FileOps {
    return when {
        url.startsWith("yandex") -> YandexFileOps(
            yandex = buildYandexClient(params),
            profile = params.profile,
            useCache = params.remoteCache,
            excludePatterns = params.exclude
        )
        url.startsWith("gdrive") -> GDriveFileOps(
            gdrive = buildGDriveClient(params),
            profile = params.profile,
            useCache = params.remoteCache,
            excludePatterns = params.exclude
        )
        else -> LocalFileOps(excludePatterns = params.exclude)
    }
}

fun buildYandexClient(params: Params): YandexResumableUploader {
    val okHttpClient = OkHttpClientBuilder.provideOkHttpClient(params.parallelThreads)
    val token = params.yandexToken ?: throw RuntimeException("Yandex token is missed!")
    return YandexResumableUploader(token, okHttpClient, params.devMode)
}

fun buildGDriveClient(params: Params): GDriveClient {
    val home = System.getProperty("user.home")
    val credentialsPath = params.gdriveCredentials
        ?.replace("~", home)
        ?: File(home, ".tga-backup/client_secret.json").absolutePath
    val tokenStorePath = File(home, ".tga-backup/${params.profile}/gdrive-token").absolutePath
    return GDriveClient(credentialsPath, tokenStorePath)
}
