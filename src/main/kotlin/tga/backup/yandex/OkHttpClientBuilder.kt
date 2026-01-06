package tga.backup.yandex

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object OkHttpClientBuilder {

    fun provideOkHttpClient(): OkHttpClient {
        // 1. Настройка диспетчера для параллельности
        val dispatcher = Dispatcher()
        // Максимум запросов всего (по умолчанию 64, нам хватит, но можно увеличить)
        dispatcher.maxRequests = 20
        // ВАЖНО: Максимум запросов к ОДНОМУ хосту (cloud-api.yandex.net).
        // По умолчанию 5. Ставим 10, чтобы все ваши потоки работали реально параллельно.
        dispatcher.maxRequestsPerHost = 10

        // 2. Настройка пула соединений
        // Держим 10 idle (простаивающих) соединений, чтобы не переоткрывать TCP/TLS каждый раз.
        // keepAliveDuration = 5 минут.
        val connectionPool = ConnectionPool(10, 5L, TimeUnit.MINUTES)

        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            // Тайм-ауты (важны для видео)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            // Если вдруг сервер скажет "повтори", OkHttp сам попробует (для GET/HEAD)
            .retryOnConnectionFailure(true)
            .build()
    }
}