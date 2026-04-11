package com.trainingvalidator.poc.assessment.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.assessment.AssessmentUploadService
import com.trainingvalidator.poc.assessment.models.*
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.RecommendedProgramData
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.ui.programs.ProgramListActivity
import com.trainingvalidator.poc.ui.main.MainContainerActivity
import com.trainingvalidator.poc.ui.utils.feedbackLanguageCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AssessmentResultActivity - Displays Body Scan results.
 * 
 * Sections:
 * 1. Body Score (motivational hero number)
 * 2. Domain Scores (Mobility, Control, Symmetry, Safety)
 * 3. Body Map (colored regions)
 * 4. Hypothesis Cards (observed patterns)
 * 5. Safety Gates (restrictions)
 * 6. Recommendations
 */
class AssessmentResultActivity : AppCompatActivity() {
    
    private var result: BodyScanResult? = null
    /** Matches Profile / app locale (same as training feedback). */
    private val language: String get() = feedbackLanguageCode()
    
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#121212")

        val resultJson = intent.getStringExtra(EXTRA_RESULT_JSON)
        result = if (!resultJson.isNullOrBlank()) {
            try {
                Gson().fromJson(resultJson, BodyScanResult::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize assessment result", e)
                null
            }
        } else {
            null
        }

        if (result == null) {
            Log.w(TAG, "AssessmentResultActivity started without valid result payload")
        }
        
        buildUI()
    }
    
    private fun buildUI() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }
        
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(32))
        }
        
        val res = result
        
        // ═══════════════════════════════════════════
        // SECTION 1: Body Score Hero
        // ═══════════════════════════════════════════
        content.addView(createHeroSection(res))
        
        // ═══════════════════════════════════════════
        // SECTION 2: Domain Scores
        // ═══════════════════════════════════════════
        content.addView(createSectionTitle(getString(R.string.assessment_domains_title)))
        content.addView(createDomainScoresSection(res))
        
        // ═══════════════════════════════════════════
        // SECTION 3: Body Map
        // ═══════════════════════════════════════════
        content.addView(createSectionTitle(getString(R.string.assessment_body_map_title)))
        content.addView(createBodyMapSection(res))
        
        // ═══════════════════════════════════════════
        // SECTION 4: Hypothesis Cards
        // ═══════════════════════════════════════════
        val hypotheses = res?.hypotheses ?: emptyList()
        if (hypotheses.isNotEmpty()) {
            content.addView(createSectionTitle(getString(R.string.assessment_observations_title)))
            for (card in hypotheses) {
                content.addView(createHypothesisCard(card))
            }
        }
        
        // ═══════════════════════════════════════════
        // SECTION 5: Safety Gates
        // ═══════════════════════════════════════════
        val gates = res?.safetyGates ?: emptyList()
        if (gates.isNotEmpty()) {
            content.addView(createSectionTitle(getString(R.string.assessment_safety_title)))
            for (gate in gates) {
                content.addView(createSafetyGateCard(gate))
            }
        }
        
        // ═══════════════════════════════════════════
        // SECTION 6: Recommendations
        // ═══════════════════════════════════════════
        val recs = res?.recommendations ?: emptyList()
        if (recs.isNotEmpty()) {
            content.addView(createSectionTitle(getString(R.string.assessment_recommendations_title)))
            for (rec in recs.take(5)) {
                content.addView(createRecommendationCard(rec))
            }
        }
        
        // Disclaimer
        content.addView(createDisclaimer())

        // Recommended program section (loaded async)
        val recommendationContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        content.addView(recommendationContainer)
        loadRecommendation(recommendationContainer)
        
        // Action buttons
        content.addView(createActionButtons())
        
        scroll.addView(content)
        setContentView(scroll)
    }

    /**
     * Load prescription recommendation from the backend and display it.
     */
    private fun loadRecommendation(container: LinearLayout) {
        lifecycleScope.launch {
            try {
                val authHeader = AuthManager.getAuthHeader(this@AssessmentResultActivity) ?: return@launch
                val response = withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.getRecommendation(authHeader)
                }
                if (response.isSuccessful && response.body()?.success == true) {
                    val data = response.body()?.data ?: return@launch
                    val program = data.recommendedProgram ?: return@launch
                    container.addView(createRecommendationSection(program, data.classification.reason))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load recommendation", e)
            }
        }
    }

    /**
     * Create the "Recommended for you" UI card.
     */
    private fun createRecommendationSection(program: RecommendedProgramData, reason: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B3A1B"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            lp.bottomMargin = dp(16)
            layoutParams = lp

            addView(TextView(context).apply {
                text = getString(R.string.assessment_recommended_for_you)
                setTextColor(Color.parseColor("#4CAF50"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            })

            addView(TextView(context).apply {
                text = reason
                setTextColor(Color.parseColor("#81C784"))
                textSize = 12f
                setPadding(0, dp(4), 0, dp(12))
            })

            val name = program.name[language] ?: program.name["en"] ?: program.slug
            addView(TextView(context).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
            })

            addView(TextView(context).apply {
                text = getString(R.string.assessment_program_info, program.durationWeeks, program.difficulty, program.type)
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 13f
                setPadding(0, dp(4), 0, dp(16))
            })

            addView(Button(context).apply {
                text = getString(R.string.assessment_start_program)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener { enrollInProgram(program.id) }
            })
        }
    }

    /**
     * Enroll in the recommended program via the ActivePlan API.
     */
    private fun enrollInProgram(programId: String) {
        lifecycleScope.launch {
            try {
                val authHeader = AuthManager.getAuthHeader(this@AssessmentResultActivity) ?: return@launch
                withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.enrollProgram(
                        authHeader,
                        mapOf("programId" to programId)
                    )
                }
                navigateToHome()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enroll in program", e)
                navigateToHome()
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════
    // UI BUILDERS
    // ═══════════════════════════════════════════════════════
    
    private fun createHeroSection(res: BodyScanResult?): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setPadding(dp(24), dp(32), dp(24), dp(32))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(24)
            layoutParams = lp
            
            val score = res?.bodyScore ?: 0f
            val level = res?.fitnessLevel ?: FitnessLevel.AVERAGE
            
            addView(TextView(context).apply {
                text = "${score.toInt()}"
                setTextColor(Color.WHITE)
                textSize = 56f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
            
            addView(TextView(context).apply {
                text = getString(R.string.assessment_body_score)
                setTextColor(Color.parseColor("#A5D6A7"))
                textSize = 14f
                gravity = Gravity.CENTER
            })
            
            addView(TextView(context).apply {
                text = level.getLabel(language)
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
            
            // Disclaimer under score
            addView(TextView(context).apply {
                text = getString(R.string.assessment_motivational_note)
                setTextColor(Color.parseColor("#81C784"))
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        }
    }
    
    private fun createDomainScoresSection(res: BodyScanResult?): LinearLayout {
        val scores = res?.domainScores ?: DomainScores(0f, 0f, null, 0f)
        
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(16)
            layoutParams = lp
            
            addView(createDomainRow(getString(R.string.domain_mobility), scores.mobility, 0xFF4CAF50.toInt()))
            addView(createDomainRow(getString(R.string.domain_control), scores.control, 0xFF2196F3.toInt()))
            if (scores.symmetry != null) {
                addView(createDomainRow(getString(R.string.domain_symmetry), scores.symmetry, 0xFF9C27B0.toInt()))
            }
            addView(createDomainRow(getString(R.string.domain_safety), scores.safety, 0xFFFF9800.toInt()))
        }
    }
    
    private fun createDomainRow(name: String, score: Float, color: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            layoutParams = lp
            
            // Color indicator
            addView(View(context).apply {
                setBackgroundColor(color)
                layoutParams = LinearLayout.LayoutParams(dp(4), dp(32)).apply {
                    rightMargin = dp(12)
                }
            })
            
            addView(TextView(context).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            
            // Score
            addView(TextView(context).apply {
                text = "${score.toInt()}%"
                setTextColor(getScoreColor(score))
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
            })
        }
    }
    
    private fun createBodyMapSection(res: BodyScanResult?): LinearLayout {
        val regions = res?.regions ?: emptyList()
        
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(16)
            layoutParams = lp
            
            if (regions.isEmpty()) {
                addView(TextView(context).apply {
                    text = getString(R.string.assessment_no_region_data)
                    setTextColor(Color.parseColor("#757575"))
                    textSize = 14f
                    gravity = Gravity.CENTER
                })
            } else {
                // Group by region
                val grouped = regions.groupBy { it.region }
                for ((region, regionList) in grouped) {
                    addView(createRegionRow(region, regionList))
                }
            }
            
            // Body Map visual (placeholder — BodyMapView will be a custom Canvas view)
            addView(BodyMapView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(250)
                ).apply { topMargin = dp(16) }
                setRegions(regions)
            })
        }
    }
    
    private fun createRegionRow(region: BodyRegion, regionData: List<AssessmentRegion>): LinearLayout {
        val best = regionData.maxByOrNull { it.regionalScore }!!
        
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
            gravity = Gravity.CENTER_VERTICAL
            
            // Status dot
            addView(View(context).apply {
                setBackgroundColor(best.status.color)
                val size = dp(10)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    rightMargin = dp(10)
                }
            })
            
            // Region name
            addView(TextView(context).apply {
                text = region.getLabel(language)
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            
            // Score
            addView(TextView(context).apply {
                text = "${best.regionalScore.toInt()}%"
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 14f
            })
            
            // Confidence badge
            if (best.confidence != ConfidenceLevel.HIGH) {
                addView(TextView(context).apply {
                    text = if (best.confidence == ConfidenceLevel.MEDIUM) " ~" else " ?"
                    setTextColor(best.confidence.getColor())
                    textSize = 14f
                    setPadding(dp(4), 0, 0, 0)
                })
            }
        }
    }
    
    private fun createHypothesisCard(card: HypothesisCard): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layoutParams = lp
            
            // Observation
            addView(TextView(context).apply {
                text = "📋 ${card.observation.get(language)}"
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            })
            
            // Possible causes
            addView(TextView(context).apply {
                text = getString(R.string.assessment_possible_causes)
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 12f
                setPadding(0, dp(8), 0, dp(4))
            })
            
            for (cause in card.possibleCauses) {
                val icon = when (cause.status) {
                    CauseStatus.CONFIRMED -> "✅"
                    CauseStatus.POSSIBLE -> "❓"
                    CauseStatus.RULED_OUT -> "❌"
                }
                addView(TextView(context).apply {
                    text = "$icon ${cause.cause.get(language)}"
                    setTextColor(when (cause.status) {
                        CauseStatus.CONFIRMED -> Color.parseColor("#4CAF50")
                        CauseStatus.POSSIBLE -> Color.parseColor("#FFC107")
                        CauseStatus.RULED_OUT -> Color.parseColor("#757575")
                    })
                    textSize = 13f
                    setPadding(dp(8), dp(2), 0, dp(2))
                })
            }
            
            // Confidence badge
            addView(TextView(context).apply {
                text = card.confidence.getLabel(language)
                setTextColor(card.confidence.getColor())
                textSize = 11f
                setPadding(0, dp(8), 0, 0)
            })
        }
    }
    
    private fun createSafetyGateCard(gate: SafetyGate): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#3E2723"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(8)
            layoutParams = lp
            
            addView(TextView(context).apply {
                text = "⛔ ${gate.reason.get(language)}"
                setTextColor(Color.parseColor("#FF8A65"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            })
            
            if (gate.blockedExerciseTypes.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = getString(R.string.assessment_blocked, gate.blockedExerciseTypes.take(3).joinToString(", "))
                    setTextColor(Color.parseColor("#BCAAA4"))
                    textSize = 12f
                    setPadding(0, dp(4), 0, 0)
                })
            }
            
            if (gate.allowedAlternatives.isNotEmpty()) {
                addView(TextView(context).apply {
                    text = getString(R.string.assessment_alternatives, gate.allowedAlternatives.take(3).joinToString(", "))
                    setTextColor(Color.parseColor("#A5D6A7"))
                    textSize = 12f
                    setPadding(0, dp(4), 0, 0)
                })
            }
        }
    }
    
    private fun createRecommendationCard(rec: Recommendation): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            layoutParams = lp
            
            // Priority number
            addView(TextView(context).apply {
                text = "${rec.priority}"
                setTextColor(Color.parseColor("#4CAF50"))
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                val size = dp(32)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    rightMargin = dp(12)
                }
                gravity = Gravity.CENTER
            })
            
            // Content
            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            textContainer.addView(TextView(context).apply {
                text = rec.description.get(language)
                setTextColor(Color.WHITE)
                textSize = 14f
            })
            
            textContainer.addView(TextView(context).apply {
                text = "${rec.phase.getLabel(language)} • ${rec.targetRegion.getLabel(language)}"
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 11f
            })
            
            addView(textContainer)
        }
    }
    
    private fun createDisclaimer(): TextView {
        return TextView(this).apply {
            text = getString(R.string.assessment_disclaimer)
            setTextColor(Color.parseColor("#757575"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(16))
        }
    }
    
    private fun createActionButtons(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            addView(Button(context).apply {
                text = getString(R.string.assessment_find_program)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener { navigateToPrograms() }
            })

            addView(Button(context).apply {
                text = getString(R.string.assessment_share)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#1976D2"))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                setOnClickListener {
                    Toast.makeText(context, getString(R.string.share_not_available), Toast.LENGTH_SHORT).show()
                }
            })

            addView(TextView(context).apply {
                text = getString(R.string.assessment_go_home)
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(16))
                setOnClickListener { navigateToHome() }
            })
        }
    }

    /**
     * Navigate to programs list.
     * Phase 0: Opens the programs list for manual selection.
     * Phase 2: Will use Prescription Engine to recommend a specific program.
     */
    private fun navigateToPrograms() {
        try {
            val intent = Intent(this, ProgramListActivity::class.java)
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.w(TAG, "ProgramListActivity not available, going to Home", e)
            navigateToHome()
        }
    }

    /**
     * Navigate to the main home screen.
     */
    private fun navigateToHome() {
        try {
            val intent = Intent(this, MainContainerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "MainContainerActivity not available", e)
        }
        finish()
    }
    
    private fun createSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(12))
        }
    }
    
    private fun getScoreColor(score: Float): Int = when {
        score >= 85 -> 0xFF4CAF50.toInt()
        score >= 65 -> 0xFF8BC34A.toInt()
        score >= 45 -> 0xFFFFC107.toInt()
        score >= 25 -> 0xFFFF9800.toInt()
        else -> 0xFFFF5252.toInt()
    }
    
    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
    
    companion object {
        private const val TAG = "AssessmentResult"
        private const val EXTRA_RESULT_JSON = "assessment_result_json"
        
        fun createIntent(context: Context, result: BodyScanResult): Intent {
            return Intent(context, AssessmentResultActivity::class.java).apply {
                putExtra(EXTRA_RESULT_JSON, Gson().toJson(result))
            }
        }
    }
}
