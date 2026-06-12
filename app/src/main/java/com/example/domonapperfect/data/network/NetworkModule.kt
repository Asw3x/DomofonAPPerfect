package com.example.domonapperfect.data.network

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object NetworkModule {
    private const val BASE_URL = "https://api.domonap.ru/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }
    
    private var tokenProvider: (() -> String?)? = null
    private var onUnauthorized: (() -> Unit)? = null

    fun init(tokenProvider: () -> String?, onUnauthorized: () -> Unit) {
        this.tokenProvider = tokenProvider
        this.onUnauthorized = onUnauthorized
    }

    private val instanceId = java.util.UUID.randomUUID().toString()

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                    .addHeader("dom-app", "mobile;")
                    .addHeader("dom-platform", "Android;")
                    .addHeader("instanceId", "$instanceId;")
                    
                tokenProvider?.invoke()?.let { token ->
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }
                val response = chain.proceed(requestBuilder.build())
                if (response.code == 401) {
                    onUnauthorized?.invoke()
                }
                response
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val domonapApi: DomonapApi by lazy { retrofit.create(DomonapApi::class.java) }
}
