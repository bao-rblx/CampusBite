package com.campusbite.data.remote

import com.campusbite.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    /** Retries idempotent GETs up to [maxRetries] times on IOException. */
    private class RetryInterceptor(private val maxRetries: Int = 2) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            var attempt = 0
            var lastError: IOException? = null
            while (attempt <= maxRetries) {
                try {
                    return chain.proceed(chain.request())
                } catch (e: IOException) {
                    lastError = e
                    attempt++
                }
            }
            throw lastError ?: IOException("Unknown network error")
        }
    }

    fun create(): CampusBiteApi {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor())
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.MOCKAPI_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CampusBiteApi::class.java)
    }
}
