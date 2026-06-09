package com.movit.feature.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovitOnboardingViewModelTest {

    @Test
    fun stepValidation_ageGenderRequiresFields() {
        val data = OnboardingData()
        assertFalse(data.isStepValid(OnboardingData.STEP_AGE_GENDER))
        val valid = data.copy(ageYears = 28, biologicalSex = "male")
        assertTrue(valid.isStepValid(OnboardingData.STEP_AGE_GENDER))
    }

    @Test
    fun stepValidation_experienceRequiresTierAndDays() {
        val data = OnboardingData(resistanceExperience = "beginner")
        assertFalse(data.isStepValid(OnboardingData.STEP_EXPERIENCE))
        val valid = data.copy(targetDaysPerWeek = 3)
        assertTrue(valid.isStepValid(OnboardingData.STEP_EXPERIENCE))
    }

    @Test
    fun viewModel_blocksContinueWhenStepInvalid() {
        val viewModel = MovitOnboardingViewModel()
        assertFalse(viewModel.state.value.canContinue)
        viewModel.onEvent(MovitOnboardingEvent.AgeChanged("28"))
        viewModel.onEvent(MovitOnboardingEvent.SexSelected("male"))
        assertTrue(viewModel.state.value.canContinue)
    }

    @Test
    fun viewModel_advancesStepWhenValid() {
        val viewModel = MovitOnboardingViewModel()
        viewModel.onEvent(MovitOnboardingEvent.AgeChanged("28"))
        viewModel.onEvent(MovitOnboardingEvent.SexSelected("male"))
        viewModel.onEvent(MovitOnboardingEvent.ContinueClicked)
        assertEquals(OnboardingData.STEP_BODY_METRICS, viewModel.state.value.step)
    }
}
