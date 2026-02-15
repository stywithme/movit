package com.trainingvalidator.poc.ui.report

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.trainingvalidator.poc.training.report.PostTrainingReport

/**
 * RepPagerAdapter — V2 Report Pager
 *
 * Navigates 7 screens per exercise (no individual-rep pages):
 *
 *  0  Hero             — Overall score + reps + duration + QuickInsight + Share
 *  1  Overview         — Reps Journey chart + Form / Safety / Control cards
 *  2  Best vs Worst    — Mirrored visual comparison
 *  3  Form Details     — ROM, Symmetry, Form Consistency
 *  4  Safety Details   — Alignment, Stability, DangerAlerts
 *  5  Control+Fatigue  — Tempo, TUT, VL%, Fatigue analysis, Load
 *  6  Tips & Export    — Exercise-message tips + PDF / Share
 *
 * Pages are dynamically included/excluded based on data availability:
 *  - Overview  → hidden when < 2 reps (nothing to chart)
 *  - Best/Worst → hidden when no best reps or 0 reps
 *  - Form/Safety/Control detail screens → always shown (adapt internally)
 *  - Tips → always shown
 */
class RepPagerAdapter(
    activity: FragmentActivity,
    private val report: PostTrainingReport,
    private val isArabic: Boolean
) : FragmentStateAdapter(activity) {

    companion object {
        const val TYPE_HERO = 0
        const val TYPE_OVERVIEW = 1
        const val TYPE_BEST_WORST = 2
        const val TYPE_FORM_DETAILS = 3
        const val TYPE_SAFETY_DETAILS = 4
        const val TYPE_CONTROL_DETAILS = 5
        const val TYPE_TIPS = 6
    }

    private val repCount = report.repTimeline.size
    private val isHold = report.exerciseConfig?.isHoldExercise() == true

    // Build the dynamic page list based on available data
    private val pageList: List<Int> = buildList {
        add(TYPE_HERO)

        // Overview: need ≥ 2 reps for a meaningful chart (or hold exercise for timeline)
        if (repCount >= 2 || isHold) {
            add(TYPE_OVERVIEW)
        }

        // Best vs Worst: need at least one best rep AND it's not a hold exercise
        if (!isHold && report.bestReps.isNotEmpty() && repCount >= 2) {
            add(TYPE_BEST_WORST)
        }

        // Detail screens — always present; each adapts internally
        add(TYPE_FORM_DETAILS)
        add(TYPE_SAFETY_DETAILS)
        add(TYPE_CONTROL_DETAILS)

        // Tips — always shown
        add(TYPE_TIPS)
    }

    override fun getItemCount(): Int = pageList.size

    override fun createFragment(position: Int): Fragment {
        return when (pageList[position]) {
            TYPE_HERO -> ReportPageFragment.newSummaryInstance().also {
                it.setData(report, isArabic, itemCount, position)
            }

            TYPE_OVERVIEW -> PerformanceOverviewFragment.newInstance().also {
                it.setData(report, isArabic)
            }

            TYPE_BEST_WORST -> BestWorstComparisonFragment.newInstance().also {
                it.setData(report, isArabic)
            }

            TYPE_FORM_DETAILS -> FormDetailsFragment.newInstance().also {
                it.setData(report, isArabic)
            }

            TYPE_SAFETY_DETAILS -> SafetyDetailsFragment.newInstance().also {
                it.setData(report, isArabic)
            }

            TYPE_CONTROL_DETAILS -> ControlFatigueFragment.newInstance().also {
                it.setData(report, isArabic)
            }

            TYPE_TIPS -> TipsExportFragment.newInstance().also {
                it.setData(report, isArabic)
            }

            else -> ReportPageFragment.newSummaryInstance().also {
                it.setData(report, isArabic, itemCount, position)
            }
        }
    }

    /** Human-readable page title for accessibility and indicators. */
    fun getPageTitle(position: Int): String {
        return when (pageList.getOrNull(position)) {
            TYPE_HERO -> if (isArabic) "ملخص التمرين" else "Exercise Summary"
            TYPE_OVERVIEW -> if (isArabic) "نظرة شاملة" else "Performance Overview"
            TYPE_BEST_WORST -> if (isArabic) "أفضل وأسوأ عدة" else "Best vs Worst"
            TYPE_FORM_DETAILS -> if (isArabic) "تفاصيل الشكل" else "Form Details"
            TYPE_SAFETY_DETAILS -> if (isArabic) "تفاصيل الأمان" else "Safety Details"
            TYPE_CONTROL_DETAILS -> if (isArabic) "التحكم والتعب" else "Control & Fatigue"
            TYPE_TIPS -> if (isArabic) "النصائح" else "Tips"
            else -> ""
        }
    }

    /** Returns the page type constant at the given adapter position. */
    fun getPageType(position: Int): Int = pageList.getOrElse(position) { TYPE_HERO }
}
