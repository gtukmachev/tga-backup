package tga.backup.files

import com.squareup.okhttp.OkHttpClient
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
    val okHttpClient: OkHttpClient = OkHttpClientFactory.makeClient()
    okHttpClient.setConnectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    okHttpClient.setReadTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
    okHttpClient.setWriteTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
    okHttpClient.dispatcher.maxRequestsPerHost = 20
    okHttpClient.dispatcher.maxRequests = 100
    return RestClient(credentials, okHttpClient )
}
