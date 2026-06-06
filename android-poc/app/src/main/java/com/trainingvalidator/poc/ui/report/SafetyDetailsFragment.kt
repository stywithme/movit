package com.trainingvalidator.poc.ui.report

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.training.models.JointState
import com.trainingvalidator.poc.training.models.MetricCode
import com.trainingvalidator.poc.training.report.*
import com.trainingvalidator.poc.ui.report.ReportUiHelper as H

/**
 * Screen 5 — Safety Details
 *
 * Deep-dive into the Safety card:
 *   • Safety Score (big ring)
 *   • Alignment Accuracy (PositionCheck-based)
 *   • Trunk Stability (spine variance)
 *   • Danger Alerts list (critical issues)
 *   • Position Check summary (ERROR / WARNING / TIP counts)
 *
 * Adapts for:
 *   - No position checks: hides Alignment row
 *   - No captured danger alerts: shows summary fallback instead of false safe state
 *   - Hold exercises: emphasises stability over alignment
 */
class SafetyDetailsFragment : Fragment() {

    companion object {
        fun newInstance() = SafetyDetailsFragment()
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
        val safety = metrics.safetyCard
        val config = report.exerciseConfig

        // Header
        col.addView(H.sectionTitle(ctx, "🛡️", if (isArabic) "تفاصيل الأمان" else "Safety Details"))
        col.addView(H.sectionSubtitle(ctx,
            if (isArabic) "تقييم سلامة المفاصل ووضعية الجسم" else "Joint safety & body positioning assessment"
        ))

        // ── Big score ────────────────────────────────────────────
        val scoreCard = H.glassCard(ctx, H.colorFromScore(ctx, safety.getCardScore()))
        scoreCard.gravity = Gravity.CENTER_HORIZONTAL
        scoreCard.addView(H.scoreBadge(ctx, safety.getCardScore(), 72).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = H.dp(ctx, 6)
        })
        scoreCard.addView(TextView(ctx).apply {
            val status = MetricStatus.fromPercentage(safety.getCardScore())
            text = if (isArabic) status.getLabel().ar else status.getLabel().en
            textSize = 16f; setTextColor(status.getColor()); gravity = Gravity.CENTER
        })
        col.addView(scoreCard)

        // ── Alignment Accuracy ───────────────────────────────────
        if (shouldShow(config, MetricCode.ALIGNMENT)) {
            safety.alignmentAccuracy?.let { align ->
                col.addView(H.metricRow(ctx, "📏",
                    if (isArabic) "دقة المحاذاة" else "Alignment Accuracy",
                    align, isArabic
                ))
            }
        }

        // ── Trunk Stability ──────────────────────────────────────
        if (shouldShow(config, MetricCode.STABILITY)) {
            safety.stability?.let { stab ->
                col.addView(H.metricRow(ctx, "🏋️",
                    if (isArabic) "ثبات الجذع" else "Trunk Stability",
                    stab, isArabic
                ))
            }
        }

        // ── Position Check Summary ───────────────────────────────
        val summary = report.summary
        val hasPositionData = summary.positionErrorReps > 0 ||
                summary.positionWarningReps > 0 ||
                summary.positionTipReps > 0

        if (hasPositionData) {
            col.addView(H.divider(ctx))
            col.addView(buildPositionCheckSummary(ctx, summary))
        }

        // ── Danger Alerts ────────────────────────────────────────
        val dangerCount = summary.invalidatedReps.coerceAtLeast(report.dangerAlerts.size)
        if (report.dangerAlerts.isNotEmpty()) {
            col.addView(H.divider(ctx))
            col.addView(buildDangerSection(ctx, report.dangerAlerts))
        } else if (dangerCount > 0) {
            col.addView(H.divider(ctx))
            col.addView(buildDangerCountOnlySection(ctx, dangerCount))
        } else {
            col.addView(H.divider(ctx))
            col.addView(buildSafeMessage(ctx))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Position check summary
    // ═══════════════════════════════════════════════════════════════

    private fun buildPositionCheckSummary(ctx: android.content.Context, summary: PerformanceSummary): LinearLayout {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(TextView(ctx).apply {
            text = if (isArabic) "ملخص فحص الوضعية" else "Position Check Summary"
            textSize = 14f; setTextColor(H.textWhite(requireContext()))
            setPadding(0, 0, 0, H.dp(ctx, 8))
        })

        val checks = listOf(
            Triple("🔴", if (isArabic) "أخطاء وضعية" else "Position Errors", summary.positionErrorReps),
            Triple("🟠", if (isArabic) "تحذيرات وضعية" else "Position Warnings", summary.positionWarningReps),
            Triple("💡", if (isArabic) "نصائح وضعية" else "Position Tips", summary.positionTipReps)
        )
        val totalReps = summary.totalReps.coerceAtLeast(1).toFloat()
        for ((icon, label, count) in checks) {
            if (count > 0) {
                container.addView(buildCheckRow(ctx, icon, label, count, totalReps))
            }
        }
        return container
    }

    private fun buildCheckRow(
        ctx: android.content.Context,
        icon: String,
        label: String,
        count: Int,
        totalReps: Float
    ): LinearLayout {
        val pct = (count / totalReps) * 100f
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 6) }
            setPadding(H.dp(ctx, 12), H.dp(ctx, 8), H.dp(ctx, 12), H.dp(ctx, 8))
            background = GradientDrawable().apply {
                setColor(0x0DFFFFFF); cornerRadius = H.dp(ctx, 8).toFloat()
            }

            addView(TextView(ctx).apply { text = icon; textSize = 18f })
            addView(TextView(ctx).apply {
                text = label; textSize = 13f; setTextColor(H.textMuted(requireContext()))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = H.dp(ctx, 8) }
            })
            addView(TextView(ctx).apply {
                text = if (isArabic) "$count عدات (${pct.toInt()}%)" else "$count reps (${pct.toInt()}%)"
                textSize = 13f; setTextColor(H.textWhite(requireContext()))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Danger alerts
    // ═══════════════════════════════════════════════════════════════

    private fun buildDangerSection(ctx: android.content.Context, alerts: List<DangerAlert>): LinearLayout {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(TextView(ctx).apply {
            text = if (isArabic) "🚨 تنبيهات أمان" else "🚨 Safety Alerts"
            textSize = 16f; setTextColor(H.colorRed(requireContext()))
            setPadding(0, 0, 0, H.dp(ctx, 8))
        })

        for (alert in alerts.take(5)) { // Show max 5
            container.addView(buildDangerCard(ctx, alert))
        }
        return container
    }

    private fun buildDangerCountOnlySection(ctx: android.content.Context, dangerCount: Int): LinearLayout {
        val card = H.glassCard(ctx, H.colorRed(requireContext()))
        card.addView(TextView(ctx).apply {
            text = if (isArabic) "! Safety alerts detected" else "! Safety alerts detected"
            textSize = 16f
            setTextColor(H.colorRed(requireContext()))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(ctx).apply {
            text = if (isArabic) "$dangerCount reps reached an unsafe position" else "$dangerCount reps reached an unsafe position"
            textSize = 13f
            setTextColor(H.textWhite(requireContext()))
            setPadding(0, H.dp(ctx, 6), 0, 0)
        })
        card.addView(TextView(ctx).apply {
            text = if (isArabic) "Detailed frame capture is unavailable for this alert." else "Detailed frame capture is unavailable for this alert."
            textSize = 12f
            setTextColor(H.textMuted(requireContext()))
            setPadding(0, H.dp(ctx, 4), 0, 0)
        })
        return card
    }
    private fun buildDangerCard(ctx: android.content.Context, alert: DangerAlert): LinearLayout {
        val card = H.glassCard(ctx, H.colorRed(requireContext()))

        // Joint + Rep
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply {
                text = "⚠️"
                textSize = 20f
            })
            addView(TextView(ctx).apply {
                text = if (isArabic) alert.jointName.ar else alert.jointName.en
                textSize = 15f; setTextColor(H.textWhite(requireContext()))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = H.dp(ctx, 8) }
            })
            addView(TextView(ctx).apply {
                text = if (isArabic) "العدة #${alert.repNumber}" else "Rep #${alert.repNumber}"
                textSize = 12f; setTextColor(H.textMuted(requireContext()))
            })
        })

        // Message
        card.addView(TextView(ctx).apply {
            text = if (isArabic) alert.dangerMessage.ar else alert.dangerMessage.en
            textSize = 13f; setTextColor(H.textWhite(requireContext()))
            setPadding(0, H.dp(ctx, 6), 0, 0)
        })

        // Angle info
        card.addView(TextView(ctx).apply {
            text = if (isArabic) {
                "الزاوية: ${alert.getFormattedAngle()} | المدى الآمن: ${alert.getSafeRangeText()}"
            } else {
                "Angle: ${alert.getFormattedAngle()} | Safe: ${alert.getSafeRangeText()}"
            }
            textSize = 11f; setTextColor(H.textMuted(requireContext()))
            setPadding(0, H.dp(ctx, 4), 0, 0)
        })

        // Solution
        card.addView(TextView(ctx).apply {
            text = "💡 ${if (isArabic) alert.solutionTip.ar else alert.solutionTip.en}"
            textSize = 12f; setTextColor(H.colorGreen(requireContext()))
            setPadding(0, H.dp(ctx, 6), 0, 0)
        })

        return card
    }

    // ═══════════════════════════════════════════════════════════════
    //  Safe message
    // ═══════════════════════════════════════════════════════════════

    private fun buildSafeMessage(ctx: android.content.Context): LinearLayout {
        val card = H.glassCard(ctx, H.colorGreen(requireContext()))
        card.gravity = Gravity.CENTER
        card.addView(TextView(ctx).apply {
            text = "✅"
            textSize = 36f; gravity = Gravity.CENTER
        })
        card.addView(TextView(ctx).apply {
            text = if (isArabic) "تمرين آمن!" else "Safe Workout!"
            textSize = 18f; setTextColor(H.colorGreen(requireContext())); gravity = Gravity.CENTER
            setPadding(0, H.dp(ctx, 6), 0, 0)
        })
        card.addView(TextView(ctx).apply {
            text = if (isArabic) "لم يُلاحظ أي وضع خطر خلال التمرين"
            else "No dangerous positions detected during the exercise"
            textSize = 13f; setTextColor(H.textMuted(requireContext())); gravity = Gravity.CENTER
        })
        return card
    }

    private fun shouldShow(config: ExerciseConfigSnapshot?, metric: MetricCode): Boolean {
        return config?.shouldShowMetric(metric) != false
    }
}
