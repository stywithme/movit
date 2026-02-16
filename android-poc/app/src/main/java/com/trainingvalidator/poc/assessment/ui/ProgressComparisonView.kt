package com.trainingvalidator.poc.assessment.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.trainingvalidator.poc.assessment.engine.ReferenceNormsProvider
import com.trainingvalidator.poc.assessment.models.BodyScanResult
import com.trainingvalidator.poc.assessment.models.DomainScores
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * ProgressComparisonView - Shows comparison between current and previous assessments.
 * 
 * Only shows "real improvement" when the change exceeds MDC.
 * Changes below MDC are shown as "stable" (not falsely presented as progress).
 */
class ProgressComparisonView(context: Context) : LinearLayout(context) {

    private val language: String get() = "en"

    init {
        orientation = VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(16))
        setBackgroundColor(Color.parseColor("#1E1E1E"))
    }

    /**
     * Bind comparison data between current and previous assessment.
     */
    fun bind(current: BodyScanResult, previous: BodyScanResult?) {
        removeAllViews()

        if (previous == null) {
            addView(createNoHistoryView())
            return
        }

        // Title
        addView(TextView(context).apply {
            text = if (language == "ar") "مقارنة مع التقييم السابق" else "Comparison with Previous Assessment"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        })

        // Body Score comparison
        addView(createComparisonRow(
            label = "Body Score",
            currentValue = current.bodyScore,
            previousValue = previous.bodyScore,
            mdcThreshold = 5f
        ))

        // Domain scores
        addDomainComparison("Mobility", "المرونة",
            current.domainScores.mobility, previous.domainScores.mobility, 6f)
        addDomainComparison("Control", "التحكم",
            current.domainScores.control, previous.domainScores.control, 5f)

        val currentSym = current.domainScores.symmetry
        val previousSym = previous.domainScores.symmetry
        if (currentSym != null && previousSym != null) {
            addDomainComparison("Symmetry", "التماثل", currentSym, previousSym, 5f)
        }

        addDomainComparison("Safety", "السلامة",
            current.domainScores.safety, previous.domainScores.safety, 4f)

        // Region-level comparison
        addView(TextView(context).apply {
            text = if (language == "ar") "تغيّر المناطق" else "Region Changes"
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(8))
        })

        val currentRegions = current.regions.associateBy { "${it.region}_${it.side}" }
        val previousRegions = previous.regions.associateBy { "${it.region}_${it.side}" }

        for ((key, currentRegion) in currentRegions) {
            val previousRegion = previousRegions[key] ?: continue
            val mdc = ReferenceNormsProvider.getErrorBand(
                when (currentRegion.region) {
                    com.trainingvalidator.poc.assessment.models.BodyRegion.SHOULDER -> "left_shoulder"
                    com.trainingvalidator.poc.assessment.models.BodyRegion.HIP -> "left_hip"
                    com.trainingvalidator.poc.assessment.models.BodyRegion.KNEE -> "left_knee"
                    com.trainingvalidator.poc.assessment.models.BodyRegion.ANKLE -> "left_ankle"
                    else -> "spine"
                }
            ) * 1.5f

            val label = "${currentRegion.region.getLabel(language)}${
                if (currentRegion.side != com.trainingvalidator.poc.assessment.models.RegionSide.CENTER)
                    " (${currentRegion.side.name.lowercase()})" else ""
            }"

            addView(createComparisonRow(
                label = label,
                currentValue = currentRegion.regionalScore,
                previousValue = previousRegion.regionalScore,
                mdcThreshold = mdc
            ))
        }

        // Summary message
        val bodyDelta = current.bodyScore - previous.bodyScore
        val isRealImprovement = bodyDelta > 5f
        addView(createSummaryMessage(bodyDelta, isRealImprovement))
    }

    private fun addDomainComparison(
        labelEn: String, labelAr: String,
        current: Float, previous: Float,
        mdc: Float
    ) {
        addView(createComparisonRow(
            label = if (language == "ar") labelAr else labelEn,
            currentValue = current,
            previousValue = previous,
            mdcThreshold = mdc
        ))
    }

    private fun createComparisonRow(
        label: String,
        currentValue: Float,
        previousValue: Float,
        mdcThreshold: Float
    ): LinearLayout {
        val delta = currentValue - previousValue
        val isReal = kotlin.math.abs(delta) >= mdcThreshold

        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
            gravity = Gravity.CENTER_VERTICAL

            // Label
            addView(TextView(context).apply {
                text = label
                setTextColor(Color.parseColor("#E0E0E0"))
                textSize = 13f
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            })

            // Previous → Current
            addView(TextView(context).apply {
                text = "${previousValue.toInt()}%"
                setTextColor(Color.parseColor("#757575"))
                textSize = 13f
            })

            addView(TextView(context).apply {
                text = " → "
                setTextColor(Color.parseColor("#757575"))
                textSize = 13f
            })

            addView(TextView(context).apply {
                text = "${currentValue.toInt()}%"
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
            })

            // Delta indicator
            addView(TextView(context).apply {
                val (text, color) = if (!isReal) {
                    "≈" to Color.parseColor("#757575")
                } else if (delta > 0) {
                    "+${delta.toInt()}" to Color.parseColor("#4CAF50")
                } else {
                    "${delta.toInt()}" to Color.parseColor("#FF5252")
                }
                this.text = " $text"
                setTextColor(color)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(8), 0, 0, 0)
            })
        }
    }

    private fun createSummaryMessage(delta: Float, isReal: Boolean): LinearLayout {
        val bgColor: Int
        val message: String
        val icon: String

        when {
            !isReal -> {
                bgColor = Color.parseColor("#263238")
                icon = "📊"
                message = if (language == "ar")
                    "الفرق ضمن نطاق الخطأ — حافظ على المستوى"
                else
                    "Change is within measurement error — maintain your level"
            }
            delta > 10 -> {
                bgColor = Color.parseColor("#1B5E20")
                icon = "🎉"
                message = if (language == "ar")
                    "تحسّن حقيقي ملحوظ! استمر في العمل الجيد"
                else
                    "Real noticeable improvement! Keep up the great work"
            }
            delta > 0 -> {
                bgColor = Color.parseColor("#33691E")
                icon = "📈"
                message = if (language == "ar")
                    "تحسّن طفيف — واصل التدريب"
                else
                    "Slight improvement — keep training"
            }
            else -> {
                bgColor = Color.parseColor("#3E2723")
                icon = "📉"
                message = if (language == "ar")
                    "بعض المناطق تراجعت — راجع التوصيات"
                else
                    "Some areas regressed — review recommendations"
            }
        }

        return LinearLayout(context).apply {
            orientation = VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(16)
            layoutParams = lp

            addView(TextView(context).apply {
                text = "$icon $message"
                setTextColor(Color.WHITE)
                textSize = 14f
            })
        }
    }

    private fun createNoHistoryView(): TextView {
        return TextView(context).apply {
            text = if (language == "ar")
                "هذا أول تقييم لك — سنقارن مع نتائجك القادمة"
            else
                "This is your first assessment — we'll compare with your next results"
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
