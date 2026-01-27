package com.trainingvalidator.poc.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityMainContainerBinding

/**
 * MainContainerActivity - Main app container with Bottom Navigation
 * 
 * Contains 4 tabs:
 * - Home
 * - Exercises
 * - History
 * - Profile
 */
class MainContainerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_START_TAB = "extra_start_tab"
    }

    private lateinit var binding: ActivityMainContainerBinding

    // Fragment instances (lazy)
    private val homeFragment by lazy { HomeFragment() }
    private val exercisesFragment by lazy { ExercisesFragment() }
    private val historyFragment by lazy { HistoryFragment() }
    private val profileFragment by lazy { ProfileFragment() }

    private var activeFragment: Fragment = homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityMainContainerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupFragments()
        setupBottomNavigation()
    }

    private fun setupFragments() {
        // Add all fragments but hide all except the active one
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragmentContainer, homeFragment, "home")
            add(R.id.fragmentContainer, exercisesFragment, "exercises").hide(exercisesFragment)
            add(R.id.fragmentContainer, historyFragment, "history").hide(historyFragment)
            add(R.id.fragmentContainer, profileFragment, "profile").hide(profileFragment)
        }.commit()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val targetFragment = when (item.itemId) {
                R.id.nav_home -> homeFragment
                R.id.nav_exercises -> exercisesFragment
                R.id.nav_history -> historyFragment
                R.id.nav_profile -> profileFragment
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
        binding.bottomNavigation.selectedItemId = tabId
    }
}
