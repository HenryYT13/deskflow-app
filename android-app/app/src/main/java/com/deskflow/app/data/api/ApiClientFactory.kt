package com.deskflow.app.data.api

import com.deskflow.app.data.local.SecurePreferences
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClientFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun create(securePreferences: SecurePreferences): FocusApiService {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()

            // Attach Bearer token if device is paired
            val token = securePreferences.deviceToken
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }

            builder.header("User-Agent", "DeskFlow-FocusMode-Android/1.0.0")
            builder.header("Accept", "application/json")

            chain.proceed(builder.build())
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .build()

        val baseUrl = securePreferences.apiUrl.let {
            if (it.endsWith("/")) it else "$it/"
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FocusApiService::class.java)
    }
}
