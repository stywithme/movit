package com.movit.shared.training

import kotlin.test.Test
import kotlin.test.assertEquals

class MovitTrainingAnalyticsTest {
    @Test
    fun trackStartWorkout_recordsEventNameAndParams() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val prior = MovitTrainingAnalytics.sink
        try {
            MovitTrainingAnalytics.sink = { name, params -> events += name to params }
            MovitTrainingAnalytics.trackStartWorkout("w1", source = "train")
            assertEquals(1, events.size)
            assertEquals("training_start_workout", events[0].first)
            assertEquals("w1", events[0].second["workout_id"])
            assertEquals("train", events[0].second["source"])
        } finally {
            MovitTrainingAnalytics.sink = prior
        }
    }
}
