package com.trainingvalidator.poc.assessment.engine

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.AssessmentTemplateData
import com.trainingvalidator.poc.network.DomainWeightsData
import com.trainingvalidator.poc.network.TemplateExerciseData

/**
 * AssessmentTemplateManager - Manages assessment templates with offline support.
 * 
 * Fetches the appropriate template from the server and caches it locally.
 * Falls back to cached template or default hardcoded values if offline.
 */
object AssessmentTemplateManager {
    private const val TAG = "AssessmentTemplate"
    private const val PREFS_NAME = "assessment_template_cache"
    private const val KEY_TEMPLATE_JSON = "cached_template"
    private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    private var cachedTemplate: AssessmentTemplateData? = null

    // Default values (hardcoded fallback)
    val DEFAULT_DOMAIN_WEIGHTS = DomainWeightsData(
        mobility = 0.35f,
        control = 0.25f,
        symmetry = 0.20f,
        safety = 0.20f
    )

    val DEFAULT_CORE_EXERCISES = listOf(
        "assessment_overhead_squat",
        "assessment_lunge",
        "assessment_shoulder_mobility"
    )

    /**
     * Resolve the assessment template from the server.
     * Falls back to cached or default template if server is unavailable.
     */
    suspend fun resolve(context: Context, authToken: String): AssessmentTemplateData? {
        try {
            val response = ApiClient.mobileSyncApi.resolveAssessmentTemplate("Bearer $authToken")
            if (response.isSuccessful && response.body()?.success == true) {
                val template = response.body()!!.data
                if (template != null) {
                    cachedTemplate = template
                    saveToCache(context, template)
                    Log.d(TAG, "Template resolved from server: ${template.templateId}")
                    return template
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve template from server, trying cache", e)
        }

        // Try cached version
        val cached = loadFromCache(context)
        if (cached != null) {
            cachedTemplate = cached
            Log.d(TAG, "Using cached template: ${cached.templateId}")
            return cached
        }

        Log.d(TAG, "No template available, will use defaults")
        return null
    }

    fun getCachedTemplate(): AssessmentTemplateData? = cachedTemplate

    fun resetCache() {
        cachedTemplate = null
    }

    /**
     * Get domain weights from template or defaults.
     */
    fun getDomainWeights(): DomainWeightsData {
        return cachedTemplate?.domainWeights ?: DEFAULT_DOMAIN_WEIGHTS
    }

    /**
     * Get core exercise slugs from template or defaults.
     */
    fun getCoreExercises(): List<String> {
        val template = cachedTemplate ?: return DEFAULT_CORE_EXERCISES
        return template.exercises
            .filter { it.entryType == "core" }
            .sortedBy { it.sortOrder }
            .map { it.exerciseSlug }
    }

    /**
     * Get adaptive exercise definitions from template.
     */
    fun getAdaptiveExercises(): List<TemplateExerciseData> {
        val template = cachedTemplate ?: return emptyList()
        return template.exercises
            .filter { it.entryType == "adaptive" }
            .sortedBy { it.sortOrder }
    }

    /**
     * Get the template ID for upload.
     */
    fun getTemplateId(): String? = cachedTemplate?.templateId

    /**
     * Get exercise display info from template.
     */
    fun getExerciseInfo(slug: String): TemplateExerciseData? {
        return cachedTemplate?.exercises?.find { it.exerciseSlug == slug }
    }

    private fun saveToCache(context: Context, template: AssessmentTemplateData) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = Gson().toJson(template)
            prefs.edit()
                .putString(KEY_TEMPLATE_JSON, json)
                .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache template", e)
        }
    }

    private fun loadFromCache(context: Context): AssessmentTemplateData? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0)
            if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) return null

            val json = prefs.getString(KEY_TEMPLATE_JSON, null) ?: return null
            Gson().fromJson(json, AssessmentTemplateData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached template", e)
            null
        }
    }
}
