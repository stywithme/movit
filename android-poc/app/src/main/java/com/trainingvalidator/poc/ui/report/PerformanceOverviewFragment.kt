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
import com.trainingvalidator.poc.training.report.*
import com.trainingvalidator.poc.ui.report.ReportUiHelper as H
import com.trainingvalidator.poc.ui.report.components.RepsJourneyChart

/**
 * Screen 2 — Performance Overview
 *
 * Layout:
 *   ┌──────────────────────┐
 *   │  📊 Reps Journey     │
 *   │  [bar chart]         │
 *   ├──────────────────────┤
 *   │  [Form] [Safety] [Control]   ← 3 score cards
 *   └──────────────────────┘
 *
 * For Hold exercises: chart is replaced with a timeline/progress indicator.
 */
class PerformanceOverviewFragment : Fragment() {

    companion object {
        fun newInstance() = PerformanceOverviewFragment()
    }

    private var report: PostTrainingReport? = null
    private var isArabic = false

    // View refs
    private var chart: RepsJourneyChart? = null
    private var tvTitle: TextView? = null
    private var tvSubtitle: TextView? = null
    private var cardsRow: LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return createLayout()
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
    //  Layout
    // ═══════════════════════════════════════════════════════════════

    private fun createLayout(): View {
        val ctx = requireContext()
        val scroll = H.pageRoot(ctx)
        val col = H.pageColumn(ctx)

        tvTitle = H.sectionTitle(ctx, "📊", "Reps Journey")
        col.addView(tvTitle)
        tvSubtitle = H.sectionSubtitle(ctx, "Your performance across all reps")
        col.addView(tvSubtitle)

        // Chart
        chart = RepsJourneyChart(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                H.dp(ctx, 180)
            )
        }
        col.addView(chart)

        // 3 score cards
        cardsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 24) }
            weightSum = 3f
        }
        col.addView(cardsRow)

        scroll.addView(col)
        return scroll
    }

    // ═══════════════════════════════════════════════════════════════
    //  Bind data
    // ═══════════════════════════════════════════════════════════════

    private fun bindData() {
        val report = this.report ?: return
        val ctx = context ?: return
        val metrics = report.performanceMetrics ?: PerformanceMetricsBuilder.build(report)

        tvTitle?.text = if (isArabic) "📊 رحلة العدات" else "📊 Reps Journey"
        tvSubtitle?.text = if (isArabic) "أداؤك عبر كل العدات" else "Your performance across all reps"

        // Chart data
        chart?.setData(report.repTimeline, metrics.controlCard.fatigueIndex)

        // 3 cards
        cardsRow?.removeAllViews()
        cardsRow?.addView(
            buildScoreCard(
                ctx,
                icon = "🎯",
                title = if (isArabic) "الشكل" else "Form",
                score = metrics.formCard.getCardScore(),
                subItems = buildFormSubItems(metrics.formCard)
            )
        )
        cardsRow?.addView(
            buildScoreCard(
                ctx,
                icon = "🛡️",
                title = if (isArabic) "الأمان" else "Safety",
                score = metrics.safetyCard.getCardScore(),
                subItems = buildSafetySubItems(metrics.safetyCard)
            )
        )
        cardsRow?.addView(
            buildScoreCard(
                ctx,
                icon = "🎛️",
                title = if (isArabic) "التحكم" else "Control",
                score = metrics.controlCard.getCardScore(),
                subItems = buildControlSubItems(metrics.controlCard)
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  Card builder
    // ═══════════════════════════════════════════════════════════════

    private fun buildScoreCard(
        ctx: android.content.Context,
        icon: String,
        title: String,
        score: Float,
        subItems: List<Pair<String, String>>
    ): LinearLayout {
        val color = H.colorFromScore(score)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = H.dp(ctx, 6)
                marginStart = H.dp(ctx, 6)
            }
            background = GradientDrawable().apply {
                setColor(H.CARD_BG)
                setStroke(1, color)
                cornerRadius = H.dp(ctx, 14).toFloat()
            }
            setPadding(H.dp(ctx, 10), H.dp(ctx, 14), H.dp(ctx, 10), H.dp(ctx, 14))

            // Icon + title
            addView(TextView(ctx).apply {
                text = "$icon $title"
                textSize = 13f
                setTextColor(H.TEXT_WHITE)
                gravity = Gravity.CENTER
            })

            // Score ring
            addView(H.scoreBadge(ctx, score, 52).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = H.dp(ctx, 8)
            })

            // Sub-items
            for ((label, value) in subItems) {
                addView(TextView(ctx).apply {
                    text = "$label: $value"
                    textSize = 10f
                    setTextColor(H.TEXT_MUTED)
                    gravity = Gravity.CENTER
                    setPadding(0, H.dp(ctx, 3), 0, 0)
                })
            }
        }
    }

    private fun buildFormSubItems(form: FormMetrics): List<Pair<String, String>> {
        val items = mutableListOf<Pair<String, String>>()
        form.rom?.let { items.add((if (isArabic) "المدى" else "ROM") to it.displayValue) }
        form.symmetry?.let { items.add((if (isArabic) "التوازن" else "Sym") to it.displayValue) }
        form.formConsistency?.let { items.add((if (isArabic) "الثبات" else "Cons") to it.displayValue) }
        return items
    }

    private fun buildSafetySubItems(safety: SafetyMetrics): List<Pair<String, String>> {
        val items = mutableListOf<Pair<String, String>>()
        safety.alignmentAccuracy?.let { items.add((if (isArabic) "المحاذاة" else "Align") to it.displayValue) }
        safety.stability?.let { items.add((if (isArabic) "الثبات" else "Stab") to it.displayValue) }
        if (safety.dangerCount > 0) {
            items.add((if (isArabic) "تحذير" else "Danger") to "${safety.dangerCount}")
        }
        return items
    }

    private fun buildControlSubItems(control: ControlMetrics): List<Pair<String, String>> {
        val items = mutableListOf<Pair<String, String>>()
        control.tempo?.let { items.add((if (isArabic) "الإيقاع" else "Tempo") to it.getFormattedTempo()) }
        control.totalTUT?.let { items.add("TUT" to "${it}s") }
        control.fatigueIndex?.let { items.add((if (isArabic) "التعب" else "Fatigue") to "#$it") }
        return items
    }
}
