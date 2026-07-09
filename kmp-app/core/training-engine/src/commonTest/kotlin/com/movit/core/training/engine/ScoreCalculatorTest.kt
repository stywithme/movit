package com.movit.core.training.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScoreCalculatorTest {

    @Test
    fun getScoreRate_matchesLegacyRates() {
        assertEquals(100f, ScoreCalculator.getScoreRate(JointState.PERFECT))
        assertEquals(80f, ScoreCalculator.getScoreRate(JointState.NORMAL))
        assertEquals(60f, ScoreCalculator.getScoreRate(JointState.PAD))
        assertEquals(40f, ScoreCalculator.getScoreRate(JointState.WARNING))
        assertEquals(0f, ScoreCalculator.getScoreRate(JointState.DANGER))
        assertEquals(80f, ScoreCalculator.getScoreRate(JointState.TRANSITION))
    }

    @Test
    fun calculateRepScore_weightedPrimaryAndSecondary() {
        val states = mapOf(
            "elbow" to JointStateInfo("elbow", JointState.PERFECT, isPrimary = true),
            "knee" to JointStateInfo("knee", JointState.NORMAL, isPrimary = false),
        )
        val result = ScoreCalculator.calculateRepScore(states, setOf("elbow"))
        assertEquals(95.38462f, result.score, absoluteTolerance = 0.01f)
        assertTrue(result.isCounted)
        assertFalse(result.isInvalidated)
        assertEquals(JointState.NORMAL, result.worstState)
    }

    @Test
    fun calculateRepScore_dangerAppliesPenaltyAndInvalidates() {
        val states = mapOf(
            "elbow" to JointStateInfo("elbow", JointState.DANGER, isPrimary = true),
        )
        val result = ScoreCalculator.calculateRepScore(states, setOf("elbow"))
        assertEquals(0f, result.score)
        assertFalse(result.isCounted)
        assertTrue(result.isInvalidated)
        assertEquals(listOf("elbow"), result.dangerJoints)
    }

    @Test
    fun calculateHoldScore_weightedByTimeInStates() {
        val result = ScoreCalculator.calculateHoldScore(
            mapOf(
                JointState.PERFECT to 700L,
                JointState.NORMAL to 300L,
            ),
        )
        assertEquals(94f, result.score, absoluteTolerance = 0.1f)
        assertFalse(result.isInvalidated)
    }

    @Test
    fun calculateHoldScore_transientDangerBelowThresholdDoesNotInvalidate() {
        val result = ScoreCalculator.calculateHoldScore(
            mapOf(JointState.DANGER to 50L, JointState.PERFECT to 950L),
        )
        assertTrue(result.score > 0f)
        assertFalse(result.isInvalidated)
    }

    @Test
    fun calculateHoldScore_sustainedDangerInvalidates() {
        val result = ScoreCalculator.calculateHoldScore(
            mapOf(JointState.DANGER to 300L, JointState.PERFECT to 700L),
        )
        assertEquals(0f, result.score)
        assertTrue(result.isInvalidated)
    }
}
