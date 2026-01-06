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
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            // Timeouts (important for video)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            // If the server says "retry", OkHttp will try automatically (for GET/HEAD)
            .retryOnConnectionFailure(true)
            .build()
    }
}