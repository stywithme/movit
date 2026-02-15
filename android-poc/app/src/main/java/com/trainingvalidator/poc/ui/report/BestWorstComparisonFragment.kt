package com.trainingvalidator.poc.ui.report

import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.training.report.*
import com.trainingvalidator.poc.ui.report.ReportUiHelper as H
import java.io.File

/**
 * Screen 3 — Best vs Worst Comparison
 *
 * Exact 50/50 vertical split:
 *   ┌──────────────────────────┐
 *   │  ⭐ BEST REP             │  50%
 *   │  [image]  | #N  80%      │
 *   │           | ⏱ 3.2s       │
 *   │           | Perfect form │
 *   ├──────────────────────────┤
 *   │  📉 WORST REP            │  50%
 *   │  [image]  | #9  80%      │
 *   │           | ⏱ 6.9s       │
 *   │           | Position warn│
 *   └──────────────────────────┘
 */
class BestWorstComparisonFragment : Fragment() {

    companion object {
        fun newInstance() = BestWorstComparisonFragment()
    }

    private var report: PostTrainingReport? = null
    private var isArabic = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(H.BG_DARK)
        }
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
        val root = view as? LinearLayout ?: return
        root.removeAllViews()

        val bestRep = report.bestReps.firstOrNull()
        val worstRep = report.worstRep

        // ── Top half: Best Rep (weight = 1) ───────────────────────
        root.addView(
            buildHalf(
                isBest = true,
                repNumber = bestRep?.repNumber,
                score = bestRep?.score,
                duration = bestRep?.getFormattedDuration(),
                message = bestRep?.reasons?.firstOrNull()?.let { if (isArabic) it.ar else it.en }
                    ?: if (isArabic) "شكل مثالي!" else "Perfect form!",
                frame = bestRep?.frameCapture
            )
        )

        // ── Thin VS divider ───────────────────────────────────────
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, H.dp(ctx, 1)
            )
            setBackgroundColor(0x33FFFFFF)
        })

        // ── Bottom half: Worst Rep (weight = 1) ──────────────────
        if (worstRep != null) {
            root.addView(
                buildHalf(
                    isBest = false,
                    repNumber = worstRep.repNumber,
                    score = (100 - worstRep.errorCount * 15).coerceAtLeast(0).toFloat(),
                    duration = worstRep.getFormattedDuration(),
                    message = if (isArabic) worstRep.primaryError.ar else worstRep.primaryError.en,
                    frame = worstRep.frameCapture
                )
            )
        } else {
            root.addView(buildAllGreatHalf())
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Build one half (50%)
    // ═══════════════════════════════════════════════════════════════

    private fun buildHalf(
        isBest: Boolean,
        repNumber: Int?,
        score: Float?,
        duration: String?,
        message: String,
        frame: FrameCapture?
    ): LinearLayout {
        val ctx = requireContext()
        val accent = if (isBest) H.GREEN else H.ORANGE
        val bgColor = if (isBest) 0x0D4CAF50 else 0x0DFF9800  // very subtle tint

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f  // weight = 1 → exact 50%
            )
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(bgColor)
            setPadding(H.dp(ctx, 16), H.dp(ctx, 12), H.dp(ctx, 16), H.dp(ctx, 12))

            // ── Left: Image ──────────────────────────
            addView(buildImageSection(frame, accent))

            // ── Right: Stats ─────────────────────────
            addView(buildStatsSection(isBest, repNumber, score, duration, message, accent))
        }
    }

    private fun buildImageSection(frame: FrameCapture?, accent: Int): FrameLayout {
        val ctx = requireContext()
        val container = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = H.dp(ctx, 14)
            }
            background = GradientDrawable().apply {
                setColor(0x1AFFFFFF)
                cornerRadius = H.dp(ctx, 16).toFloat()
                setStroke(H.dp(ctx, 2), accent)
            }
            clipToOutline = true
        }

        val imageView = ImageView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        if (frame != null) {
            val file = File(frame.frameUri.ifEmpty { frame.thumbnailUri })
            if (file.exists()) {
                imageView.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            } else {
                imageView.setImageResource(R.drawable.ic_person_placeholder)
                imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        } else {
            imageView.setImageResource(R.drawable.ic_person_placeholder)
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        container.addView(imageView)
        return container
    }

    private fun buildStatsSection(
        isBest: Boolean,
        repNumber: Int?,
        score: Float?,
        duration: String?,
        message: String,
        accent: Int
    ): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER_VERTICAL

            // Badge label
            addView(TextView(ctx).apply {
                text = if (isBest) {
                    if (isArabic) "⭐ أفضل عدة" else "⭐ Best Rep"
                } else {
                    if (isArabic) "📉 أسوأ عدة" else "📉 Worst Rep"
                }
                textSize = 15f
                setTextColor(accent)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            // Rep #
            if (repNumber != null) {
                addView(TextView(ctx).apply {
                    text = if (isArabic) "العدة #$repNumber" else "Rep #$repNumber"
                    textSize = 13f
                    setTextColor(H.TEXT_MUTED)
                    setPadding(0, H.dp(ctx, 4), 0, 0)
                })
            }

            // Score (large)
            addView(TextView(ctx).apply {
                text = "${score?.toInt() ?: 0}%"
                textSize = 44f
                setTextColor(accent)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, H.dp(ctx, 6), 0, 0)
            })

            // Duration
            if (duration != null) {
                addView(TextView(ctx).apply {
                    text = "⏱ $duration"
                    textSize = 14f
                    setTextColor(H.TEXT_MUTED)
                    setPadding(0, H.dp(ctx, 4), 0, 0)
                })
            }

            // Message
            addView(TextView(ctx).apply {
                text = message
                textSize = 13f
                setTextColor(H.TEXT_WHITE)
                maxLines = 2
                setPadding(0, H.dp(ctx, 6), 0, 0)
            })
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  All-great fallback
    // ═══════════════════════════════════════════════════════════════

    private fun buildAllGreatHalf(): LinearLayout {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            gravity = Gravity.CENTER
            setBackgroundColor(0x0D4CAF50)

            addView(TextView(ctx).apply {
                text = "🎉"
                textSize = 48f
                gravity = Gravity.CENTER
            })
            addView(TextView(ctx).apply {
                text = if (isArabic) "جميع العدات كانت رائعة!" else "All reps were great!"
                textSize = 18f
                setTextColor(H.GREEN)
                gravity = Gravity.CENTER
                setPadding(0, H.dp(ctx, 8), 0, 0)
            })
            addView(TextView(ctx).apply {
                text = if (isArabic) "لم يُلاحظ فرق كبير بين العدات"
                else "No significant difference between reps"
                textSize = 13f
                setTextColor(H.TEXT_MUTED)
                gravity = Gravity.CENTER
            })
        }
    }
}
