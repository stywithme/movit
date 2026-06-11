package com.trainingvalidator.poc.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityMainContainerBinding
import com.trainingvalidator.poc.ui.explore.ExploreFragment
import com.trainingvalidator.poc.ui.home.HomeFragment
import com.trainingvalidator.poc.ui.reports.HistoryFragment
import com.trainingvalidator.poc.ui.programs.TrainFragment

/**
 * MainContainerActivity - Main app container with Bottom Navigation
 * 
 * Contains 4 tabs:
 * - Home
 * - Train
 * - Explore
 * - Reports
 */
class MainContainerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_START_TAB = "extra_start_tab"
    }

    private lateinit var binding: ActivityMainContainerBinding

    private lateinit var homeFragment: HomeFragment
    private lateinit var trainFragment: TrainFragment
    private lateinit var exploreFragment: ExploreFragment
    private lateinit var historyFragment: HistoryFragment

    private lateinit var activeFragment: Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        if (savedInstanceState == null) {
            homeFragment = HomeFragment()
            trainFragment = TrainFragment()
            exploreFragment = ExploreFragment()
            historyFragment = HistoryFragment()
            activeFragment = homeFragment
            setupFragments()
        } else {
            // Recover existing fragment instances to avoid duplication on config change
            homeFragment = supportFragmentManager.findFragmentByTag("home") as? HomeFragment ?: HomeFragment()
            trainFragment = supportFragmentManager.findFragmentByTag("train") as? TrainFragment ?: TrainFragment()
            exploreFragment = supportFragmentManager.findFragmentByTag("explore") as? ExploreFragment ?: ExploreFragment()
            historyFragment = supportFragmentManager.findFragmentByTag("reports") as? HistoryFragment ?: HistoryFragment()
            activeFragment = listOf(homeFragment, trainFragment, exploreFragment, historyFragment)
                .firstOrNull { !it.isHidden } ?: homeFragment
        }
        
        setupBottomNavigation()
    }

    private fun setupFragments() {
        // Add all fragments but hide all except home
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragmentContainer, homeFragment, "home")
            add(R.id.fragmentContainer, trainFragment, "train").hide(trainFragment)
            add(R.id.fragmentContainer, exploreFragment, "explore").hide(exploreFragment)
            add(R.id.fragmentContainer, historyFragment, "reports").hide(historyFragment)
        }.commit()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val targetFragment = when (item.itemId) {
                R.id.nav_home -> homeFragment
                R.id.nav_train -> trainFragment
                R.id.nav_explore -> exploreFragment
                R.id.nav_reports -> historyFragment
                else -> return@setOnItemSelectedListener false
            }
            
            if (targetFragment != activeFragment) {
                supportFragmentManager.beginTransaction().apply {
                    hide(activeFragment)
                    show(targetFragment)
                    setReorderingAllowed(true)
                }.commit()
                
                activeFragment = targetFragment
            }
            true
        }
        
        // Check if a specific tab was requested (e.g., after language change)
        val startTab = intent.getIntExtra(EXTRA_START_TAB, R.id.nav_home)
        binding.bottomNavigation.selectedItemId = startTab
    }

    /**
     * Navigate to a specific tab programmatically
     */
    fun navigateToTab(tabId: Int) {
        // Map old tab IDs to new ones if necessary
        val effectiveTabId = when (tabId) {
            R.id.nav_home -> R.id.nav_home
            R.id.nav_programs, R.id.nav_train -> R.id.nav_train // map legacy programs to train
            R.id.nav_exercises, R.id.nav_explore -> R.id.nav_explore // map legacy exercises to explore
            R.id.nav_history, R.id.nav_reports -> R.id.nav_reports // map legacy history to reports
            else -> tabId
        }
        binding.bottomNavigation.selectedItemId = effectiveTabId
    }
}
