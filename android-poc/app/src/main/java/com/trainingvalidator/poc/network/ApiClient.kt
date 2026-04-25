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
    
    private var context: android.content.Context? = null

    fun init(context: android.content.Context) {
        this.context = context.applicationContext
    }

    /**
     * Authenticator to handle 401 Unauthorized errors
     * Automatically gets new access token using refresh token
     */
    private class TokenAuthenticator : okhttp3.Authenticator {
        override fun authenticate(route: okhttp3.Route?, response: okhttp3.Response): okhttp3.Request? {
            val ctx = context ?: return null
            
            // Avoid infinite loops
            if (response.responseCount > 3) return null

            val refreshToken = com.trainingvalidator.poc.storage.AuthManager.getRefreshToken(ctx) ?: return null
            
            // Synchronously refresh token
            // We need a separate Retrofit instance or simple HTTP call to avoid recursion/deadlock
            // But since we are inside Authenticator, let's use a simpler approach or a dedicated auth client
            
            // Creates a separate minimal client just for refresh to avoid interceptor recursion issues
            val refreshClient = ApiClient.getOkHttpClient().newBuilder()
                .authenticator(okhttp3.Authenticator.NONE) // No authenticator for refresh request
                .build()
                
            val refreshRetrofit = Retrofit.Builder()
                .baseUrl(ApiConfig.getEffectiveBaseUrl())
                .client(refreshClient)
                .addConverterFactory(GsonConverterFactory.create(ApiClient.gson))
                .build()
                
            val authApi = refreshRetrofit.create(AuthApi::class.java)
            
            return try {
                val refreshResponse = runBlocking {
                     authApi.refreshToken(RefreshTokenRequest(refreshToken))
                }
                
                if (refreshResponse.isSuccessful && refreshResponse.body()?.success == true) {
                    val newTokens = refreshResponse.body()?.data
                    if (newTokens != null) {
                         com.trainingvalidator.poc.storage.AuthManager.saveNewTokens(
                             ctx, 
                             newTokens.accessToken, 
                             newTokens.refreshToken,
                             newTokens.expiresIn.toLong()
                         )
                         
                         // Retry original request with new token
                         response.request.newBuilder()
                             .header("Authorization", "Bearer ${newTokens.accessToken}")
                             .build()
                    } else null
                } else {
                    null // Refresh failed
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh failed", e)
                null
            }
        }
        
        // Helper specifically for runBlocking in Java-compatible way if needed, 
        // but simple runBlocking { } works in Kotlin
        private fun <T> runBlocking(block: suspend kotlinx.coroutines.CoroutineScope.() -> T): T {
            return kotlinx.coroutines.runBlocking(block = block)
        }
        
        // Extension property to count responses
        private val okhttp3.Response.responseCount: Int
            get() {
                var result = 1
                var prior = priorResponse
                while (prior != null) {
                    result++
                    prior = prior.priorResponse
                }
                return result
            }
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
            .authenticator(TokenAuthenticator()) // Register Authenticator
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                
                // Add Auth header if available and not already present
                if (original.header("Authorization") == null) {
                     context?.let { ctx ->
                         com.trainingvalidator.poc.storage.AuthManager.getAccessToken(ctx)?.let { token ->
                             builder.addHeader("Authorization", "Bearer $token")
                         }
                     }
                }
                    
                chain.proceed(builder.build())
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
     * Auth API instance
     */
    val authApi: AuthApi by lazy {
        retrofit.create(AuthApi::class.java)
    }

    /**
     * User booking API (sessions, rules)
     */
    val bookingApi: BookingApi by lazy {
        retrofit.create(BookingApi::class.java)
    }

    val subscriptionApi: SubscriptionApi by lazy {
        retrofit.create(SubscriptionApi::class.java)
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
