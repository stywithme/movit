package com.movit.feature.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingDataTest {

    @Test
    fun ageValidation_rejectsOutOfRange() {
        assertFalse(OnboardingData(ageYears = 12, biologicalSex = "male").isStepValid(OnboardingData.STEP_AGE_GENDER))
        assertFalse(OnboardingData(ageYears = 91, biologicalSex = "male").isStepValid(OnboardingData.STEP_AGE_GENDER))
        assertTrue(OnboardingData(ageYears = 28, biologicalSex = "male").isStepValid(OnboardingData.STEP_AGE_GENDER))
    }

    @Test
    fun bodyMetricsValidation_enforcesLegacyRanges() {
        val invalidHeight = OnboardingData(heightCm = 100f, weightKg = 70f)
        assertFalse(invalidHeight.isStepValid(OnboardingData.STEP_BODY_METRICS))

        val invalidWeight = OnboardingData(heightCm = 170f, weightKg = 25f)
        assertFalse(invalidWeight.isStepValid(OnboardingData.STEP_BODY_METRICS))

        val valid = OnboardingData(heightCm = 175f, weightKg = 78f)
        assertTrue(valid.isStepValid(OnboardingData.STEP_BODY_METRICS))
    }

    @Test
    fun locationStep_requiresEquipmentForHome() {
        val homeEmpty = OnboardingData(trainingLocation = "home")
        assertFalse(homeEmpty.isStepValid(OnboardingData.STEP_LOCATION))

        val homeWithGear = OnboardingData(trainingLocation = "home", availableEquipment = setOf("dumbbell"))
        assertTrue(homeWithGear.isStepValid(OnboardingData.STEP_LOCATION))

        val gym = OnboardingData(trainingLocation = "gym")
        assertTrue(gym.isStepValid(OnboardingData.STEP_LOCATION))
    }

    @Test
    fun withHomeLocationDefaults_addsBodyweightLikeLegacy() {
        val updated = OnboardingData(trainingLocation = "home").withHomeLocationDefaults()
        assertTrue(updated.availableEquipment.contains("bodyweight"))
    }

    @Test
    fun toTrainingProfileRequest_matchesLegacyPayloadShape() {
        val data = OnboardingData(
            ageYears = 28,
            biologicalSex = "male",
            heightCm = 175f,
            weightKg = 78f,
            resistanceExperience = "beginner",
            targetDaysPerWeek = 3,
            trainingGoal = "STRENGTH",
            trainingWeekdays = setOf(2, 4, 6),
            trainingLocation = "home",
            availableEquipment = setOf("dumbbell"),
            healthDisclaimerAccepted = true,
        )

        val request = data.toTrainingProfileRequest()

        assertEquals("male", request.biologicalSex)
        assertEquals(175f, request.heightCm)
        assertEquals(78f, request.weightKg)
        assertEquals("beginner", request.resistanceExperience)
        assertEquals(0, request.trainingExperienceMonths)
        assertEquals(listOf(2, 4, 6), request.trainingWeekdays)
        assertEquals(3, request.availableDaysPerWeek)
        assertEquals("home", request.trainingLocation)
        assertEquals(listOf("dumbbell", "bodyweight"), request.availableEquipment)
        assertEquals("STRENGTH", request.trainingGoal)
        assertTrue(request.healthDisclaimerAccepted)
        assertTrue(request.dateOfBirth!!.endsWith("-01-01"))
    }

    @Test
    fun gymLocation_usesFullEquipmentList() {
        val data = OnboardingData(
            trainingLocation = "gym",
            availableEquipment = setOf("dumbbell"),
        )
        val request = data.toTrainingProfileRequest()
        assertEquals(OnboardingData.GYM_EQUIPMENT, request.availableEquipment)
    }

    @Test
    fun availableDaysPerWeek_fallsBackToTargetWhenNoWeekdays() {
        val data = OnboardingData(
            targetDaysPerWeek = 4,
            trainingWeekdays = emptySet(),
        )
        assertEquals(4, data.toTrainingProfileRequest().availableDaysPerWeek)
    }

    @Test
    fun stepErrorKey_returnsNullWhenValid() {
        val data = OnboardingData(ageYears = 28, biologicalSex = "female")
        assertNull(data.stepErrorKey(OnboardingData.STEP_AGE_GENDER))
    }

    @Test
    fun stepErrorKey_returnsAgeRangeKey() {
        val data = OnboardingData(ageYears = 5, biologicalSex = "female")
        assertEquals("onboarding_error_age_range", data.stepErrorKey(OnboardingData.STEP_AGE_GENDER))
    }
}
