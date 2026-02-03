package com.trainingvalidator.poc.ui.report

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.trainingvalidator.poc.training.report.PostTrainingReport

/**
 * RepPagerAdapter - Adapter for horizontal swiping between report pages
 * 
 * Page structure:
 * - Page 0: Exercise Summary (best frame + overall stats)
 * - Page 1: Reps Journey (chart of all reps)
 * - Page 2: Key Moments (best/worst comparison)
 * - Page 3: Tips (improvement suggestions)
 * - Page 4+: Individual rep details
 */
class RepPagerAdapter(
    activity: FragmentActivity,
    private val report: PostTrainingReport,
    private val isArabic: Boolean
) : FragmentStateAdapter(activity) {
    
    companion object {
        // Page type constants
        private const val TYPE_SUMMARY = 0
        private const val TYPE_JOURNEY = 1
        private const val TYPE_KEY_MOMENTS = 2
        private const val TYPE_TIPS = 3
        private const val TYPE_REP = 4
    }
    
    private val repCount = report.repTimeline.size
    
    // Determine which fixed pages to show based on data
    private val showJourney = repCount >= 2 // Need at least 2 reps for a journey
    private val showKeyMoments = report.bestReps.isNotEmpty()
    private val showTips = true // Always show (shows "perfect" message if no tips)
    
    // Build the page list dynamically as (type, repIndex)
    private val pageList: List<Pair<Int, Int>> = buildList {
        add(TYPE_SUMMARY to -1)
        if (showJourney) add(TYPE_JOURNEY to -1)
        if (showKeyMoments) add(TYPE_KEY_MOMENTS to -1)
        if (showTips) add(TYPE_TIPS to -1)
        // Add individual reps
        for (i in 0 until repCount) {
            add(TYPE_REP to i)
        }
    }
    
    override fun getItemCount(): Int = pageList.size
    
    override fun createFragment(position: Int): Fragment {
        val (pageType, repIndex) = pageList[position]
        
        val fragment: Fragment = when (pageType) {
            TYPE_SUMMARY -> createSummaryFragment(position)
            TYPE_JOURNEY -> createJourneyFragment()
            TYPE_KEY_MOMENTS -> createKeyMomentsFragment()
            TYPE_TIPS -> createTipsFragment()
            TYPE_REP -> createRepFragment(repIndex, position)
            else -> createSummaryFragment(position)
        }
        
        return fragment
    }
    
    private fun createSummaryFragment(position: Int): Fragment {
        val fragment = ReportPageFragment.newSummaryInstance()
        fragment.setData(report, isArabic, itemCount, position)
        return fragment
    }
    
    private fun createJourneyFragment(): Fragment {
        val fragment = RepsJourneyFragment.newInstance()
        fragment.setData(report, isArabic)
        return fragment
    }
    
    private fun createKeyMomentsFragment(): Fragment {
        val fragment = KeyMomentsFragment.newInstance()
        fragment.setData(report, isArabic)
        return fragment
    }
    
    private fun createTipsFragment(): Fragment {
        val fragment = TipsFragment.newInstance()
        fragment.setData(report, isArabic)
        return fragment
    }
    
    private fun createRepFragment(repIndex: Int, position: Int): Fragment {
        val fragment = ReportPageFragment.newRepInstance(repIndex)
        fragment.setData(report, isArabic, itemCount, position)
        return fragment
    }
    
    /**
     * Get page title for accessibility and indicators
     */
    fun getPageTitle(position: Int): String {
        val (pageType, repIndex) = pageList[position]
        
        return when (pageType) {
            TYPE_SUMMARY -> if (isArabic) "ملخص التمرين" else "Exercise Summary"
            TYPE_JOURNEY -> if (isArabic) "رحلة العدات" else "Reps Journey"
            TYPE_KEY_MOMENTS -> if (isArabic) "لحظات مميزة" else "Key Moments"
            TYPE_TIPS -> if (isArabic) "نصائح" else "Tips"
            TYPE_REP -> {
                val repNum = report.repTimeline.getOrNull(repIndex)?.repNumber ?: (repIndex + 1)
                if (isArabic) "العدة #$repNum" else "Rep #$repNum"
            }
            else -> ""
        }
    }
    
    /**
     * Get the index of the first rep page
     */
    fun getFirstRepPageIndex(): Int {
        return pageList.indexOfFirst { it.first == TYPE_REP }.takeIf { it >= 0 } ?: itemCount
    }
    
    /**
     * Check if a position is a rep page
     */
    fun isRepPage(position: Int): Boolean {
        return pageList.getOrNull(position)?.first == TYPE_REP
    }
}
