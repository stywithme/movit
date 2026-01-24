package com.trainingvalidator.poc.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url
import okhttp3.ResponseBody

/**
 * Mobile Sync API Interface
 * 
 * Retrofit interface for the mobile sync endpoints.
 */
interface MobileSyncApi {
    
    /**
     * Sync exercises with the server.
     * 
     * @param updatedAfter Optional ISO timestamp for incremental sync
     * @param forceRefresh Force full sync, ignoring updatedAfter
     * @return MobileSyncResponse with exercises and audio manifest
     */
    @GET("api/mobile/sync")
    suspend fun sync(
        @Query("updatedAfter") updatedAfter: String? = null,
        @Query("forceRefresh") forceRefresh: Boolean? = null
    ): Response<MobileSyncResponse>
    
    /**
     * Download an audio file.
     * 
     * @param url Full URL or path to the audio file
     * @return ResponseBody containing the audio data
     */
    @Streaming
    @GET
    suspend fun downloadAudio(@Url url: String): Response<ResponseBody>
}
