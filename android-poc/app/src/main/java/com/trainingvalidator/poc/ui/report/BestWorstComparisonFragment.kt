package com.trainingvalidator.poc.ui.report

import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.training.models.RepQuality
import com.trainingvalidator.poc.training.report.BestRepHighlight
import com.trainingvalidator.poc.training.report.FrameCapture
import com.trainingvalidator.poc.training.report.PostTrainingReport
import com.trainingvalidator.poc.training.report.RepReplayClip
import com.trainingvalidator.poc.training.report.WorstRepHighlight
import com.trainingvalidator.poc.ui.report.ReportUiHelper as H
import com.trainingvalidator.poc.ui.report.components.RepReplayPlayerView
import java.io.File

/**
 * Screen 3 ΓÇö Best vs Worst Comparison
 *
 * Exact 50/50 vertical split:
 *   ΓöîΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÉ
 *   Γöé  Γ¡É BEST REP             Γöé  50%
 *   Γöé  [image]  | #N  80%      Γöé
 *   Γöé           | ΓÅ▒ 3.2s       Γöé
 *   Γöé           | Perfect form Γöé
 *   Γö£ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöñ
 *   Γöé  ≡ƒôë WORST REP            Γöé  50%
 *   Γöé  [image]  | #9  80%      Γöé
 *   Γöé           | ΓÅ▒ 6.9s       Γöé
 *   Γöé           | Position warnΓöé
 *   ΓööΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÿ
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
            setBackgroundColor(H.bgDark(requireContext()))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindData()
    }

    override fun onDestroyView() {
        (view as? ViewGroup)?.removeAllViews()
        super.onDestroyView()
    }

    fun setData(report: PostTrainingReport, isArabic: Boolean) {
        this.report = report
        this.isArabic = isArabic
        if (isAdded) bindData()
    }

    // ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ
    //  Bind
    // ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ

    private fun bindData() {
        val report = this.report ?: return
        val ctx = context ?: return
        val root = view as? LinearLayout ?: return
        root.removeAllViews()
        root.layoutDirection =
            if (isArabic) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

        val timeline = report.repTimeline
        val bestRep = report.bestReps.firstOrNull()
        val worstRep = report.worstRep

        val bestHalf = if (bestRep != null) {
            BestHalfUi(
                repNumber = bestRep.repNumber,
                score = bestRep.score,
                duration = bestRep.getFormattedDuration(),
                message = bestRep.reasons.firstOrNull()?.let { if (isArabic) it.ar else it.en },
                frame = bestRep.frameCapture,
                quality = bestRep.quality,
                replayClip = bestRep.replayClip
            )
        } else {
            val entry = timeline.firstOrNull { it.isBestRep }
                ?: timeline.maxByOrNull { it.score }
            if (entry == null) {
                BestHalfUi(
                    repNumber = null,
                    score = null,
                    duration = null,
                    message = null,
                    frame = null,
                    quality = null,
                    replayClip = null
                )
            } else {
                BestHalfUi(
                    repNumber = entry.repNumber,
                    score = entry.score,
                    duration = entry.getFormattedDuration(),
                    message = if (isArabic) entry.stateDisplayName.ar else entry.stateDisplayName.en,
                    frame = entry.frameCapture,
                    quality = entry.quality,
                    replayClip = null
                )
            }
        }

        // ΓöÇΓöÇ Top half: Best Rep (weight = 1) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        root.addView(
            buildHalf(
                isBest = true,
                repNumber = bestHalf.repNumber,
                score = bestHalf.score,
                duration = bestHalf.duration,
                quality = bestHalf.quality ?: RepQuality.CLEAN,
                message = bestHalf.message
                    ?: defaultBestMessage(bestHalf.quality ?: RepQuality.CLEAN),
                frame = bestHalf.frame,
                replayClip = bestHalf.replayClip
            )
        )

        // ΓöÇΓöÇ Thin VS divider ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        root.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, H.dp(ctx, 1)
            )
            setBackgroundColor(0x33FFFFFF)
        })

        // ΓöÇΓöÇ Bottom half: Worst Rep (weight = 1) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        if (worstRep != null) {
            root.addView(
                buildHalf(
                    isBest = false,
                    repNumber = worstRep.repNumber,
                    score = worstRep.score,
                    duration = worstRep.getFormattedDuration(),
                    quality = worstRep.quality,
                    message = if (isArabic) worstRep.primaryError.ar else worstRep.primaryError.en,
                    frame = worstRep.frameCapture,
                    replayClip = worstRep.replayClip
                )
            )
        } else {
            val worstEntry = timeline.firstOrNull { it.isWorstRep }
                ?: timeline.minByOrNull { it.score }
            if (worstEntry != null && timeline.size >= 2) {
                val primary = worstEntry.stateMessage ?: worstEntry.stateDisplayName
                root.addView(
                    buildHalf(
                        isBest = false,
                        repNumber = worstEntry.repNumber,
                        score = worstEntry.score,
                        duration = worstEntry.getFormattedDuration(),
                        quality = worstEntry.quality,
                        message = if (isArabic) primary.ar else primary.en,
                        frame = worstEntry.frameCapture,
                        replayClip = null
                    )
                )
            } else {
                root.addView(buildAllGreatHalf())
            }
        }
    }

    // ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ
    //  Build one half (50%)
    // ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ

    private fun buildHalf(
        isBest: Boolean,
        repNumber: Int?,
        score: Float?,
        duration: String?,
        quality: RepQuality,
        message: String,
        frame: FrameCapture?,
        replayClip: RepReplayClip?
    ): LinearLayout {
        val ctx = requireContext()
        val accent = if (isBest) H.colorGreen(ctx) else H.colorOrange(ctx)
        val bgColor = if (isBest) 0x0D4CAF50 else 0x0DFF9800  // very subtle tint

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f  // weight = 1 ΓåÆ exact 50%
            )
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(bgColor)
            setPadding(H.dp(ctx, 16), H.dp(ctx, 12), H.dp(ctx, 16), H.dp(ctx, 12))

            // ΓöÇΓöÇ Left: Image ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
            addView(buildImageSection(frame, replayClip, accent, isBest, quality, repNumber))

            // ΓöÇΓöÇ Right: Stats ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
            addView(buildStatsSection(isBest, repNumber, score, duration, quality, message, accent))
        }
    }

    private fun buildImageSection(
        frame: FrameCapture?,
        replayClip: RepReplayClip?,
        accent: Int,
        isBest: Boolean,
        quality: RepQuality,
        repNumber: Int?
    ): FrameLayout {
        val ctx = requireContext()
        val cornerPx = H.dp(ctx, 16).toFloat()

        val outer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = H.dp(ctx, 14)
            }
        }

        val mediaCard = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable().apply {
                setColor(0x1AFFFFFF)
                cornerRadius = cornerPx
                setStroke(H.dp(ctx, 2), accent)
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
                }
            }
            clipToOutline = true
        }

        val noStill = !hasViewableStill(frame, replayClip)
        val placeholderHint = if (noStill) {
            if (isArabic) "┘ä┘à ┘è╪¬┘à ╪º┘ä╪¬┘é╪º╪╖ ╪╡┘ê╪▒╪⌐ ┘ä┘ç╪░┘ç ╪º┘ä╪╣╪»╪⌐" else "No frame captured for this rep"
        } else {
            null
        }

        val replayView = RepReplayPlayerView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            bind(
                replayClip = replayClip,
                fallbackFrame = frame,
                accentColor = accent,
                isArabic = isArabic,
                placeholderHint = placeholderHint
            )
        }
        mediaCard.addView(replayView)

        if (canOpenFullStill(frame)) {
            val expand = ImageButton(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    H.dp(ctx, 40),
                    H.dp(ctx, 40),
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = H.dp(ctx, 6)
                    marginEnd = H.dp(ctx, 6)
                }
                setImageResource(android.R.drawable.ic_menu_search)
                contentDescription =
                    if (isArabic) "╪╣╪▒╪╢ ╪º┘ä╪╡┘ê╪▒╪⌐ ╪¿╪º┘ä╪¡╪¼┘à ╪º┘ä┘â╪º┘à┘ä" else "View full image"
                setBackgroundColor(0x66000000)
                setOnClickListener {
                    openFrameViewer(frame, getHalfTitle(isBest, quality), repNumber)
                }
            }
            mediaCard.addView(expand)
        }

        outer.addView(mediaCard)
        return outer
    }

    private fun hasViewableStill(frame: FrameCapture?, replayClip: RepReplayClip?): Boolean {
        if (replayClip?.frames?.any { File(it.frameUri).exists() } == true) return true
        val f = frame ?: return false
        return (f.frameUri.isNotEmpty() && File(f.frameUri).exists()) ||
            (f.thumbnailUri.isNotEmpty() && File(f.thumbnailUri).exists())
    }

    private fun canOpenFullStill(frame: FrameCapture?): Boolean {
        val f = frame ?: return false
        return (f.frameUri.isNotEmpty() && File(f.frameUri).exists()) ||
            (f.thumbnailUri.isNotEmpty() && File(f.thumbnailUri).exists())
    }

    private fun openFrameViewer(frame: FrameCapture?, title: String, repNumber: Int?) {
        val f = frame ?: return
        val path = when {
            f.frameUri.isNotEmpty() && File(f.frameUri).exists() -> f.frameUri
            f.thumbnailUri.isNotEmpty() && File(f.thumbnailUri).exists() -> f.thumbnailUri
            else -> return
        }
        val forDialog = if (path == f.frameUri) f else f.copy(frameUri = path)
        val details = repNumber?.let { if (isArabic) "╪º┘ä╪╣╪»╪⌐ #$it" else "Rep #$it" }.orEmpty()
        ImageViewerDialog(requireContext(), forDialog, title = title, details = details).show()
    }

    private fun buildStatsSection(
        isBest: Boolean,
        repNumber: Int?,
        score: Float?,
        duration: String?,
        quality: RepQuality,
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
                text = getHalfTitle(isBest, quality)
                textSize = 15f
                setTextColor(accent)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            // Rep #
            if (repNumber != null) {
                addView(TextView(ctx).apply {
                    text = if (isArabic) "╪º┘ä╪╣╪»╪⌐ #$repNumber" else "Rep #$repNumber"
                    textSize = 13f
                    setTextColor(H.textMuted(ctx))
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
                    text = "ΓÅ▒ $duration"
                    textSize = 14f
                    setTextColor(H.textMuted(ctx))
                    setPadding(0, H.dp(ctx, 4), 0, 0)
                })
            }

            // Message
            addView(TextView(ctx).apply {
                text = message
                textSize = 13f
                setTextColor(H.textWhite(ctx))
                maxLines = 2
                setPadding(0, H.dp(ctx, 6), 0, 0)
            })
        }
    }

    // ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ
    //  All-great fallback
    // ΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉΓòÉ

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
                text = "≡ƒÄë"
                textSize = 48f
                gravity = Gravity.CENTER
            })
            addView(TextView(ctx).apply {
                text = if (isArabic) "╪¼┘à┘è╪╣ ╪º┘ä╪╣╪»╪º╪¬ ┘â╪º┘å╪¬ ╪▒╪º╪ª╪╣╪⌐!" else "All reps were great!"
                textSize = 18f
                setTextColor(H.colorGreen(ctx))
                gravity = Gravity.CENTER
                setPadding(0, H.dp(ctx, 8), 0, 0)
            })
            addView(TextView(ctx).apply {
                text = if (isArabic) "┘ä┘à ┘è┘Å┘ä╪º╪¡╪╕ ┘ü╪▒┘é ┘â╪¿┘è╪▒ ╪¿┘è┘å ╪º┘ä╪╣╪»╪º╪¬"
                else "No significant difference between reps"
                textSize = 13f
                setTextColor(H.textMuted(ctx))
                gravity = Gravity.CENTER
            })
        }
    }

    private fun getHalfTitle(isBest: Boolean, quality: RepQuality): String {
        return if (isBest) {
            when (quality) {
                RepQuality.CLEAN -> if (isArabic) "Γ¡É ╪ú┘ü╪╢┘ä ╪╣╪»╪⌐" else "Γ¡É Best Rep"
                RepQuality.NEEDS_CORRECTION -> if (isArabic) "Γ¡É ╪ú┘ü╪╢┘ä ┘à╪¡╪º┘ê┘ä╪⌐" else "Γ¡É Best Attempt"
                RepQuality.DANGER -> if (isArabic) "Γ¡É ╪ú┘ü╪╢┘ä ┘à╪¡╪º┘ê┘ä╪⌐ ┘à╪¬╪º╪¡╪⌐" else "Γ¡É Best Available Attempt"
            }
        } else {
            when (quality) {
                RepQuality.DANGER -> if (isArabic) "≡ƒôë ╪ú╪«╪╖╪▒ ╪╣╪»╪⌐" else "≡ƒôë Most Dangerous Rep"
                else -> if (isArabic) "≡ƒôë ╪ú╪│┘ê╪ú ╪╣╪»╪⌐" else "≡ƒôë Worst Rep"
            }
        }
    }

    private fun defaultBestMessage(quality: RepQuality): String {
        return when (quality) {
            RepQuality.CLEAN -> if (isArabic) "╪ú┘ü╪╢┘ä ╪╣╪»╪⌐ ┘à┘â╪¬┘à┘ä╪⌐ ┘ü┘è ╪º┘ä╪¼┘ä╪│╪⌐" else "Best completed rep in the session"
            RepQuality.NEEDS_CORRECTION -> if (isArabic) "╪ú┘ü╪╢┘ä ┘à╪¡╪º┘ê┘ä╪⌐ ┘à┘â╪¬┘à┘ä╪⌐ ┘ä┘â┘å┘ç╪º ╪¬╪¡╪¬╪º╪¼ ╪¬╪╡╪¡┘è╪¡" else "Best completed attempt, but it still needs correction"
            RepQuality.DANGER -> if (isArabic) "╪ú┘ü╪╢┘ä ┘à╪¡╪º┘ê┘ä╪⌐ ┘à╪¬╪º╪¡╪⌐ ┘ä┘â┘å┘ç╪º ┘â╪º┘å╪¬ ╪«╪╖╪▒╪⌐" else "Best available attempt, but it was dangerous"
        }
    }
}

private data class BestHalfUi(
    val repNumber: Int?,
    val score: Float?,
    val duration: String?,
    val message: String?,
    val frame: FrameCapture?,
    val quality: RepQuality?,
    val replayClip: RepReplayClip?
)
