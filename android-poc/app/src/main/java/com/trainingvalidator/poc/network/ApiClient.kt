package com.trainingvalidator.poc.network

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.trainingvalidator.poc.training.models.StateMessageValue
import com.trainingvalidator.poc.training.models.StateMessageValueTypeAdapter
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API Client Singleton
 * 
 * Provides configured Retrofit instance for API calls.
 * Uses OkHttp with logging and custom Gson for StateMessageValue parsing.
 */
object ApiClient {
    
    private const val TAG = "ApiClient"
    
    /**
     * Lazy-initialized Gson instance with custom type adapters
     */
    private val gson by lazy {
        GsonBuilder()
            .setStrictness(Strictness.LENIENT)
            .registerTypeAdapter(StateMessageValue::class.java, StateMessageValueTypeAdapter())
            .create()
    }
    
    /**
     * Lazy-initialized OkHttpClient with logging and timeouts
     */
    private val httpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        
        OkHttpClient.Builder()
            .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(ApiConfig.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
    
    /**
     * Lazy-initialized Retrofit instance
     */
    private val retrofit by lazy {
        val baseUrl = ApiConfig.getEffectiveBaseUrl()
        Log.d(TAG, "Initializing Retrofit with base URL: $baseUrl")
        
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    /**
     * Mobile Sync API instance
     */
    val mobileSyncApi: MobileSyncApi by lazy {
        retrofit.create(MobileSyncApi::class.java)
    }
    
    /**
     * Get OkHttpClient for direct downloads
     */
    fun getOkHttpClient(): OkHttpClient = httpClient
    
    /**
     * Rebuild client with new base URL
     * Call this when switching between emulator and physical device testing
     */
    fun rebuildWithBaseUrl(baseUrl: String): MobileSyncApi {
        val newRetrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        return newRetrofit.create(MobileSyncApi::class.java)
    }
}
