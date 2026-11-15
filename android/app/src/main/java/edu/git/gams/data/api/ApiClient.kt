package edu.git.gams.data.api

import android.os.Build
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Automatically selects 10.0.2.2 for emulator, or PC's Wi-Fi IP for physical devices
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    fun getDefaultBaseUrl(): String {
        return if (isEmulator()) "http://10.0.2.2:5000/" else "http://10.185.249.97:5000/"
    }

    private var baseUrl: String = getDefaultBaseUrl()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private var retrofit: Retrofit = buildRetrofit()

    private fun buildRetrofit(): Retrofit {
        val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(cleanUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    var service: ApiService = retrofit.create(ApiService::class.java)
        private set

    fun setBaseUrl(newUrl: String) {
        baseUrl = newUrl
        retrofit = buildRetrofit()
        service = retrofit.create(ApiService::class.java)
    }

    fun getBaseUrl(): String = baseUrl
}
