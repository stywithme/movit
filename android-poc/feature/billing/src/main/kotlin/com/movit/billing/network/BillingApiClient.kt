package com.movit.billing.network

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.movit.billing.Billing
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Dedicated Retrofit client for subscription endpoints — not part of legacy [com.trainingvalidator.poc.network.ApiClient].
 */
object BillingApiClient {
    private const val TAG = "BillingApiClient"

    private val gson by lazy {
        GsonBuilder()
            .setStrictness(Strictness.LENIENT)
            .create()
    }

    private val httpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .connectTimeout(30L, TimeUnit.SECONDS)
            .readTimeout(60L, TimeUnit.SECONDS)
            .writeTimeout(60L, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")

                if (original.header("Authorization") == null) {
                    Billing.requireHost().authHeader()?.let { header ->
                        builder.addHeader("Authorization", header)
                    }
                }

                chain.proceed(builder.build())
            }
            .build()
    }

    private val retrofit by lazy {
        val baseUrl = Billing.requireHost().apiBaseUrl()
        Log.d(TAG, "Initializing billing Retrofit with base URL: $baseUrl")

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val api: SubscriptionApi by lazy {
        retrofit.create(SubscriptionApi::class.java)
    }
}
