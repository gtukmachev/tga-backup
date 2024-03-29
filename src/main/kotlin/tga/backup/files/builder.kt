package tga.backup.files

import com.yandex.disk.rest.Credentials
import com.yandex.disk.rest.OkHttpClientFactory
import com.yandex.disk.rest.RestClient
import tga.backup.params.Params

fun buildFileOpsByURL(url: String, params: Params): FileOps {
    return when {
        url.startsWith("yandex") -> YandexFileOps(buildYandexClient(params))
        else -> LocalFileOps()
    }
}

fun buildYandexClient(params: Params): RestClient {
    val credentials = Credentials(params.yandexUser, params.yandexToken)
    return RestClient(credentials, OkHttpClientFactory.makeClient() )
}
