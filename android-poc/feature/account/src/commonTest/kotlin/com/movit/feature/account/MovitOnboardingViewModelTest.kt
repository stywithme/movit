package com.movit.feature.account

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun viewModel_setsValidationErrorWhenContinueBlocked() {
        val viewModel = MovitOnboardingViewModel()
        viewModel.onEvent(MovitOnboardingEvent.ContinueClicked)
        assertEquals("onboarding_error_age_required", viewModel.state.value.validationErrorKey)
    }

    @Test
    fun viewModel_appliesBodyMetricDefaultsOnAdvance() {
        val viewModel = MovitOnboardingViewModel()
        fillAgeGender(viewModel)
        viewModel.onEvent(MovitOnboardingEvent.ContinueClicked)
        assertEquals(OnboardingData.DEFAULT_HEIGHT_CM, viewModel.state.value.data.heightCm)
        assertEquals(OnboardingData.DEFAULT_WEIGHT_KG, viewModel.state.value.data.weightKg)
    }

    @Test
    fun viewModel_homeLocationAutoAddsBodyweight() {
        val viewModel = MovitOnboardingViewModel()
        advanceToLocationStep(viewModel)
        viewModel.onEvent(MovitOnboardingEvent.LocationSelected("home"))
        assertTrue(viewModel.state.value.data.availableEquipment.contains("bodyweight"))
    }

    @Test
    fun viewModel_submitFailureThenRetry() = runBlocking {
        val viewModel = MovitOnboardingViewModel(
            repository = FakeOnboardingRepository(shouldFail = true),
        )
        fillCompleteProfile(viewModel)
        viewModel.onEvent(MovitOnboardingEvent.ContinueClicked)
        delay(50)
        assertNotNull(viewModel.state.value.submitErrorMessage)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun viewModel_submitSuccessClearsSubmitting() = runBlocking {
        val viewModel = MovitOnboardingViewModel(repository = FakeOnboardingRepository())
        fillCompleteProfile(viewModel)
        viewModel.onEvent(MovitOnboardingEvent.ContinueClicked)
        delay(50)
        assertFalse(viewModel.state.value.isSubmitting)
        assertNull(viewModel.state.value.submitErrorMessage)
    }

    private fun fillAgeGender(viewModel: MovitOnboardingViewModel) {
        viewModel.onEvent(MovitOnboardingEvent.AgeChanged("28"))
        viewModel.onEvent(MovitOnboardingEvent.SexSelected("male"))
    }

    private fun advanceToLocationStep(viewModel: MovitOnboardingViewModel) {
        fillAgeGender(viewModel)
        viewModel.onEvent(MovitOnboardingEvent.ContinueClicked)
        viewModel.onEvent(MovitOnboardingEvent.ContinueClicked)
        viewModel.onEvent(MovitOnboardingEvent.ExperienceSelected("beginner"))
        viewModel.onEvent(MovitOnboardingEvent.ContinueClicked)
        viewModel.onEvent(MovitOnboardingEvent.GoalSelected("STRENGTH"))
        viewModel.onEvent(MovitOnboardingEvent.ContinueClicked)
        viewModel.onEvent(MovitOnboardingEvent.WeekdayToggled(2))
        viewModel.onEvent(MovitOnboardingEvent.ContinueClicked)
    }

    private fun fillCompleteProfile(viewModel: MovitOnboardingViewModel) {
        advanceToLocationStep(viewModel)
        viewModel.onEvent(MovitOnboardingEvent.LocationSelected("gym"))
        viewModel.onEvent(MovitOnboardingEvent.ContinueClicked)
        viewModel.onEvent(MovitOnboardingEvent.DisclaimerChanged(true))
    }
}
