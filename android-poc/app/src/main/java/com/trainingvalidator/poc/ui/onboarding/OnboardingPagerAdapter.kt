package com.trainingvalidator.poc.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.trainingvalidator.poc.ui.onboarding.steps.StepAgeGenderFragment
import com.trainingvalidator.poc.ui.onboarding.steps.StepBodyMetricsFragment
import com.trainingvalidator.poc.ui.onboarding.steps.StepExperienceFragment
import com.trainingvalidator.poc.ui.onboarding.steps.StepGoalFragment
import com.trainingvalidator.poc.ui.onboarding.steps.StepLocationEquipmentFragment
import com.trainingvalidator.poc.ui.onboarding.steps.StepSummaryFragment
import com.trainingvalidator.poc.ui.onboarding.steps.StepWeekdaysFragment

class OnboardingPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = OnboardingViewModel.STEP_COUNT

    override fun createFragment(position: Int): Fragment = when (position) {
        OnboardingViewModel.STEP_AGE_GENDER -> StepAgeGenderFragment()
        OnboardingViewModel.STEP_BODY_METRICS -> StepBodyMetricsFragment()
        OnboardingViewModel.STEP_EXPERIENCE -> StepExperienceFragment()
        OnboardingViewModel.STEP_GOAL -> StepGoalFragment()
        OnboardingViewModel.STEP_WEEKDAYS -> StepWeekdaysFragment()
        OnboardingViewModel.STEP_LOCATION -> StepLocationEquipmentFragment()
        OnboardingViewModel.STEP_SUMMARY -> StepSummaryFragment()
        else -> StepSummaryFragment()
    }
}
