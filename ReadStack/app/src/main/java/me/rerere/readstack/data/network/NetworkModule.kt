package me.rerere.readstack.data.network

import me.rerere.readstack.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val CACHE_DIR = "http_cache"
    private const val CACHE_SIZE = 32L * 1024 * 1024 // 32 MB
    private const val USER_AGENT = "ReadStack/${BuildConfig.VERSION_NAME} (Android)"

    fun buildOkHttp(cacheDirProvider: () -> String): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val ua = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
                .build()
            chain.proceed(req)
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(ua)
            .addInterceptor(logging)
            .build()
    }
}
