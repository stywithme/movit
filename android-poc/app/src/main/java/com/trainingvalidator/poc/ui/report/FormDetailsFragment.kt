package com.trainingvalidator.poc.ui.report

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.training.models.MetricCode
import com.trainingvalidator.poc.training.report.*
import com.trainingvalidator.poc.ui.report.ReportUiHelper as H

/**
 * Screen 4 — Form Details
 *
 * Deep-dive into the Form card:
 *   • Form Score (big ring + rating)
 *   • ROM progress bar
 *   • Symmetry (L vs R breakdown for bilateral)
 *   • Form Consistency (with rep-range annotation)
 *   • State breakdown (PERFECT / NORMAL / PAD / WARNING / DANGER counts)
 *
 * Adapts for:
 *   - Non-bilateral exercises: hides Symmetry row
 *   - Hold exercises: shows hold quality instead of rep-based stats
 *   - < 4 reps: hides Form Consistency
 */
class FormDetailsFragment : Fragment() {

    companion object {
        fun newInstance() = FormDetailsFragment()
    }

    private var report: PostTrainingReport? = null
    private var isArabic = false
    private var contentCol: LinearLayout? = null

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

    fun setData(report: PostTrainingReport, isArabic: Boolean) {
        this.report = report
        this.isArabic = isArabic
        if (isAdded) bindData()
    }

    // ═══════════════════════════════════════════════════════════════
    //  Bind
    // ═══════════════════════════════════════════════════════════════

    private fun bindData() {
        val report = this.report ?: return
        val ctx = context ?: return
        val col = contentCol ?: return
        col.removeAllViews()
        val metrics = report.performanceMetrics ?: PerformanceMetricsBuilder.build(report)
        val form = metrics.formCard
        val config = report.exerciseConfig

        // Header
        col.addView(H.sectionTitle(ctx, "🎯", if (isArabic) "تفاصيل الشكل" else "Form Details"))
        col.addView(H.sectionSubtitle(ctx,
            if (isArabic) "تقييم جودة أداء الحركة" else "Movement quality assessment"
        ))

        // ── Big score + rating ────────────────────────────────────
        val scoreCard = H.glassCard(ctx, H.colorFromScore(ctx, form.getCardScore()))
        scoreCard.gravity = Gravity.CENTER_HORIZONTAL

        scoreCard.addView(H.scoreBadge(ctx, form.getCardScore(), 72).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = H.dp(ctx, 6)
        })
        scoreCard.addView(TextView(ctx).apply {
            val status = MetricStatus.fromPercentage(form.getCardScore())
            text = if (isArabic) status.getLabel().ar else status.getLabel().en
            textSize = 16f
            setTextColor(status.getColor())
            gravity = Gravity.CENTER
        })
        form.overallScore.advice?.let { advice ->
            scoreCard.addView(TextView(ctx).apply {
                text = if (isArabic) advice.ar else advice.en
                textSize = 13f
                setTextColor(H.textMuted(requireContext()))
                gravity = Gravity.CENTER
                setPadding(0, H.dp(ctx, 4), 0, 0)
            })
        }
        col.addView(scoreCard)

        // ── ROM ──────────────────────────────────────────────────
        if (shouldShow(config, MetricCode.ROM)) {
            form.rom?.let { rom ->
                col.addView(H.metricRow(ctx, "📐",
                    if (isArabic) "المدى الحركي" else "Range of Motion",
                    rom, isArabic
                ))
                col.addView(H.progressRow(ctx,
                    if (isArabic) "المدى" else "ROM",
                    rom.value, H.colorFromScore(ctx, rom.value)
                ))
            }
        }

        // ── Symmetry ─────────────────────────────────────────────
        if (shouldShow(config, MetricCode.SYMMETRY)) {
            form.symmetry?.let { sym ->
                col.addView(H.metricRow(ctx, "⚖️",
                    if (isArabic) "التوازن" else "Symmetry",
                    sym, isArabic
                ))
                if (config?.isBilateral == true && config.hasAnySideJoints != true) {
                    addSymmetryVisual(ctx, col, report)
                }
            }
            if (config?.hasAnySideJoints == true) {
                col.addView(TextView(ctx).apply {
                    text = if (isArabic) {
                        "استُخدم وضع تتبّع أي جانب — يُحسب التوازن فقط عندما يظهر الجانبان معاً في الكاميرا."
                    } else {
                        "Any-Side tracking was used — symmetry is measured only when both sides were visible."
                    }
                    textSize = 12f
                    setTextColor(H.textMuted(requireContext()))
                    setPadding(H.dp(ctx, 8), H.dp(ctx, 4), H.dp(ctx, 8), 0)
                })
            }
        }

        // ── Form Consistency ─────────────────────────────────────
        if (shouldShow(config, MetricCode.FORM_CONSISTENCY) && report.repTimeline.size >= 4) {
            form.formConsistency?.let { cons ->
                col.addView(H.metricRow(ctx, "📊",
                    if (isArabic) "ثبات الشكل" else "Form Consistency",
                    cons, isArabic
                ))
            }
        }

        // ── State breakdown ──────────────────────────────────────
        col.addView(H.divider(ctx))
        col.addView(buildStateBreakdown(ctx, report.summary.stateBreakdown))
    }

    // ═══════════════════════════════════════════════════════════════
    //  Symmetry visual (L bar vs R bar)
    // ═══════════════════════════════════════════════════════════════

    private fun addSymmetryVisual(ctx: android.content.Context, col: LinearLayout, report: PostTrainingReport) {
        val timeline = report.repTimeline
        if (timeline.size < 2) return

        val leftScores = mutableListOf<Float>()
        val rightScores = mutableListOf<Float>()
        timeline.forEachIndexed { index, rep ->
            if (index % 2 == 0) rightScores.add(rep.score) else leftScores.add(rep.score)
        }
        val avgL = if (leftScores.isNotEmpty()) leftScores.average().toFloat() else 0f
        val avgR = if (rightScores.isNotEmpty()) rightScores.average().toFloat() else 0f

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 8) }
            gravity = Gravity.CENTER_VERTICAL
            setPadding(H.dp(ctx, 12), H.dp(ctx, 8), H.dp(ctx, 12), H.dp(ctx, 8))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x0DFFFFFF); cornerRadius = H.dp(ctx, 8).toFloat()
            }
        }
        row.addView(buildSideLabel(ctx, if (isArabic) "يسار" else "Left", avgL))
        row.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(H.dp(ctx, 1), H.dp(ctx, 32)).apply {
                marginStart = H.dp(ctx, 12); marginEnd = H.dp(ctx, 12)
            }
            setBackgroundColor(0x33FFFFFF)
        })
        row.addView(buildSideLabel(ctx, if (isArabic) "يمين" else "Right", avgR))
        col.addView(row)
    }

    private fun buildSideLabel(ctx: android.content.Context, side: String, avg: Float): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(ctx).apply { text = side; textSize = 12f; setTextColor(H.textMuted(requireContext())); gravity = Gravity.CENTER })
            addView(TextView(ctx).apply {
                text = "${avg.toInt()}%"; textSize = 20f
                setTextColor(H.colorFromScore(ctx, avg))
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  State breakdown
    // ═══════════════════════════════════════════════════════════════

    private fun buildStateBreakdown(ctx: android.content.Context, breakdown: StateBreakdown): LinearLayout {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(TextView(ctx).apply {
            text = if (isArabic) "توزيع الحالات" else "State Distribution"
            textSize = 14f; setTextColor(H.textWhite(requireContext()))
            setPadding(0, 0, 0, H.dp(ctx, 8))
        })

        val states = listOf(
            Triple("🟢", if (isArabic) "مثالي" else "Perfect", breakdown.perfectCount),
            Triple("🟢", if (isArabic) "جيد" else "Normal", breakdown.normalCount),
            Triple("🟡", if (isArabic) "مقبول" else "Acceptable", breakdown.padCount),
            Triple("🟠", if (isArabic) "تحذير" else "Warning", breakdown.warningCount),
            Triple("🔴", if (isArabic) "خطر" else "Danger", breakdown.dangerCount)
        )
        val total = breakdown.total.toFloat().coerceAtLeast(1f)
        for ((icon, label, count) in states) {
            if (count > 0) {
                container.addView(H.progressRow(ctx, "$icon $label ($count)",
                    (count / total) * 100f,
                    when (icon) {
                        "🟢" -> H.colorGreen(requireContext()); "🟡" -> H.colorYellow(requireContext()); "🟠" -> H.colorOrange(requireContext()); else -> H.colorRed(requireContext())
                    }
                ))
            }
        }
        return container
    }

    private fun shouldShow(config: ExerciseConfigSnapshot?, metric: MetricCode): Boolean {
        return config?.shouldShowMetric(metric) != false
    }
}
