package com.trainingvalidator.poc.ui.level

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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.trainingvalidator.poc.assessment.ui.PreScreeningActivity
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.DomainLevelData
import com.trainingvalidator.poc.network.LevelProfileData
import com.trainingvalidator.poc.network.LimitingFactorData
import com.trainingvalidator.poc.network.RegionLevelData
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.ui.programs.ProgramListActivity
import com.trainingvalidator.poc.ui.utils.currentLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LevelProfileActivity — Displays the user's training level.
 *
 * Shows:
 * 1. Overall Level (hero number with badge)
 * 2. Domain scores radar (mobility, control, symmetry, safety)
 * 3. Body region breakdown
 * 4. Limiting factors highlighted
 * 5. "What's Next" CTA
 */
class LevelProfileActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LevelProfile"

        fun createIntent(context: Context): Intent {
            return Intent(context, LevelProfileActivity::class.java)
        }
    }

    private val viewModel: LevelProfileViewModel by viewModels()
    private val language: String get() = currentLanguage

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#121212")
        showLoading()
        observeViewModel()
        viewModel.fetchLevelProfile()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is LevelProfileUiState.Loading -> showLoading()
                        is LevelProfileUiState.NoAuth -> showError(getString(R.string.error_login_required))
                        is LevelProfileUiState.NoProfile -> showNoProfile(state.reason)
                        is LevelProfileUiState.Error -> {
                            Log.e(TAG, "Error: ${state.message}")
                            showError(getString(R.string.level_fetch_error))
                        }
                        is LevelProfileUiState.Success -> {
                            previousProfile = state.previousProfile
                            buildUI(state.profile)
                            checkLevelUpCelebration(state.profile)
                        }
                    }
                }
            }
        }
    }

    private fun showLoading() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        layout.addView(ProgressBar(this).apply {
            isIndeterminate = true
        })

        layout.addView(TextView(this).apply {
            text = getString(R.string.loading_level)
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(layout)
    }

    private var previousProfile: LevelProfileData? = null

    /**
     * Check if the user just leveled up and show a celebration dialog.
     */
    private fun checkLevelUpCelebration(profile: LevelProfileData) {
        val prefs = getSharedPreferences("level_prefs", Context.MODE_PRIVATE)
        val lastSeenLevel = prefs.getInt("last_seen_level", 0)

        if (lastSeenLevel > 0 && profile.overallLevel > lastSeenLevel) {
            showLevelUpCelebration(lastSeenLevel, profile.overallLevel, profile.levelInfo.name.en)
        }

        prefs.edit().putInt("last_seen_level", profile.overallLevel).apply()
    }

    /**
     * Show a level-up celebration overlay.
     */
    private fun showLevelUpCelebration(fromLevel: Int, toLevel: Int, levelName: String) {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isClickable = true

            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(40), dp(80), dp(40), dp(80))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            content.addView(TextView(context).apply {
                text = "LEVEL UP!"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 36f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            })

            content.addView(TextView(context).apply {
                text = "$fromLevel → $toLevel"
                setTextColor(Color.WHITE)
                textSize = 48f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(8))
            })

            content.addView(TextView(context).apply {
                text = levelName
                setTextColor(Color.parseColor("#4CAF50"))
                textSize = 24f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            })

            content.addView(TextView(context).apply {
                text = "Your hard work is paying off! Keep pushing forward."
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, dp(32))
            })

            content.addView(Button(context).apply btn@{
                text = "Continue"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(200), LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    (this@btn.parent?.parent as? FrameLayout)?.let {
                        (it.parent as? FrameLayout)?.removeView(it)
                    }
                }
            })

            addView(content)
        }

        // Add the overlay on top of the current content
        val decorView = window.decorView as FrameLayout
        decorView.addView(overlay)

        // Auto-dismiss after 5 seconds
        overlay.postDelayed({
            try { decorView.removeView(overlay) } catch (_: Exception) {}
        }, 5000)
    }

    private fun buildUI(profile: LevelProfileData) {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(32))
        }

        // Back button
        content.addView(TextView(this).apply {
            text = "← Back"
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            setPadding(0, 0, 0, dp(16))
            setOnClickListener { finish() }
        })

        // Hero: Level badge
        content.addView(createLevelHero(profile))

        // Domain Scores
        content.addView(createSectionTitle(
            if (language == "ar") "أبعاد المستوى" else "Level Dimensions"
        ))
        content.addView(createDomainLevelsSection(profile.domainLevels))

        // Region Levels
        content.addView(createSectionTitle(
            if (language == "ar") "مستوى المناطق" else "Region Levels"
        ))
        content.addView(createRegionLevelsSection(profile.regionLevels))

        // Limiting Factors
        if (profile.limitingFactors.isNotEmpty()) {
            content.addView(createSectionTitle(
                if (language == "ar") "نقاط التحسين" else "Areas to Improve"
            ))
            content.addView(createLimitingFactorsSection(profile.limitingFactors))
        }

        // Level Comparison (Phase 5.1)
        content.addView(createComparisonSection(profile))

        // What's Next CTA
        content.addView(createWhatsNext(profile))

        scroll.addView(content)
        setContentView(scroll)
    }

    /**
     * Create a "Your Progress" comparison section showing assessment improvement.
     */
    private fun createComparisonSection(profile: LevelProfileData): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            addView(createSectionTitle(
                if (language == "ar") "تقدمك" else "Your Progress"
            ))

            // Fetch assessment progress for comparison
            lifecycleScope.launch {
                try {
                    val authHeader = AuthManager.getAuthHeader(this@LevelProfileActivity) ?: return@launch
                    val response = withContext(Dispatchers.IO) {
                        ApiClient.mobileSyncApi.getAssessmentProgress(authHeader)
                    }
                    if (response.isSuccessful && response.body()?.success == true) {
                        val data = response.body()?.data ?: return@launch
                        val prev = data.previous ?: return@launch
                        val curr = data.current
                        val changes = data.changes

                        // Body score comparison
                        addView(createComparisonRow(
                            "Body Score",
                            prev.bodyScore.toFloat(),
                            curr.bodyScore.toFloat(),
                            changes?.bodyScoreDelta?.toFloat() ?: 0f
                        ))

                        // Domain comparisons
                        addView(createComparisonRow(
                            "Mobility",
                            prev.domainScores.mobility.toFloat(),
                            curr.domainScores.mobility.toFloat(),
                            changes?.mobilityDelta?.toFloat() ?: 0f
                        ))
                        addView(createComparisonRow(
                            "Control",
                            prev.domainScores.control.toFloat(),
                            curr.domainScores.control.toFloat(),
                            changes?.controlDelta?.toFloat() ?: 0f
                        ))
                        addView(createComparisonRow(
                            "Safety",
                            prev.domainScores.safety.toFloat(),
                            curr.domainScores.safety.toFloat(),
                            changes?.safetyDelta?.toFloat() ?: 0f
                        ))

                        if (changes?.isRealImprovement == true) {
                            addView(TextView(context).apply {
                                text = "Real improvement detected!"
                                setTextColor(Color.parseColor("#4CAF50"))
                                textSize = 14f
                                setTypeface(null, Typeface.BOLD)
                                gravity = Gravity.CENTER
                                setPadding(0, dp(12), 0, 0)
                            })
                        }
                    } else {
                        addView(TextView(context).apply {
                            text = "Complete a reassessment to see your progress."
                            setTextColor(Color.parseColor("#757575"))
                            textSize = 13f
                            gravity = Gravity.CENTER
                            setPadding(0, dp(8), 0, 0)
                        })
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load comparison", e)
                    addView(TextView(context).apply {
                        text = "Assessment comparison not available."
                        setTextColor(Color.parseColor("#757575"))
                        textSize = 13f
                        gravity = Gravity.CENTER
                    })
                }
            }
        }
    }

    private fun createComparisonRow(label: String, prev: Float, curr: Float, delta: Float): LinearLayout {
        val isImproved = delta > 0
        val color = when {
            delta > 5 -> "#4CAF50"
            delta > 0 -> "#8BC34A"
            delta > -5 -> "#FFC107"
            else -> "#FF5252"
        }
        val arrow = if (isImproved) "↑" else if (delta < 0) "↓" else "→"

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }

            addView(TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(TextView(context).apply {
                text = "${prev.toInt()}"
                setTextColor(Color.parseColor("#757575"))
                textSize = 14f
                setPadding(0, 0, dp(8), 0)
            })

            addView(TextView(context).apply {
                text = "$arrow"
                setTextColor(Color.parseColor(color))
                textSize = 16f
                setPadding(0, 0, dp(8), 0)
            })

            addView(TextView(context).apply {
                text = "${curr.toInt()}"
                setTextColor(Color.parseColor(color))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            })

            addView(TextView(context).apply {
                text = " (${if (delta > 0) "+" else ""}${delta.toInt()})"
                setTextColor(Color.parseColor(color))
                textSize = 12f
            })
        }
    }

    private fun createLevelHero(profile: LevelProfileData): LinearLayout {
        val levelColor = profile.levelInfo.color ?: "#4CAF50"
        val levelName = if (language == "ar") {
            profile.levelInfo.name.ar
        } else {
            profile.levelInfo.name.en
        }
        val levelDesc = profile.levelInfo.description?.let {
            if (language == "ar") it.ar else it.en
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(dp(24), dp(32), dp(24), dp(32))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(24)
            layoutParams = lp

            // Level number
            addView(TextView(context).apply {
                text = "LEVEL ${profile.overallLevel}"
                setTextColor(Color.parseColor(levelColor))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                letterSpacing = 0.15f
            })

            // Level name
            addView(TextView(context).apply {
                text = levelName
                setTextColor(Color.WHITE)
                textSize = 36f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, dp(4))
            })

            // Body score
            addView(TextView(context).apply {
                text = "Body Score: ${profile.bodyScore.toInt()}"
                setTextColor(Color.parseColor("#B0B0B0"))
                textSize = 16f
                gravity = Gravity.CENTER
            })

            // Description
            if (levelDesc != null) {
                addView(TextView(context).apply {
                    text = levelDesc
                    setTextColor(Color.parseColor("#808080"))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(8), 0, 0)
                })
            }
        }
    }

    private fun createDomainLevelsSection(domains: List<DomainLevelData>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(16)
            layoutParams = lp

            val domainColors = mapOf(
                "mobility" to "#4CAF50",
                "control" to "#2196F3",
                "symmetry" to "#9C27B0",
                "safety" to "#FF9800"
            )

            val domainNames = mapOf(
                "mobility" to (if (language == "ar") "المرونة" else "Mobility"),
                "control" to (if (language == "ar") "التحكم" else "Control"),
                "symmetry" to (if (language == "ar") "التماثل" else "Symmetry"),
                "safety" to (if (language == "ar") "السلامة" else "Safety")
            )

            for (domain in domains) {
                val color = domainColors[domain.domain] ?: "#FFFFFF"
                val name = domainNames[domain.domain] ?: domain.domain

                addView(createLevelRow(
                    name = name,
                    level = domain.level,
                    score = domain.score,
                    color = Color.parseColor(color)
                ))
            }
        }
    }

    private fun createRegionLevelsSection(regions: List<RegionLevelData>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(16)
            layoutParams = lp

            for (region in regions) {
                val displayName = region.region.replaceFirstChar { it.uppercase() }

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))

                    // Limiting indicator
                    if (region.isLimiting) {
                        addView(TextView(context).apply {
                            text = "⚠"
                            textSize = 12f
                            setPadding(0, 0, dp(8), 0)
                        })
                    }

                    // Name
                    addView(TextView(context).apply {
                        text = displayName
                        setTextColor(if (region.isLimiting) Color.parseColor("#FF9800") else Color.WHITE)
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })

                    // Level badge
                    addView(TextView(context).apply {
                        text = "L${region.level}"
                        setTextColor(Color.parseColor("#B0B0B0"))
                        textSize = 12f
                        setPadding(dp(8), 0, dp(8), 0)
                    })

                    // Score
                    addView(TextView(context).apply {
                        text = "${region.score.toInt()}%"
                        setTextColor(getScoreColor(region.score.toFloat()))
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                    })
                })
            }
        }
    }

    private fun createLimitingFactorsSection(factors: List<LimitingFactorData>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(16)
            layoutParams = lp

            for (factor in factors) {
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundColor(Color.parseColor("#2A1A00"))
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    gravity = Gravity.CENTER_VERTICAL
                    val itemLp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    itemLp.bottomMargin = dp(6)
                    layoutParams = itemLp

                    val icon = if (factor.type == "domain") "📊" else "🦴"
                    val displayName = factor.code.replaceFirstChar { it.uppercase() }

                    addView(TextView(context).apply {
                        text = "$icon $displayName"
                        setTextColor(Color.parseColor("#FFB74D"))
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })

                    addView(TextView(context).apply {
                        text = "Level ${factor.currentLevel} → ${factor.targetLevel}"
                        setTextColor(Color.parseColor("#FF9800"))
                        textSize = 13f
                    })
                })
            }
        }
    }

    private fun createWhatsNext(profile: LevelProfileData): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1B5E20"))
            setPadding(dp(20), dp(20), dp(20), dp(20))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(16)
            layoutParams = lp

            addView(TextView(context).apply {
                text = if (language == "ar") "الخطوة القادمة" else "What's Next?"
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
            })

            val suggestion = if (profile.limitingFactors.isNotEmpty()) {
                val top = profile.limitingFactors.first()
                val area = top.code.replaceFirstChar { it.uppercase() }
                "Focus on improving your $area — it's ${top.gap} level(s) behind your overall level."
            } else {
                "You're well balanced! Keep training to reach the next level."
            }

            addView(TextView(context).apply {
                text = suggestion
                setTextColor(Color.parseColor("#A5D6A7"))
                textSize = 14f
                setPadding(0, dp(8), 0, dp(12))
            })

            addView(Button(context).apply {
                text = if (language == "ar") "ابحث عن برنامج" else "Find a Program"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    try {
                        startActivity(Intent(this@LevelProfileActivity, ProgramListActivity::class.java))
                    } catch (e: Exception) {
                        Log.w(TAG, "ProgramListActivity not available", e)
                    }
                }
            })
        }
    }

    private fun createLevelRow(name: String, level: Int, score: Double, color: Int): LinearLayout {
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

            // Name
            addView(TextView(context).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            // Level badge
            addView(TextView(context).apply {
                text = "L$level"
                setTextColor(Color.parseColor("#808080"))
                textSize = 12f
                setPadding(dp(8), 0, dp(8), 0)
            })

            // Score
            addView(TextView(context).apply {
                text = "${score.toInt()}%"
                setTextColor(getScoreColor(score.toFloat()))
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
            })
        }
    }

    private fun showNoProfile(message: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(32), dp(64), dp(32), dp(32))
        }

        layout.addView(TextView(this).apply {
            text = "📊"
            textSize = 48f
            gravity = Gravity.CENTER
        })

        layout.addView(TextView(this).apply {
            text = if (language == "ar") "لم يتم التقييم بعد" else "No Assessment Yet"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        })

        layout.addView(TextView(this).apply {
            text = if (language == "ar") {
                "أكمل تقييم حركي لمعرفة مستواك"
            } else {
                "Complete a body scan assessment to discover your training level."
            }
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })

        layout.addView(Button(this).apply {
            text = if (language == "ar") "ابدأ التقييم" else "Start Assessment"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                try {
                    startActivity(PreScreeningActivity.createIntent(this@LevelProfileActivity))
                } catch (e: Exception) {
                    Log.w(TAG, "PreScreeningActivity not available", e)
                }
                finish()
            }
        })

        layout.addView(TextView(this).apply {
            text = "← Back"
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            setOnClickListener { finish() }
        })

        setContentView(layout)
    }

    private fun showError(message: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(32), dp(64), dp(32), dp(32))
        }

        layout.addView(TextView(this).apply {
            text = message
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 16f
            gravity = Gravity.CENTER
        })

        layout.addView(Button(this).apply {
            text = "Go Back"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(0, dp(16), 0, 0)
            setOnClickListener { finish() }
        })

        setContentView(layout)
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
        score >= 85f -> 0xFF4CAF50.toInt()
        score >= 65f -> 0xFF8BC34A.toInt()
        score >= 45f -> 0xFFFFC107.toInt()
        score >= 25f -> 0xFFFF9800.toInt()
        else -> 0xFFFF5252.toInt()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
