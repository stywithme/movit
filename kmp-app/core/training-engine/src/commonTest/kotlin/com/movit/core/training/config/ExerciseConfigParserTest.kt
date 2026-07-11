package com.movit.core.training.config

import com.movit.core.training.engine.CountingMethod
import com.movit.core.training.testing.readExerciseFixture
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseConfigParserTest {

    @Test
    fun squatFixture_parsesPrimaryJointsAndCounting() {
        val json = readExerciseFixture("squat.json")
        val config = ExerciseConfigParser.parseConfigJson(json)
        assertEquals(CountingMethod.UP_DOWN, config.countingMethod)
        assertTrue(config.getPrimaryJoints().size >= 2)
        assertTrue(config.validationIssues().isEmpty())
    }

    @Test
    fun plankFixture_parsesHoldExercise() {
        val json = readExerciseFixture("plank.json")
        val config = ExerciseConfigParser.parseConfigJson(json)
        assertEquals(CountingMethod.HOLD, config.countingMethod)
        assertTrue(config.isHoldExercise())
    }

    @Test
    fun bilateralFixture_parsesBilateralFlag() {
        val json = readExerciseFixture("bilateral-shoulder-mobility.json")
        val config = ExerciseConfigParser.parseConfigJson(json)
        assertTrue(config.isBilateral)
    }

    @Test
    fun positionChecksFixture_hasPositionChecks() {
        val json = readExerciseFixture("position-checks-desk.json")
        val config = ExerciseConfigParser.parseConfigJson(json)
        assertTrue(config.hasAnyPositionChecks())
        assertTrue(config.poseVariants.first().positionChecks.isNotEmpty())
    }

    @Test
    fun emptyPoseVariants_validationIssuesNonEmpty() {
        val config = ExerciseConfig(
            name = LocalizedText(en = "Broken"),
            poseVariants = emptyList(),
        )
        val issues = config.validationIssues()
        assertTrue(issues.isNotEmpty())
        assertTrue(issues.any { it.contains("poseVariants") })
    }

    @Test
    fun ghostJoint_unknownCode_validationIssuesNonEmpty() {
        val config = ExerciseConfig(
            name = LocalizedText(en = "Ghost"),
            poseVariants = listOf(
                PoseVariant(
                    trackedJoints = listOf(
                        TrackedJoint(joint = "left_toe", role = JointRole.PRIMARY),
                    ),
                ),
            ),
        )
        val issues = config.validationIssues()
        assertTrue(issues.any { it.contains("left_toe") && it.contains("no computed angle source") })
    }

    @Test
    fun spineJoint_afterWp15b_validationIssuesEmpty() {
        val config = ExerciseConfig(
            name = LocalizedText(en = "Spine"),
            poseVariants = listOf(
                PoseVariant(
                    trackedJoints = listOf(
                        TrackedJoint(joint = "spine", role = JointRole.PRIMARY),
                    ),
                ),
            ),
        )
        assertTrue(config.validationIssues().isEmpty())
    }

    @Test
    fun tenComputedLimbJoints_validationIssuesEmpty() {
        val codes = listOf(
            "left_elbow", "right_elbow",
            "left_shoulder", "right_shoulder",
            "left_hip", "right_hip",
            "left_knee", "right_knee",
            "left_ankle", "right_ankle",
        )
        val config = ExerciseConfig(
            name = LocalizedText(en = "Limbs"),
            poseVariants = listOf(
                PoseVariant(
                    trackedJoints = codes.map { TrackedJoint(joint = it, role = JointRole.PRIMARY) },
                ),
            ),
        )
        assertTrue(config.validationIssues().isEmpty())
    }

    @Test
    fun sanitizeDefaults_flipsInvertedAngleRanges_andKeepsEmptyVariants() {
        val config = ExerciseConfig(
            name = LocalizedText(en = "Sanitize"),
            poseVariants = listOf(
                PoseVariant(), // empty → kept; validationIssues catches at buildEngine
                PoseVariant(
                    trackedJoints = listOf(
                        TrackedJoint(
                            joint = "left_knee",
                            role = JointRole.PRIMARY,
                            startPose = AngleRange(min = 180.0, max = 150.0),
                            upRange = StateRanges(perfect = AngleRange(min = 100.0, max = 90.0)),
                        ),
                    ),
                ),
            ),
        )
        val sanitized = config.sanitizeDefaults()
        assertEquals(2, sanitized.poseVariants.size)
        val joint = sanitized.poseVariants[1].trackedJoints.single()
        assertEquals(150.0, joint.startPose.min)
        assertEquals(180.0, joint.startPose.max)
        assertEquals(90.0, joint.upRange!!.perfect.min)
        assertEquals(100.0, joint.upRange!!.perfect.max)
    }

    @Test
    fun parseRecords_dropsMalformedAndKeepsValid() {
        val valid = buildJsonObject {
            put("id", JsonPrimitive("1"))
            put("slug", JsonPrimitive("ok-squat"))
            put("name", buildJsonObject {
                put("en", JsonPrimitive("Ok"))
                put("ar", JsonPrimitive("Ok"))
            })
            put("poseVariants", kotlinx.serialization.json.JsonArray(emptyList()))
        }
        val malformed = JsonPrimitive("not-an-object")
        val records = ExerciseConfigParser.parseRecords(listOf(malformed, valid))
        assertEquals(1, records.size)
        assertEquals("ok-squat", records.single().slug)
    }

}
