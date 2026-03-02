package com.trainingvalidator.poc.ui.exercises

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.ui.exercises.ExercisesFragment

/**
 * Hosts `ExercisesFragment` as a standalone screen for Explore -> See All.
 */
class ExerciseListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_container)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ExercisesFragment())
                .commit()
        }
    }
}