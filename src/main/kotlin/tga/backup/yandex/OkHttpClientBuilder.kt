package tga.backup.yandex

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object OkHttpClientBuilder {

    fun provideOkHttpClient(parallelThreads: Int): OkHttpClient {
        // 1. Configure dispatcher for parallelism
        val dispatcher = Dispatcher()
        // Max total requests (default is 64, which is enough, but can be increased)
        dispatcher.maxRequests = maxOf(parallelThreads * 2, 64)
        // IMPORTANT: Max requests to ONE host (cloud-api.yandex.net).
        // Default is 5. Set to parallelThreads + 2 so all your threads work truly in parallel.
        dispatcher.maxRequestsPerHost = parallelThreads + 2

        // 2. Configure connection pool
        // Keep some idle connections to avoid re-opening TCP/TLS every time.
        // keepAliveDuration = 5 minutes.
        val connectionPool = ConnectionPool(parallelThreads + 2, 5L, TimeUnit.MINUTES)

        return OkHttpClient.Builder()
            //.protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            // Timeouts (important for video)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.MINUTES)
            .writeTimeout(1, TimeUnit.MINUTES)
            // If the server says "retry", OkHttp will try automatically (for GET/HEAD)
            .retryOnConnectionFailure(true)
            // 3. Добавляем интерцептор для подмены User-Agent
            .addInterceptor { chain ->
                val spoofUserAgent = generateSpoofUserAgent()

                val originalRequest = chain.request()
                val newRequest = originalRequest.newBuilder()
                    // Притворяемся обычным браузером или официальным клиентом,
                    // чтобы обойти шейпинг для "неизвестных скриптов"
                    .header("User-Agent", spoofUserAgent)
                    // Иногда помогает явное указание Connection: close, чтобы не держать сокет (но снизит скорость на мелких файлах)
                    .build()
                chain.proceed(newRequest)
            }
            .build()
    }

    // Функция генерации "фейкового" заголовка
    fun generateSpoofUserAgent(): String {
        // 1. Генерация random_session_id (32 символа, A-Z и 0-9)
        val charPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sessionId = (1..32)
            .map { charPool.random() }
            .joinToString("")

        // 2. Хардкод ID, как в скрипте (видимо, это ID "типовой" установки)
        val id = "6BD01244C7A94456BBCEE7EEC990AEAD"
        val id2 = "0F370CD40C594A4783BC839C846B999C"

        // 3. Собираем JSON вручную, чтобы гарантировать отсутствие пробелов
        // Python: separators=(',', ':') -> {"key":"value"}
        val jsonParams = "{" +
                "\"os\":\"windows\"," +
                "\"dtype\":\"ydisk3\"," +
                "\"vsn\":\"3.2.37.4977\"," +
                "\"id\":\"$id\"," +
                "\"id2\":\"$id2\"," +
                "\"session_id\":\"$sessionId\"" +
                "}"

        return "Yandex.Disk $jsonParams"
    }
}