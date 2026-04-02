package com.trainingvalidator.poc.ui.report

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.ProgressionEntryData
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.training.report.PostTrainingReport
import com.trainingvalidator.poc.ui.report.ReportUiHelper as H
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen 7 — Progression
 *
 * Shows progression changes triggered by the user's performance:
 *   - Level-up celebration when training parameters increased
 *   - Deload notice when parameters decreased
 *   - What changed (before/after with progress bars)
 *   - Why it happened (linked to performance metrics)
 *   - What's next (goals for next progression)
 *   - Motivational message when no changes occurred
 */
class ProgressionFragment : Fragment() {

    companion object {
        private const val TAG = "ProgressionFragment"
        fun newInstance() = ProgressionFragment()
    }

    private var report: PostTrainingReport? = null
    private var isArabic = false
    private var sessionId: String? = null
    private var contentCol: LinearLayout? = null
    private var loadingView: ProgressBar? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val scroll = H.pageRoot(requireContext())
        contentCol = H.pageColumn(requireContext())
        scroll.addView(contentCol)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindData()
    }

    fun setData(report: PostTrainingReport, isArabic: Boolean, sessionId: String? = null) {
        this.report = report
        this.isArabic = isArabic
        this.sessionId = sessionId
        if (contentCol != null) bindData()
    }

    private fun bindData() {
        val col = contentCol ?: return
        col.removeAllViews()

        col.addView(H.sectionTitle(requireContext(), "\uD83D\uDCC8", getString(R.string.progression_page_title)))
        col.addView(H.sectionSubtitle(requireContext(),
            if (isArabic) "التغييرات بناءً على أدائك" else "Changes based on your performance"
        ))

        showLoading()
        fetchProgressionData()
    }

    private fun showLoading() {
        val col = contentCol ?: return
        loadingView = ProgressBar(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                topMargin = H.dp(requireContext(), 40)
            }
        }
        col.addView(loadingView)

        col.addView(TextView(requireContext()).apply {
            text = getString(R.string.progression_loading)
            textSize = 14f
            setTextColor(H.textMuted(requireContext()))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(requireContext(), 12) }
            tag = "loading_text"
        })
    }

    private fun fetchProgressionData() {
        val ctx = context ?: return
        val authHeader = AuthManager.getAuthHeader(ctx) ?: run {
            showNoChanges()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val sid = sessionId
                    if (sid != null) {
                        ApiClient.mobileSyncApi.getSessionProgression(authHeader, sid)
                    } else {
                        ApiClient.mobileSyncApi.getRecentProgression(authHeader)
                    }
                }

                if (!isAdded) return@launch

                val changes = response.body()?.data
                if (changes.isNullOrEmpty()) {
                    showNoChanges()
                } else {
                    showProgressionChanges(changes)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch progression data", e)
                if (isAdded) showNoChanges()
            }
        }
    }

    private fun clearLoading() {
        val col = contentCol ?: return
        loadingView?.let { col.removeView(it) }
        col.findViewWithTag<View>("loading_text")?.let { col.removeView(it) }
    }

    private fun showProgressionChanges(changes: List<ProgressionEntryData>) {
        val col = contentCol ?: return
        val ctx = requireContext()
        clearLoading()

        val hasIncrease = changes.any { it.newValue > it.previousValue }
        val hasDecrease = changes.any { it.newValue < it.previousValue }

        // --- Header badge ---
        val headerCard = H.glassCard(ctx, borderColor = if (hasIncrease) H.colorGreen(ctx) else H.colorOrange(ctx))

        val badgeIcon = TextView(ctx).apply {
            text = if (hasIncrease) "\uD83C\uDF1F" else "\uD83D\uDD27"
            textSize = 40f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        headerCard.addView(badgeIcon)

        val titleText = if (hasIncrease) getString(R.string.progression_level_up) else getString(R.string.progression_deload_title)
        headerCard.addView(TextView(ctx).apply {
            text = titleText
            textSize = 24f
            setTextColor(H.textWhite(ctx))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 8) }
        })

        val subtitleText = if (hasIncrease) getString(R.string.progression_great_performance) else getString(R.string.progression_deload_message)
        headerCard.addView(TextView(ctx).apply {
            text = subtitleText
            textSize = 14f
            setTextColor(H.textMuted(ctx))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 4) }
        })

        col.addView(headerCard)

        // --- What Changed section ---
        col.addView(sectionLabel(getString(R.string.progression_what_changed)))

        for (change in changes) {
            col.addView(buildChangeCard(change))
        }

        // --- Why section ---
        col.addView(sectionLabel(getString(R.string.progression_why)))
        col.addView(buildWhyCard(hasIncrease, hasDecrease))

        // --- What's Next section ---
        col.addView(sectionLabel(getString(R.string.progression_what_next)))
        col.addView(buildNextCard(hasIncrease, hasDecrease))
    }

    private fun buildChangeCard(change: ProgressionEntryData): LinearLayout {
        val ctx = requireContext()
        val isIncrease = change.newValue > change.previousValue
        val accentColor = if (isIncrease) H.colorGreen(ctx) else H.colorOrange(ctx)

        val card = H.glassCard(ctx, borderColor = accentColor)

        // Arrow + field label row
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        topRow.addView(TextView(ctx).apply {
            text = if (isIncrease) "\u2191" else "\u2193"
            textSize = 28f
            setTextColor(accentColor)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = H.dp(ctx, 12) }
        })

        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val fieldLabel = getFieldLabel(change.field)
        val changeText = if (isIncrease) {
            getString(R.string.progression_change_increase, fieldLabel, formatValue(change.field, change.previousValue), formatValue(change.field, change.newValue))
        } else {
            getString(R.string.progression_change_decrease, fieldLabel, formatValue(change.field, change.previousValue), formatValue(change.field, change.newValue))
        }
        textCol.addView(TextView(ctx).apply {
            text = changeText
            textSize = 15f
            setTextColor(H.textWhite(ctx))
            setTypeface(typeface, Typeface.BOLD)
        })

        // Exercise name if available
        val axisLabel = change.axis?.replaceFirstChar { it.uppercase() }
        if (!axisLabel.isNullOrBlank()) {
            textCol.addView(TextView(ctx).apply {
                text = axisLabel
                textSize = 13f
                setTextColor(H.textMuted(ctx))
            })
        }

        topRow.addView(textCol)
        card.addView(topRow)

        // Progress bar visualization
        val maxVal = maxOf(change.previousValue, change.newValue).toFloat()
        if (maxVal > 0) {
            card.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, H.dp(ctx, 1)
                ).apply { topMargin = H.dp(ctx, 12); bottomMargin = H.dp(ctx, 8) }
                setBackgroundColor(0x1AFFFFFF)
            })

            val barRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Before bar
            barRow.addView(buildComparisonBar(
                if (isArabic) "قبل" else "Before",
                change.previousValue.toFloat(),
                maxVal,
                H.textMuted(ctx)
            ))

            // After bar
            barRow.addView(buildComparisonBar(
                if (isArabic) "بعد" else "After",
                change.newValue.toFloat(),
                maxVal,
                accentColor
            ))

            card.addView(barRow)
        }

        return card
    }

    private fun buildComparisonBar(label: String, value: Float, max: Float, color: Int): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 4) }

            addView(TextView(ctx).apply {
                text = label
                textSize = 12f
                setTextColor(H.textMuted(ctx))
                layoutParams = LinearLayout.LayoutParams(H.dp(ctx, 40), LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            val barContainer = LinearLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, H.dp(ctx, 8), 1f)
                background = GradientDrawable().apply {
                    setColor(0x1AFFFFFF)
                    cornerRadius = H.dp(ctx, 4).toFloat()
                }
            }

            val fraction = if (max > 0) (value / max).coerceIn(0f, 1f) else 0f
            barContainer.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    weight = fraction
                }
                background = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = H.dp(ctx, 4).toFloat()
                }
            })
            barContainer.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    weight = 1f - fraction
                }
            })
            barContainer.weightSum = 1f
            addView(barContainer)

            addView(TextView(ctx).apply {
                text = String.format("%.1f", value)
                textSize = 12f
                setTextColor(color)
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = H.dp(ctx, 8) }
            })
        }
    }

    private fun buildWhyCard(hasIncrease: Boolean, hasDecrease: Boolean): LinearLayout {
        val ctx = requireContext()
        val card = H.glassCard(ctx)

        val report = this.report
        val formScore = report?.overallQuality?.score
            ?: report?.summary?.averageScore
            ?: 0f

        card.addView(H.metricRow(ctx, "\uD83C\uDFAF",
            if (isArabic) "جودة الأداء" else "Form Score",
            "${formScore.toInt()}%",
            null,
            if (hasIncrease) {
                if (isArabic) "أداؤك تجاوز الحد المطلوب" else "Your performance exceeded the threshold"
            } else if (hasDecrease) {
                if (isArabic) "أداؤك أقل من الحد المطلوب" else "Your performance fell below the threshold"
            } else null
        ))

        val rom = report?.performanceMetrics?.formCard?.rom?.value
            ?: report?.summary?.avgROM
            ?: 0f
        if (rom > 0f) {
            card.addView(H.metricRow(ctx, "\uD83D\uDCD0",
                if (isArabic) "نطاق الحركة" else "Range of Motion",
                "${rom.toInt()}%"
            ))
        }

        return card
    }

    private fun buildNextCard(hasIncrease: Boolean, hasDecrease: Boolean): LinearLayout {
        val ctx = requireContext()
        val card = H.glassCard(ctx, borderColor = H.colorBlue(ctx))

        val tipText = when {
            hasDecrease -> getString(R.string.progression_next_deload_tip)
            hasIncrease -> getString(R.string.progression_next_weight_tip)
            else -> getString(R.string.progression_next_weight_tip)
        }

        card.addView(TextView(ctx).apply {
            text = "\uD83D\uDCA1"
            textSize = 28f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        card.addView(TextView(ctx).apply {
            text = tipText
            textSize = 15f
            setTextColor(H.textWhite(ctx))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 8) }
        })

        return card
    }

    private fun showNoChanges() {
        val col = contentCol ?: return
        val ctx = requireContext()
        clearLoading()

        val card = H.glassCard(ctx, borderColor = H.colorBlue(ctx))

        card.addView(TextView(ctx).apply {
            text = "\uD83D\uDCAA"
            textSize = 48f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })

        card.addView(TextView(ctx).apply {
            text = getString(R.string.progression_no_changes_title)
            textSize = 22f
            setTextColor(H.textWhite(ctx))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 12) }
        })

        card.addView(TextView(ctx).apply {
            text = getString(R.string.progression_no_changes_message)
            textSize = 14f
            setTextColor(H.textMuted(ctx))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 8) }
        })

        col.addView(card)

        // Goal card
        val goalCard = H.glassCard(ctx)
        goalCard.addView(H.metricRow(ctx, "\uD83C\uDFAF",
            if (isArabic) "الهدف القادم" else "Next Goal",
            "",
            null,
            getString(R.string.progression_no_changes_goal)
        ))
        col.addView(goalCard)
    }

    // --- Helpers ---

    private fun sectionLabel(text: String): TextView {
        val ctx = requireContext()
        return TextView(ctx).apply {
            this.text = text
            textSize = 18f
            setTextColor(H.textWhite(ctx))
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 24); bottomMargin = H.dp(ctx, 4) }
        }
    }

    private fun getFieldLabel(field: String): String {
        return when (field) {
            "weightKg" -> getString(R.string.progression_field_weight)
            "targetReps" -> getString(R.string.progression_field_reps)
            "sets" -> getString(R.string.progression_field_sets)
            "reassessment" -> getString(R.string.progression_field_reassessment)
            else -> field
        }
    }

    private fun formatValue(field: String, value: Double): String {
        return when (field) {
            "weightKg" -> "${String.format("%.1f", value)}kg"
            "targetReps", "sets" -> "${value.toInt()}"
            else -> String.format("%.1f", value)
        }
    }
}
