package com.trainingvalidator.poc.ui.report

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.trainingvalidator.poc.training.report.MetricStatus
import com.trainingvalidator.poc.training.report.MetricWithStatus

/**
 * ReportUiHelper — Shared UI factory for the V2 report screens.
 *
 * All fragments build their layouts programmatically; this object centralises
 * common primitives (glass cards, metric rows, score rings, section headers)
 * so the code stays DRY and visually consistent.
 */
object ReportUiHelper {

    // ─── Colour constants ────────────────────────────────────────
    const val BG_DARK = 0xFF0A0F1A.toInt()
    const val TEXT_WHITE = 0xFFFFFFFF.toInt()
    const val TEXT_MUTED = 0xAAFFFFFF.toInt()
    const val CARD_BG = 0x1AFFFFFF
    const val CARD_BORDER_DEFAULT = 0x33FFFFFF
    const val GREEN = 0xFF4CAF50.toInt()
    const val LIGHT_GREEN = 0xFF8BC34A.toInt()
    const val YELLOW = 0xFFFFC107.toInt()
    const val RED = 0xFFFF5252.toInt()
    const val ORANGE = 0xFFFF9800.toInt()
    const val BLUE = 0xFF2196F3.toInt()

    fun dp(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    // ─── Full-page scrollable root ──────────────────────────────
    fun pageRoot(context: Context): ScrollView {
        return ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    fun pageColumn(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(BG_DARK)
            setPadding(dp(context, 24), dp(context, 80), dp(context, 24), dp(context, 120))
        }
    }

    // ─── Section header (icon + title + subtitle) ───────────────
    fun sectionTitle(context: Context, icon: String, title: String): TextView {
        return TextView(context).apply {
            text = "$icon $title"
            textSize = 24f
            setTextColor(TEXT_WHITE)
            setPadding(0, 0, 0, dp(context, 4))
        }
    }

    fun sectionSubtitle(context: Context, subtitle: String): TextView {
        return TextView(context).apply {
            text = subtitle
            textSize = 14f
            setTextColor(TEXT_MUTED)
            setPadding(0, 0, 0, dp(context, 20))
        }
    }

    // ─── Glass card container ───────────────────────────────────
    fun glassCard(
        context: Context,
        borderColor: Int = CARD_BORDER_DEFAULT,
        marginTop: Int = 12
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, marginTop) }
            background = GradientDrawable().apply {
                setColor(CARD_BG)
                setStroke(1, borderColor)
                cornerRadius = dp(context, 16).toFloat()
            }
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
        }
    }

    // ─── Score circle (big number) ──────────────────────────────
    fun scoreBadge(context: Context, score: Float, size: Int = 56): LinearLayout {
        val color = MetricStatus.fromPercentage(score).getColor()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                dp(context, size), dp(context, size)
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(context, 3), color)
                setColor(0x1A000000)
            }
            addView(TextView(context).apply {
                text = "${score.toInt()}%"
                textSize = if (size > 48) 18f else 14f
                setTextColor(color)
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
    }

    // ─── Metric row (icon · label · value · status dot) ─────────
    fun metricRow(
        context: Context,
        icon: String,
        label: String,
        value: String,
        status: MetricStatus? = null,
        advice: String? = null
    ): LinearLayout {
        val dp = { v: Int -> dp(context, v) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(0x0DFFFFFF)
                cornerRadius = dp(8).toFloat()
            }

            // Top row: icon + label + value + status dot
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            topRow.addView(TextView(context).apply { text = icon; textSize = 18f })
            topRow.addView(TextView(context).apply {
                text = label; textSize = 14f; setTextColor(TEXT_MUTED)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(8) }
            })
            topRow.addView(TextView(context).apply {
                text = value; textSize = 16f; setTextColor(TEXT_WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            status?.let { s ->
                topRow.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
                        .apply { marginStart = dp(8) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(s.getColor())
                    }
                })
            }
            addView(topRow)

            // Advice line (optional)
            if (!advice.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = advice; textSize = 12f; setTextColor(TEXT_MUTED)
                    setPadding(dp(26), dp(2), 0, 0)
                })
            }
        }
    }

    // ─── Metric from MetricWithStatus ───────────────────────────
    fun metricRow(
        context: Context,
        icon: String,
        label: String,
        metric: MetricWithStatus,
        isArabic: Boolean
    ): LinearLayout {
        return metricRow(
            context, icon, label, metric.displayValue, metric.status,
            metric.advice?.let { if (isArabic) it.ar else it.en }
        )
    }

    // ─── Progress bar row ───────────────────────────────────────
    fun progressRow(
        context: Context,
        label: String,
        percentage: Float,
        color: Int = GREEN
    ): LinearLayout {
        val dp = { v: Int -> dp(context, v) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }

            // Label + percentage
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            header.addView(TextView(context).apply {
                text = label; textSize = 13f; setTextColor(TEXT_MUTED)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            header.addView(TextView(context).apply {
                text = "${percentage.toInt()}%"; textSize = 13f; setTextColor(color)
            })
            addView(header)

            // Bar
            val barBg = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(6)
                ).apply { topMargin = dp(4) }
                background = GradientDrawable().apply {
                    setColor(0x1AFFFFFF); cornerRadius = dp(3).toFloat()
                }
            }
            barBg.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    weight = percentage.coerceIn(0f, 100f) / 100f
                }
                background = GradientDrawable().apply {
                    setColor(color); cornerRadius = dp(3).toFloat()
                }
            })
            // Spacer for remaining bar
            barBg.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                    weight = (100f - percentage.coerceIn(0f, 100f)) / 100f
                }
            })
            barBg.weightSum = 1f
            addView(barBg)
        }
    }

    // ─── Divider ────────────────────────────────────────────────
    fun divider(context: Context): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)
            ).apply { topMargin = dp(context, 12); bottomMargin = dp(context, 12) }
            setBackgroundColor(0x1AFFFFFF)
        }
    }

    // ─── Colour from score ──────────────────────────────────────
    fun colorFromScore(score: Float): Int = when {
        score >= 90 -> GREEN
        score >= 80 -> LIGHT_GREEN
        score >= 70 -> YELLOW
        else -> RED
    }
}
