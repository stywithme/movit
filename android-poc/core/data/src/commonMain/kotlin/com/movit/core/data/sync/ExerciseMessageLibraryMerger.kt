package com.movit.core.data.sync

import com.movit.core.network.dto.LocalizedNameDto
import com.movit.core.network.dto.SyncMessageTemplateDto
import com.movit.core.training.config.ExerciseConfig
import com.movit.core.training.config.ExerciseConfigRecord
import com.movit.core.training.config.FeedbackMessages
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.config.PoseVariant
import com.movit.core.training.config.StateMessageValue
import com.movit.core.training.config.StateMessages

/**
 * Expands [MessageAssignment] references using the sync message library before caching exercise configs.
 * Mirrors legacy [com.movit.storage.SyncManager.resolveExerciseMessages].
 */
internal object ExerciseMessageLibraryMerger {

    fun resolveRecords(
        records: List<ExerciseConfigRecord>,
        messageLibrary: List<SyncMessageTemplateDto>,
    ): List<ExerciseConfigRecord> {
        if (messageLibrary.isEmpty()) return records
        val messageMap = messageLibrary.associateBy { it.id }
        return records.map { record ->
            val config = record.config
            val variants = config.poseVariants
            if (variants.isEmpty()) return@map record
            val resolvedVariants = variants.map { variant ->
                resolvePoseVariantMessages(variant, messageMap)
            }
            record.copy(config = config.copy(poseVariants = resolvedVariants))
        }
    }

    fun resolveExerciseConfigs(
        exercises: List<ExerciseConfig>,
        messageLibrary: List<SyncMessageTemplateDto>,
    ): List<ExerciseConfig> {
        if (messageLibrary.isEmpty()) return exercises
        val messageMap = messageLibrary.associateBy { it.id }
        return exercises.map { exercise ->
            val variants = exercise.poseVariants
            if (variants.isEmpty()) return@map exercise
            exercise.copy(
                poseVariants = variants.map { resolvePoseVariantMessages(it, messageMap) },
            )
        }
    }

    private fun resolvePoseVariantMessages(
        variant: PoseVariant,
        messageMap: Map<String, SyncMessageTemplateDto>,
    ): PoseVariant {
        val assignments = variant.messageAssignments.sortedBy { it.sortOrder }
        if (assignments.isEmpty()) return variant

        val motivational = variant.feedbackMessages.motivational.toMutableList()
        val tips = variant.feedbackMessages.tips.toMutableList()

        val trackedJoints = variant.trackedJoints
        val jointMessageMap: MutableMap<String, StateMessages?> =
            trackedJoints.associate { it.joint to it.stateMessages }.toMutableMap()
        val phaseMessageByJoint = mutableMapOf<String, MutableMap<String, StateMessages?>>()
        trackedJoints.forEach { joint ->
            val perPhase = mutableMapOf<String, StateMessages?>()
            joint.phaseStateMessages?.forEach { (phase, messages) -> perPhase[phase] = messages }
            phaseMessageByJoint[joint.joint] = perPhase
        }
        val positionMessageMap = mutableMapOf<String, LocalizedText>()

        for (assignment in assignments) {
            val template = messageMap[assignment.messageId] ?: continue
            val content = template.content.toLocalizedText()

            when (assignment.target) {
                "feedback" -> when (assignment.context) {
                    "motivational" -> motivational.add(content)
                    "tip" -> tips.add(content)
                }

                "joint_state" -> {
                    val jointCode = assignment.jointCode ?: continue
                    val stateKey = assignment.context ?: continue
                    val current = jointMessageMap[jointCode]
                    jointMessageMap[jointCode] = applyStateMessage(
                        current,
                        stateKey,
                        assignment.zone,
                        content,
                    )
                }

                "joint_state_phase" -> {
                    val jointCode = assignment.jointCode ?: continue
                    val ctx = assignment.context ?: continue
                    val idx = ctx.indexOf(':')
                    if (idx <= 0) continue
                    val phaseRaw = ctx.substring(0, idx)
                    val stateKey = ctx.substring(idx + 1)
                    val phase = normalizeSecondaryPhaseKey(phaseRaw) ?: continue
                    val perJoint = phaseMessageByJoint.getOrPut(jointCode) { mutableMapOf() }
                    val cur = perJoint[phase]
                    perJoint[phase] = applyStateMessage(cur, stateKey, assignment.zone, content)
                }

                "position" -> {
                    val checkId = assignment.checkId ?: continue
                    positionMessageMap[checkId] = content
                }
            }
        }

        val updatedTrackedJoints = trackedJoints.map { joint ->
            val updatedMessages = jointMessageMap[joint.joint]
            val finalMessages = updatedMessages ?: joint.stateMessages
            val phaseUpdates = phaseMessageByJoint[joint.joint]
            val finalPhase = if (!phaseUpdates.isNullOrEmpty()) {
                val base = (joint.phaseStateMessages ?: emptyMap()).toMutableMap()
                phaseUpdates.forEach { (phase, messages) ->
                    if (messages != null) base[phase] = messages
                }
                base
            } else {
                joint.phaseStateMessages
            }
            val stateChanged = finalMessages != joint.stateMessages
            val phaseChanged = finalPhase != joint.phaseStateMessages
            if (stateChanged || phaseChanged) {
                joint.copy(stateMessages = finalMessages, phaseStateMessages = finalPhase)
            } else {
                joint
            }
        }

        val updatedPositionChecks = variant.positionChecks.map { check ->
            val message = positionMessageMap[check.id]
            if (message != null) check.copy(errorMessage = message) else check
        }

        val updatedFeedback = if (motivational.isNotEmpty() || tips.isNotEmpty()) {
            FeedbackMessages(motivational = motivational, tips = tips)
        } else {
            variant.feedbackMessages
        }

        return variant.copy(
            trackedJoints = updatedTrackedJoints,
            positionChecks = updatedPositionChecks,
            feedbackMessages = updatedFeedback,
        )
    }

    private fun normalizeSecondaryPhaseKey(raw: String): String? {
        val lower = raw.trim().lowercase()
        val migrated = when (lower) {
            "start" -> "top"
            "hold", "count" -> "count"
            "idle" -> "all"
            else -> lower
        }
        if (migrated == "all") return null
        if (migrated in listOf("top", "down", "bottom", "up")) return migrated
        return null
    }

    private fun applyStateMessage(
        current: StateMessages?,
        stateKey: String,
        zone: String?,
        message: LocalizedText,
    ): StateMessages {
        val existing = current ?: StateMessages()
        val normalizedState = normalizeStateMessageKey(stateKey) ?: return existing
        val normalizedZone = zone?.trim()?.lowercase()
        val updatedValue = mergeStateMessageValue(
            when (normalizedState) {
                "perfect" -> existing.perfect
                "normal" -> existing.normal
                "pad" -> existing.pad
                "warning" -> existing.warning
                "danger" -> existing.danger
                else -> null
            },
            normalizedZone,
            message,
        )

        return when (normalizedState) {
            "perfect" -> existing.copy(perfect = updatedValue)
            "normal" -> existing.copy(normal = updatedValue)
            "pad" -> existing.copy(pad = updatedValue)
            "warning" -> existing.copy(warning = updatedValue)
            "danger" -> existing.copy(danger = updatedValue)
            else -> existing
        }
    }

    private fun mergeStateMessageValue(
        existing: StateMessageValue?,
        zone: String?,
        message: LocalizedText,
    ): StateMessageValue {
        if (zone.isNullOrBlank()) {
            return StateMessageValue.Single(message)
        }

        val current = existing as? StateMessageValue.ZoneSpecific
            ?: StateMessageValue.ZoneSpecific()

        return when (zone) {
            "down" -> current.copy(down = message)
            else -> current.copy(up = message)
        }
    }

    private fun normalizeStateMessageKey(raw: String): String? = when (raw.trim().lowercase()) {
        "perfect" -> "perfect"
        "normal", "good" -> "normal"
        "pad", "accept", "acceptable" -> "pad"
        "warning" -> "warning"
        "danger" -> "danger"
        else -> null
    }

    private fun LocalizedNameDto.toLocalizedText(): LocalizedText =
        LocalizedText(en = en, ar = ar)
}
