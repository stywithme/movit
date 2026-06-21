package com.movit.core.training.bilateral

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BilateralControllerParityTest {

    @Test
    fun nonBilateral_neverFlips() {
        val controller = BilateralController(
            isBilateral = false,
            config = BilateralConfigInput(startSide = "left"),
            targetReps = 10,
        )
        controller.onRepCounted(1)
        controller.onRepCounted(2)
        assertEquals(BilateralSide.LEFT, controller.currentSide)
        assertFalse(controller.isFlipped)
    }

    @Test
    fun everyRep_flipsAfterEachCountedRep() {
        val controller = BilateralController(
            isBilateral = true,
            config = BilateralConfigInput(switchMode = BilateralSwitchMode.EVERY_REP, startSide = "right"),
            targetReps = 12,
        )
        assertEquals(BilateralSide.RIGHT, controller.currentSide)
        controller.onRepCounted(1)
        assertEquals(BilateralSide.LEFT, controller.currentSide)
        assertTrue(controller.isFlipped)
        controller.onRepCounted(2)
        assertEquals(BilateralSide.RIGHT, controller.currentSide)
    }

    @Test
    fun afterAllReps_flipsOnlyWhenTargetReached() {
        val controller = BilateralController(
            isBilateral = true,
            config = BilateralConfigInput(
                switchMode = BilateralSwitchMode.AFTER_ALL_REPS,
                startSide = "right",
            ),
            targetReps = 3,
        )
        controller.onRepCounted(1)
        assertEquals(BilateralSide.RIGHT, controller.currentSide)
        controller.onRepCounted(2)
        assertEquals(BilateralSide.RIGHT, controller.currentSide)
        controller.onRepCounted(3)
        assertEquals(BilateralSide.LEFT, controller.currentSide)
    }

    @Test
    fun legacySwitchEvery_respectsInterval() {
        val controller = BilateralController(
            isBilateral = true,
            config = BilateralConfigInput(switchEvery = 2, startSide = "left"),
            targetReps = 10,
        )
        controller.onRepCounted(1)
        assertEquals(BilateralSide.LEFT, controller.currentSide)
        controller.onRepCounted(2)
        assertEquals(BilateralSide.RIGHT, controller.currentSide)
    }

    @Test
    fun resetToConfigStart_restoresStartSide() {
        val sides = mutableListOf<BilateralSide>()
        val controller = BilateralController(
            isBilateral = true,
            config = BilateralConfigInput(switchMode = BilateralSwitchMode.EVERY_REP, startSide = "left"),
            targetReps = 10,
        )
        controller.onSideChanged = { sides += it }
        controller.onRepCounted(1)
        controller.resetToConfigStart()
        assertEquals(BilateralSide.LEFT, controller.currentSide)
        assertEquals(listOf(BilateralSide.RIGHT, BilateralSide.LEFT), sides)
    }

    @Test
    fun currentSideCode_isLowercaseName() {
        val controller = BilateralController(
            isBilateral = true,
            config = BilateralConfigInput(startSide = "left"),
            targetReps = 5,
        )
        assertEquals("left", controller.currentSideCode)
    }
}
