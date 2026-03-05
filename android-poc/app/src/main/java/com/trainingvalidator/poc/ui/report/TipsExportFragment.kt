package com.trainingvalidator.poc.ui.report

import android.content.Intent
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

/**
 * Screen 7 — Tips & Export
 *
 * Three-layer tip generation (per Post-Training-Report-Review.md):
 *
 *   1. Strategic Next-Session Recommendation (system-generated)
 *   2. Primary Tips from Exercise Messages
 *      — stateMessages (joint-specific, zone-specific)
 *      — feedbackMessages.tips (general exercise tips)
 *      — positionCheck.errorMessage (position-based errors)
 *   3. Data-Enriched Context (metric numbers appended)
 *
 * Also includes:
 *   • Share button
 *   • PDF export button (placeholder for now)
 *
 * Adapts for:
 *   - No tips: shows "Perfect session" celebration
 *   - Hold exercises: different strategic recommendation
 */
class TipsExportFragment : Fragment() {

    companion object {
        fun newInstance() = TipsExportFragment()
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

        // Header
        col.addView(H.sectionTitle(ctx, "💡", if (isArabic) "نصائح وتصدير" else "Tips & Export"))
        col.addView(H.sectionSubtitle(ctx,
            if (isArabic) "نصائح مخصصة بناءً على أدائك" else "Personalized tips based on your performance"
        ))

        // ── Layer 1: Strategic Recommendation ────────────────────
        col.addView(buildStrategicRecommendation(ctx, report))

        // ── Layer 2 + 3: Exercise-specific tips + data context ───
        val tips = report.improvementTips
        if (tips.isEmpty()) {
            col.addView(buildPerfectSessionMessage(ctx))
        } else {
            // Group tips by severity
            val critical = tips.filter { it.severity == TipSeverity.CRITICAL }
            val important = tips.filter { it.severity == TipSeverity.IMPORTANT }
            val helpful = tips.filter { it.severity == TipSeverity.HELPFUL }

            if (critical.isNotEmpty()) {
                col.addView(buildTipGroupHeader(ctx, "🚨",
                    if (isArabic) "أولوية قصوى" else "Critical Priority",
                    H.colorRed(ctx)
                ))
                critical.forEachIndexed { i, tip -> col.addView(buildTipCard(ctx, tip, i + 1, H.colorRed(ctx))) }
            }
            if (important.isNotEmpty()) {
                col.addView(buildTipGroupHeader(ctx, "⚠️",
                    if (isArabic) "مهم" else "Important",
                    H.colorOrange(ctx)
                ))
                important.forEachIndexed { i, tip -> col.addView(buildTipCard(ctx, tip, i + 1, H.colorOrange(ctx))) }
            }
            if (helpful.isNotEmpty()) {
                col.addView(buildTipGroupHeader(ctx, "💡",
                    if (isArabic) "نصائح مفيدة" else "Helpful Tips",
                    H.colorGreen(ctx)
                ))
                helpful.forEachIndexed { i, tip -> col.addView(buildTipCard(ctx, tip, i + 1, H.colorGreen(ctx))) }
            }
        }

        // ── Error analysis tips (from exercise errorAnalysis) ────
        if (report.errorAnalysis.isNotEmpty()) {
            col.addView(H.divider(ctx))
            col.addView(buildErrorBasedTips(ctx, report))
        }

        // ── Action buttons ───────────────────────────────────────
        col.addView(H.divider(ctx))
        col.addView(buildActionButtons(ctx))
    }

    // ═══════════════════════════════════════════════════════════════
    //  Layer 1: Strategic recommendation
    // ═══════════════════════════════════════════════════════════════

    private fun buildStrategicRecommendation(ctx: android.content.Context, report: PostTrainingReport): LinearLayout {
        val score = report.overallQuality?.score ?: report.summary.averageScore
        val isHold = report.isHoldExercise()
        val hasDanger = report.hasDangerAlerts()

        val (icon, title, body) = when {
            hasDanger -> Triple("⚠️",
                if (isArabic) "الجلسة القادمة: سلامة أولاً" else "Next Session: Safety First",
                if (isArabic) "ركز على تصحيح الأوضاع الخطرة قبل زيادة الحمل"
                else "Focus on correcting unsafe positions before increasing load"
            )
            score >= 90 -> Triple("🚀",
                if (isArabic) "الجلسة القادمة: تحدى نفسك!" else "Next Session: Challenge Yourself!",
                if (isHold) {
                    if (isArabic) "حاول زيادة مدة الثبات أو إضافة أوزان"
                    else "Try increasing hold duration or adding weight"
                } else {
                    if (isArabic) "جرب زيادة الوزن أو إضافة عدات"
                    else "Try increasing weight or adding more reps"
                }
            )
            score >= 70 -> Triple("🎯",
                if (isArabic) "الجلسة القادمة: ثبّت الأساس" else "Next Session: Solidify Basics",
                if (isArabic) "حافظ على نفس الوزن وركز على تحسين الشكل"
                else "Keep the same weight and focus on improving form"
            )
            else -> Triple("📈",
                if (isArabic) "الجلسة القادمة: أساسيات أولاً" else "Next Session: Fundamentals First",
                if (isArabic) "قلل الوزن وركز على إتقان الحركة"
                else "Reduce weight and focus on mastering the movement"
            )
        }

        val card = H.glassCard(ctx, H.colorBlue(ctx))
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply { text = icon; textSize = 28f })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = H.dp(ctx, 12) }
                addView(TextView(ctx).apply {
                    text = title; textSize = 16f; setTextColor(H.textWhite(ctx))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                addView(TextView(ctx).apply {
                    text = body; textSize = 13f; setTextColor(H.textMuted(ctx))
                    setPadding(0, H.dp(ctx, 2), 0, 0)
                })
            })
        })
        return card
    }

    // ═══════════════════════════════════════════════════════════════
    //  Layer 2+3: Tip cards
    // ═══════════════════════════════════════════════════════════════

    private fun buildTipGroupHeader(
        ctx: android.content.Context,
        icon: String,
        title: String,
        color: Int
    ): TextView {
        return TextView(ctx).apply {
            text = "$icon $title"
            textSize = 15f; setTextColor(color)
            setPadding(0, H.dp(ctx, 16), 0, H.dp(ctx, 6))
        }
    }

    private fun buildTipCard(
        ctx: android.content.Context,
        tip: ImprovementTip,
        number: Int,
        accentColor: Int
    ): LinearLayout {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 8) }
            setPadding(H.dp(ctx, 14), H.dp(ctx, 14), H.dp(ctx, 14), H.dp(ctx, 14))
            background = GradientDrawable().apply {
                setColor(H.cardBg(ctx)); setStroke(1, accentColor)
                cornerRadius = H.dp(ctx, 12).toFloat()
            }
        }

        // Number badge
        card.addView(TextView(ctx).apply {
            text = "$number"; textSize = 16f; setTextColor(H.textWhite(ctx))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(H.dp(ctx, 32), H.dp(ctx, 32)).apply {
                marginEnd = H.dp(ctx, 12)
            }
            background = GradientDrawable().apply {
                setColor(accentColor); cornerRadius = H.dp(ctx, 16).toFloat()
            }
        })

        // Content
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        content.addView(TextView(ctx).apply {
            text = if (isArabic) tip.title.ar else tip.title.en
            textSize = 14f; setTextColor(H.textWhite(ctx))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(ctx).apply {
            text = if (isArabic) tip.description.ar else tip.description.en
            textSize = 12f; setTextColor(H.textMuted(ctx)); maxLines = 3
            setPadding(0, H.dp(ctx, 2), 0, 0)
        })

        // Related reps annotation (data context)
        if (tip.relatedReps.isNotEmpty()) {
            content.addView(TextView(ctx).apply {
                val repsStr = tip.relatedReps.joinToString(", ") { "#$it" }
                text = if (isArabic) "العدات: $repsStr" else "Reps: $repsStr"
                textSize = 11f; setTextColor(accentColor)
                setPadding(0, H.dp(ctx, 4), 0, 0)
            })
        }

        card.addView(content)

        // Icon
        card.addView(TextView(ctx).apply {
            text = tip.icon; textSize = 22f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = H.dp(ctx, 8) }
        })

        return card
    }

    // ═══════════════════════════════════════════════════════════════
    //  Error-based tips (from errorAnalysis + exercise messages)
    // ═══════════════════════════════════════════════════════════════

    private fun buildErrorBasedTips(ctx: android.content.Context, report: PostTrainingReport): LinearLayout {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(TextView(ctx).apply {
            text = if (isArabic) "🔍 من تحليل الأخطاء" else "🔍 From Error Analysis"
            textSize = 15f; setTextColor(H.textWhite(ctx))
            setPadding(0, 0, 0, H.dp(ctx, 8))
        })

        // Show top 3 most frequent errors with their exercise-specific messages
        for (error in report.errorAnalysis.sortedByDescending { it.count }.take(3)) {
            val errorCard = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = H.dp(ctx, 8) }
                setPadding(H.dp(ctx, 14), H.dp(ctx, 12), H.dp(ctx, 14), H.dp(ctx, 12))
                val borderColor = if (error.isDanger()) H.colorRed(ctx) else H.colorOrange(ctx)
                background = GradientDrawable().apply {
                    setColor(H.cardBg(ctx)); setStroke(1, borderColor)
                    cornerRadius = H.dp(ctx, 10).toFloat()
                }
            }

            // Joint name + state + count
            errorCard.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(ctx).apply {
                    text = error.stateIcon; textSize = 18f
                })
                addView(TextView(ctx).apply {
                    val jointName = if (isArabic) error.jointName.ar else error.jointName.en
                    text = jointName
                    textSize = 14f; setTextColor(H.textWhite(ctx))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { marginStart = H.dp(ctx, 6) }
                })
                addView(TextView(ctx).apply {
                    text = if (isArabic) "${error.count}×" else "${error.count}×"
                    textSize = 14f; setTextColor(H.textMuted(ctx))
                })
            })

            // Exercise-specific message (from stateMessages)
            errorCard.addView(TextView(ctx).apply {
                text = if (isArabic) error.message.ar else error.message.en
                textSize = 13f; setTextColor(H.textMuted(ctx))
                setPadding(0, H.dp(ctx, 4), 0, 0)
            })

            // Exercise-specific tip (from feedbackMessages.tips)
            errorCard.addView(TextView(ctx).apply {
                text = "💡 ${if (isArabic) error.tip.ar else error.tip.en}"
                textSize = 12f; setTextColor(H.colorGreen(ctx))
                setPadding(0, H.dp(ctx, 6), 0, 0)
            })

            container.addView(errorCard)
        }
        return container
    }

    // ═══════════════════════════════════════════════════════════════
    //  Perfect session
    // ═══════════════════════════════════════════════════════════════

    private fun buildPerfectSessionMessage(ctx: android.content.Context): LinearLayout {
        val card = H.glassCard(ctx, H.colorGreen(ctx))
        card.gravity = Gravity.CENTER
        card.addView(TextView(ctx).apply {
            text = "🎉"; textSize = 48f; gravity = Gravity.CENTER
        })
        card.addView(TextView(ctx).apply {
            text = if (isArabic) "أداء ممتاز!" else "Excellent Performance!"
            textSize = 20f; setTextColor(H.colorGreen(ctx)); gravity = Gravity.CENTER
            setPadding(0, H.dp(ctx, 10), 0, 0)
        })
        card.addView(TextView(ctx).apply {
            text = if (isArabic) "لا توجد ملاحظات — استمر على هذا المستوى!"
            else "No improvement notes — keep up the great work!"
            textSize = 14f; setTextColor(H.textMuted(ctx)); gravity = Gravity.CENTER
        })
        return card
    }

    // ═══════════════════════════════════════════════════════════════
    //  Action buttons
    // ═══════════════════════════════════════════════════════════════

    private fun buildActionButtons(ctx: android.content.Context): LinearLayout {
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = H.dp(ctx, 8) }
            gravity = Gravity.CENTER
            weightSum = 2f
        }

        // Share button
        container.addView(buildActionButton(ctx, "📤",
            if (isArabic) "مشاركة" else "Share"
        ) { shareReport() })

        // PDF Export button
        container.addView(buildActionButton(ctx, "📄",
            if (isArabic) "تصدير PDF" else "Export PDF"
        ) { exportPdf() })

        return container
    }

    private fun buildActionButton(
        ctx: android.content.Context,
        icon: String,
        label: String,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = H.dp(ctx, 6); marginEnd = H.dp(ctx, 6)
            }
            setPadding(H.dp(ctx, 16), H.dp(ctx, 14), H.dp(ctx, 16), H.dp(ctx, 14))
            background = GradientDrawable().apply {
                setColor(H.cardBg(ctx))
                setStroke(1, H.cardBorder(ctx))
                cornerRadius = H.dp(ctx, 12).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            addView(TextView(ctx).apply { text = icon; textSize = 20f })
            addView(TextView(ctx).apply {
                text = label; textSize = 14f; setTextColor(H.textWhite(ctx))
                setPadding(H.dp(ctx, 8), 0, 0, 0)
            })
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════════════════════

    private fun shareReport() {
        val report = this.report ?: return
        val exerciseName = if (isArabic) report.exerciseName.ar else report.exerciseName.en
        val score = report.overallQuality?.getFormattedScore()
            ?: report.summary.getFormattedScore()
        val reps = report.summary.totalReps
        val duration = report.summary.getFormattedDuration()

        val shareText = if (isArabic) {
            "🏋️ $exerciseName\n" +
                    "📊 الجودة: $score\n" +
                    "🔄 العدات: $reps\n" +
                    "⏱ المدة: $duration\n\n" +
                    "#تمرين #لياقة"
        } else {
            "🏋️ $exerciseName\n" +
                    "📊 Quality: $score\n" +
                    "🔄 Reps: $reps\n" +
                    "⏱ Duration: $duration\n\n" +
                    "#workout #fitness"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent,
            if (isArabic) "مشاركة التقرير" else "Share Report"
        ))
    }

    private fun exportPdf() {
        // TODO: Implement PDF generation
        // For now, fall back to share
        shareReport()
    }
}
