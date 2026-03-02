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
import com.trainingvalidator.poc.training.models.MetricCode
import com.trainingvalidator.poc.training.report.*
import com.trainingvalidator.poc.ui.report.ReportUiHelper as H

/**
 * Screen 6 — Control, Fatigue & Load
 *
 * Deep-dive into the Control card plus Fatigue analysis and Load metrics:
 *   • Control Score (big ring)
 *   • Tempo (E-I-C visual)
 *   • Time Under Tension
 *   • Tempo Consistency
 *   • Velocity Loss %
 *   • Fatigue Index analysis
 *   • Load (Weight / Volume / Est 1RM)
 *
 * Adapts for:
 *   - Hold exercises: hides Tempo, TUT, Fatigue
 *   - Bodyweight exercises: hides Load section
 *   - < 3 reps: hides Velocity Loss, Tempo Consistency
 */
class ControlFatigueFragment : Fragment() {

    companion object {
        fun newInstance() = ControlFatigueFragment()
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
        val control = metrics.controlCard
        val config = report.exerciseConfig
        val isHold = config?.isHoldExercise() == true

        // Header
        col.addView(H.sectionTitle(ctx, "🎛️",
            if (isArabic) "التحكم والتعب والحمل" else "Control, Fatigue & Load"
        ))
        col.addView(H.sectionSubtitle(ctx,
            if (isArabic) "تحليل الإيقاع والتحمل والأوزان" else "Tempo, endurance & load analysis"
        ))

        // ── Big score ────────────────────────────────────────────
        val scoreCard = H.glassCard(ctx, H.colorFromScore(ctx, control.getCardScore()))
        scoreCard.gravity = Gravity.CENTER_HORIZONTAL
        scoreCard.addView(H.scoreBadge(ctx, control.getCardScore(), 72).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = H.dp(ctx, 6)
        })
        scoreCard.addView(TextView(ctx).apply {
            val status = MetricStatus.fromPercentage(control.getCardScore())
            text = if (isArabic) status.getLabel().ar else status.getLabel().en
            textSize = 16f; setTextColor(status.getColor()); gravity = Gravity.CENTER
        })
        col.addView(scoreCard)

        // ── Tempo (E-I-C visual) ─────────────────────────────────
        if (!isHold && shouldShow(config, MetricCode.TEMPO)) {
            control.tempo?.let { tempo ->
                col.addView(buildTempoVisual(ctx, tempo))
            }
        }

        // ── TUT ──────────────────────────────────────────────────
        if (!isHold && shouldShow(config, MetricCode.TUT)) {
            control.totalTUT?.let { tut ->
                col.addView(H.metricRow(ctx, "⏳",
                    if (isArabic) "وقت الشد الكلي" else "Time Under Tension",
                    "${tut}s", null,
                    if (isArabic) getTutAdviceAr(tut) else getTutAdviceEn(tut)
                ))
            }
        }

        // ── Tempo Consistency ────────────────────────────────────
        if (!isHold && report.repTimeline.size >= 3) {
            control.tempoConsistency?.let { tc ->
                col.addView(H.metricRow(ctx, "🎵",
                    if (isArabic) "ثبات الإيقاع" else "Tempo Consistency",
                    tc, isArabic
                ))
            }
        }

        // ── Velocity Loss ────────────────────────────────────────
        if (!isHold && report.repTimeline.size >= 3) {
            control.velocityLoss?.let { vl ->
                col.addView(H.metricRow(ctx, "⚡",
                    if (isArabic) "فقدان السرعة" else "Velocity Loss",
                    vl, isArabic
                ))
            }
        }

        // ── Fatigue Index ────────────────────────────────────────
        if (!isHold && shouldShow(config, MetricCode.FATIGUE_INDEX)) {
            col.addView(H.divider(ctx))
            col.addView(buildFatigueSection(ctx, control.fatigueIndex, report))
        }

        // ── Load ─────────────────────────────────────────────────
        metrics.loadMetrics?.let { load ->
            col.addView(H.divider(ctx))
            col.addView(buildLoadSection(ctx, load, config))
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Tempo visual (E-I-C bars)
    // ═══════════════════════════════════════════════════════════════

    private fun buildTempoVisual(ctx: android.content.Context, tempo: TempoDisplay): LinearLayout {
        val card = H.glassCard(ctx)

        // Title row
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply { text = "⏱️"; textSize = 18f })
            addView(TextView(ctx).apply {
                text = if (isArabic) "الإيقاع" else "Tempo"
                textSize = 14f; setTextColor(H.textMuted(ctx))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = H.dp(ctx, 8) }
            })
            addView(TextView(ctx).apply {
                text = tempo.getFormattedTempo()
                textSize = 18f; setTextColor(H.textWhite(ctx))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        })

        // Phase bars
        val phases = listOf(
            Triple(if (isArabic) "إنزال" else "Eccentric", tempo.eccentricMs, H.colorBlue(ctx)),
            Triple(if (isArabic) "ثبات" else "Isometric", tempo.isometricMs, H.colorYellow(ctx)),
            Triple(if (isArabic) "رفع" else "Concentric", tempo.concentricMs, H.colorGreen(ctx))
        )
        val maxMs = phases.maxOf { it.second }.coerceAtLeast(1)

        for ((label, ms, color) in phases) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = H.dp(ctx, 6) }
            }
            // Label
            row.addView(TextView(ctx).apply {
                text = label; textSize = 11f; setTextColor(H.textMuted(ctx))
                layoutParams = LinearLayout.LayoutParams(H.dp(ctx, 60), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            // Bar
            val barContainer = LinearLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, H.dp(ctx, 14), 1f)
                background = GradientDrawable().apply {
                    setColor(0x0DFFFFFF); cornerRadius = H.dp(ctx, 7).toFloat()
                }
                weightSum = maxMs.toFloat()
            }
            barContainer.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    weight = ms.toFloat()
                }
                background = GradientDrawable().apply {
                    setColor(color); cornerRadius = H.dp(ctx, 7).toFloat()
                }
            })
            barContainer.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    weight = (maxMs - ms).toFloat()
                }
            })
            row.addView(barContainer)
            // Duration
            row.addView(TextView(ctx).apply {
                text = "%.1fs".format(ms / 1000.0)
                textSize = 11f; setTextColor(color)
                setPadding(H.dp(ctx, 8), 0, 0, 0)
            })
            card.addView(row)
        }
        return card
    }

    // ═══════════════════════════════════════════════════════════════
    //  Fatigue
    // ═══════════════════════════════════════════════════════════════

    private fun buildFatigueSection(
        ctx: android.content.Context,
        fatigueIndex: Int?,
        report: PostTrainingReport
    ): LinearLayout {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(TextView(ctx).apply {
            text = if (isArabic) "📉 تحليل التعب" else "📉 Fatigue Analysis"
            textSize = 15f; setTextColor(H.textWhite(ctx))
            setPadding(0, 0, 0, H.dp(ctx, 8))
        })

        if (fatigueIndex != null) {
            val totalReps = report.repTimeline.size
            val ratio = if (totalReps > 0) (fatigueIndex.toFloat() / totalReps * 100f) else 0f

            container.addView(H.metricRow(ctx, "📊",
                if (isArabic) "بداية التعب" else "Fatigue Start",
                if (isArabic) "العدة #$fatigueIndex من $totalReps" else "Rep #$fatigueIndex of $totalReps",
                if (ratio >= 70) MetricStatus.GOOD else MetricStatus.NEEDS_WORK,
                if (isArabic) {
                    if (ratio >= 70) "تحمل جيد — التعب ظهر متأخراً" else "تعب مبكر — حاول تقليل الوزن"
                } else {
                    if (ratio >= 70) "Good endurance — late fatigue onset" else "Early fatigue — consider reducing load"
                }
            ))

            // Fatigue progress bar
            container.addView(H.progressRow(ctx,
                if (isArabic) "نسبة العدات قبل التعب" else "Reps before fatigue",
                ratio, if (ratio >= 70) H.colorGreen(ctx) else H.colorOrange(ctx)
            ))
        } else {
            // No fatigue detected
            container.addView(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER
                setPadding(H.dp(ctx, 16), H.dp(ctx, 12), H.dp(ctx, 16), H.dp(ctx, 12))
                background = GradientDrawable().apply {
                    setColor(0x0D4CAF50); cornerRadius = H.dp(ctx, 8).toFloat()
                }
                addView(TextView(ctx).apply {
                    text = if (isArabic) "✅ لم يُكتشف تعب — تحمل ممتاز!"
                    else "✅ No fatigue detected — excellent endurance!"
                    textSize = 14f; setTextColor(H.colorGreen(ctx)); gravity = Gravity.CENTER
                })
            })
        }
        return container
    }

    // ═══════════════════════════════════════════════════════════════
    //  Load
    // ═══════════════════════════════════════════════════════════════

    private fun buildLoadSection(
        ctx: android.content.Context,
        load: LoadMetrics,
        config: ExerciseConfigSnapshot?
    ): LinearLayout {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(TextView(ctx).apply {
            text = if (isArabic) "🏋️ الأوزان والحمل" else "🏋️ Load Metrics"
            textSize = 15f; setTextColor(H.textWhite(ctx))
            setPadding(0, 0, 0, H.dp(ctx, 8))
        })

        // Weight
        if (shouldShow(config, MetricCode.WEIGHT)) {
            container.addView(H.metricRow(ctx, "🏋️",
                if (isArabic) "الوزن" else "Weight",
                load.getFormattedWeight()
            ))
        }

        // Volume
        if (shouldShow(config, MetricCode.VOLUME)) {
            load.getFormattedVolume()?.let { vol ->
                container.addView(H.metricRow(ctx, "📦",
                    if (isArabic) "الحجم الكلي" else "Total Volume",
                    vol
                ))
            }
        }

        // Est 1RM
        if (shouldShow(config, MetricCode.EST_1RM)) {
            load.getFormattedEst1RM()?.let { rm ->
                container.addView(H.metricRow(ctx, "💪",
                    if (isArabic) "القوة القصوى التقديرية" else "Estimated 1RM",
                    rm
                ))
            }
        }

        return container
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun getTutAdviceAr(tut: Int): String = when {
        tut in 40..60 -> "مثالي للتضخم (40-60 ثانية) ✅"
        tut < 20 -> "قصير جداً — أبطئ الحركة"
        tut > 70 -> "طويل — مناسب للتحمل"
        else -> "جيد"
    }

    private fun getTutAdviceEn(tut: Int): String = when {
        tut in 40..60 -> "Ideal for hypertrophy (40-60s) ✅"
        tut < 20 -> "Too short — slow down the movement"
        tut > 70 -> "Long — good for endurance"
        else -> "Good"
    }

    private fun shouldShow(config: ExerciseConfigSnapshot?, metric: MetricCode): Boolean {
        return config?.shouldShowMetric(metric) != false
    }
}
