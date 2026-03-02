package com.trainingvalidator.poc.ui.report

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.training.report.*
import com.trainingvalidator.poc.ui.report.ReportUiHelper as H
import com.trainingvalidator.poc.ui.report.components.ArcGaugeView
import com.trainingvalidator.poc.ui.report.components.RepsJourneyChart

/**
 * Screen 2 — Performance Overview
 *
 * Layout (V2 — redesigned):
 *   ┌──────────────────────────────┐
 *   │  Title + Subtitle            │
 *   │  QuickInsight message        │
 *   │  [Reps Journey chart]        │
 *   │                              │
 *   │  ┌─[ARC]── Form ──────────┐  │
 *   │  │         ROM · Sym · Con │  │
 *   │  └────────────────────────┘  │
 *   │  ┌─[ARC]── Safety ────────┐  │
 *   │  │         Align · Stab   │  │
 *   │  └────────────────────────┘  │
 *   │  ┌─[ARC]── Control ───────┐  │
 *   │  │         Tempo · TUT    │  │
 *   │  └────────────────────────┘  │
 *   └──────────────────────────────┘
 *
 * Each score card is full-width with an ArcGaugeView on the left and
 * sub-metrics displayed as compact chips on the right.
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
    private var insightCard: LinearLayout? = null
    private var cardsContainer: LinearLayout? = null

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

        // Title
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

        // Score cards container
        cardsContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 20) }
        }
        col.addView(cardsContainer)

        // QuickInsight at the bottom
        insightCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 20) }
            gravity = Gravity.CENTER_VERTICAL
            setPadding(H.dp(ctx, 14), H.dp(ctx, 12), H.dp(ctx, 14), H.dp(ctx, 12))
            background = GradientDrawable().apply {
                setColor(0x0DFFFFFF)
                cornerRadius = H.dp(ctx, 12).toFloat()
            }
            isVisible = false
        }
        col.addView(insightCard)

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

        // Title
        tvTitle?.text = if (isArabic) "📊 رحلة العدات" else "📊 Reps Journey"
        tvSubtitle?.text = if (isArabic) "أداؤك عبر كل العدات" else "Your performance across all reps"

        // QuickInsight
        bindInsight(report)

        // Chart data
        chart?.setData(report.repTimeline, metrics.controlCard.fatigueIndex)

        // Score cards
        cardsContainer?.removeAllViews()

        cardsContainer?.addView(
            buildScoreCard(
                ctx,
                icon = "🎯",
                title = if (isArabic) "الشكل" else "Form",
                score = metrics.formCard.getCardScore(),
                accentColor = H.colorFromScore(ctx, metrics.formCard.getCardScore()),
                subItems = buildFormChips(ctx, metrics.formCard)
            )
        )

        cardsContainer?.addView(
            buildScoreCard(
                ctx,
                icon = "🛡️",
                title = if (isArabic) "الأمان" else "Safety",
                score = metrics.safetyCard.getCardScore(),
                accentColor = H.colorFromScore(ctx, metrics.safetyCard.getCardScore()),
                subItems = buildSafetyChips(ctx, metrics.safetyCard)
            )
        )

        cardsContainer?.addView(
            buildScoreCard(
                ctx,
                icon = "🎛️",
                title = if (isArabic) "التحكم" else "Control",
                score = metrics.controlCard.getCardScore(),
                accentColor = H.colorFromScore(ctx, metrics.controlCard.getCardScore()),
                subItems = buildControlChips(ctx, metrics.controlCard)
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  QuickInsight
    // ═══════════════════════════════════════════════════════════════

    private fun bindInsight(report: PostTrainingReport) {
        val ctx = context ?: return
        val card = insightCard ?: return
        card.removeAllViews()

        val insight = report.quickInsight ?: QuickInsightGenerator.generate(report)
        val title = if (isArabic) insight.title.ar else insight.title.en
        val subtitle = if (isArabic) insight.subtitle.ar else insight.subtitle.en

        val color = when (insight.type) {
            InsightType.CELEBRATION -> H.colorGreen(requireContext())
            InsightType.DANGER_WARNING -> H.colorRed(requireContext())
            InsightType.FOCUS_POINT -> H.colorOrange(requireContext())
        }

        // Icon
        card.addView(TextView(ctx).apply {
            text = insight.icon; textSize = 24f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = H.dp(ctx, 10) }
        })

        // Text column
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(TextView(ctx).apply {
            text = title; textSize = 14f; setTextColor(color)
            setTypeface(typeface, Typeface.BOLD)
        })
        if (subtitle.isNotBlank()) {
            textCol.addView(TextView(ctx).apply {
                text = subtitle; textSize = 12f; setTextColor(H.textMuted(requireContext()))
                maxLines = 2
                setPadding(0, H.dp(ctx, 2), 0, 0)
            })
        }
        card.addView(textCol)
        card.isVisible = true
    }

    // ═══════════════════════════════════════════════════════════════
    //  Score card builder (full-width, arc gauge + sub-metrics)
    // ═══════════════════════════════════════════════════════════════

    private fun buildScoreCard(
        ctx: android.content.Context,
        icon: String,
        title: String,
        score: Float,
        accentColor: Int,
        subItems: List<View>
    ): LinearLayout {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 10) }
            background = GradientDrawable().apply {
                setColor(H.cardBg(requireContext()))
                setStroke(1, 0x33FFFFFF)
                cornerRadius = H.dp(ctx, 16).toFloat()
            }
            setPadding(H.dp(ctx, 12), H.dp(ctx, 14), H.dp(ctx, 16), H.dp(ctx, 14))
        }

        // Left accent bar (4dp wide, full card height)
        card.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                H.dp(ctx, 4), LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { marginEnd = H.dp(ctx, 10) }
            background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = H.dp(ctx, 2).toFloat()
            }
        })

        // Arc gauge
        val gauge = ArcGaugeView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                H.dp(ctx, 68), H.dp(ctx, 68)
            ).apply { marginEnd = H.dp(ctx, 14) }
            setScore(score)
        }
        card.addView(gauge)

        // Right side: title + chip rows
        val rightCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Title row (icon + name)
        rightCol.addView(TextView(ctx).apply {
            text = "$icon  $title"
            textSize = 16f
            setTextColor(H.textWhite(requireContext()))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, H.dp(ctx, 6))
        })

        // Sub-metric chips in a flow layout (wrapped horizontal)
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }
        // Use a flex-like wrap: put chips in rows of 2-3
        val chipContainer = buildChipFlowLayout(ctx, subItems)
        rightCol.addView(chipContainer)

        card.addView(rightCol)
        return card
    }

    private fun buildChipFlowLayout(ctx: android.content.Context, chips: List<View>): LinearLayout {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Group chips into rows of max 2
        val chunked = chips.chunked(2)
        for (row in chunked) {
            val rowLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = H.dp(ctx, 3) }
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            for (chip in row) {
                rowLayout.addView(chip)
            }
            container.addView(rowLayout)
        }
        return container
    }

    // ═══════════════════════════════════════════════════════════════
    //  Chip builders per card type
    // ═══════════════════════════════════════════════════════════════

    private fun buildFormChips(ctx: android.content.Context, form: FormMetrics): List<View> {
        val chips = mutableListOf<View>()
        form.rom?.let {
            chips.add(H.metricChip(ctx,
                if (isArabic) "المدى" else "ROM",
                it.displayValue,
                it.status.getColor()
            ))
        }
        form.symmetry?.let {
            chips.add(H.metricChip(ctx,
                if (isArabic) "التوازن" else "Sym",
                it.displayValue,
                it.status.getColor()
            ))
        }
        form.formConsistency?.let {
            chips.add(H.metricChip(ctx,
                if (isArabic) "الثبات" else "Cons",
                it.displayValue,
                it.status.getColor()
            ))
        }
        return chips
    }

    private fun buildSafetyChips(ctx: android.content.Context, safety: SafetyMetrics): List<View> {
        val chips = mutableListOf<View>()
        safety.alignmentAccuracy?.let {
            chips.add(H.metricChip(ctx,
                if (isArabic) "المحاذاة" else "Align",
                it.displayValue,
                it.status.getColor()
            ))
        }
        safety.stability?.let {
            chips.add(H.metricChip(ctx,
                if (isArabic) "الثبات" else "Stab",
                it.displayValue,
                it.status.getColor()
            ))
        }
        if (safety.dangerCount > 0) {
            chips.add(H.metricChip(ctx,
                if (isArabic) "تحذير" else "Danger",
                "${safety.dangerCount}",
                H.colorRed(requireContext())
            ))
        }
        return chips
    }

    private fun buildControlChips(ctx: android.content.Context, control: ControlMetrics): List<View> {
        val chips = mutableListOf<View>()
        control.tempo?.let {
            chips.add(H.metricChip(ctx,
                if (isArabic) "الإيقاع" else "Tempo",
                it.getFormattedTempo(),
                null
            ))
        }
        control.totalTUT?.let {
            chips.add(H.metricChip(ctx, "TUT", "${it}s", null))
        }
        control.fatigueIndex?.let {
            chips.add(H.metricChip(ctx,
                if (isArabic) "التعب" else "Fatigue",
                "#$it",
                if (it > (report?.repTimeline?.size ?: 0) * 0.7) H.colorGreen(requireContext()) else H.colorOrange(requireContext())
            ))
        }
        return chips
    }
}
