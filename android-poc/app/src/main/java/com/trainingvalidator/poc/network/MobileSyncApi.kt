package com.trainingvalidator.poc.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
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
        @Header("Authorization") authorization: String? = null,
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

    // ─── Program Session Endpoints ──────────────────────────────

    /**
     * Notify server that a program session has started.
     */
    @POST("api/mobile/sessions/{sessionId}/start")
    suspend fun startSession(
        @Path("sessionId") sessionId: String,
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    /**
     * Notify server that a program session has been completed.
     */
    @POST("api/mobile/sessions/{sessionId}/complete")
    suspend fun completeSession(
        @Path("sessionId") sessionId: String,
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    /**
     * Submit a detailed session report.
     */
    @POST("api/mobile/sessions/{sessionId}/report")
    suspend fun reportSession(
        @Path("sessionId") sessionId: String,
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    // ─── User Program Customization Endpoints ─────────────────

    /**
     * Update user program customizations (session modifications, reorders, etc.)
     */
    @PUT("api/mobile/user-programs/{id}")
    suspend fun updateUserProgram(
        @Path("id") userProgramId: String,
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>
}
