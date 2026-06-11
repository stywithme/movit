package com.trainingvalidator.poc.training.feedback

import com.trainingvalidator.poc.training.engine.BodyPosture
import com.trainingvalidator.poc.training.engine.ExpectedDirection
import com.trainingvalidator.poc.training.engine.VisibleRegion
import com.trainingvalidator.poc.training.models.LocalizedText

/**
 * Stable keys + defaults for all training/setup copy that should sync from the message library.
 * Defaults match previous hardcoded strings so behavior is unchanged when a key is missing on the server.
 */
object MobileMessageResolver {

    const val TRAINING_NUMERAL_MAX = 30

    fun trainingNumeralKey(n: Int): String? =
        if (n in 1..TRAINING_NUMERAL_MAX) "training_countdown_$n" else null

    /**
     * Spoken digit (countdown) or rep announcement — same numeral keys 1..30.
     */
    fun resolveTrainingNumeral(n: Int): LocalizedText {
        val key = trainingNumeralKey(n) ?: return LocalizedText(ar = "$n", en = "$n")
        return SystemMessageRegistry.get(key, n.toString(), n.toString())
    }

    // --- Scene phase tips (PoseSetupGuide) — include ↻ on direction line ---

    fun directionPhaseKey(dirs: List<ExpectedDirection>): String {
        val parts = dirs
            .filter { it != ExpectedDirection.ANY && directionAr(it) != null }
            .map { it.name }
            .sorted()
        return if (parts.isEmpty()) "setup_direction_any"
        else "setup_direction_" + parts.joinToString("_").lowercase()
    }

    fun resolveDirectionPhaseTip(dirs: List<ExpectedDirection>): LocalizedText {
        val arParts = dirs.mapNotNull { directionAr(it) }
        val enParts = dirs.mapNotNull { directionEn(it) }
        val defAr = "صوّر من ${arParts.joinToString(" أو ")} ↻"
        val defEn = "Film from ${enParts.joinToString(" or ")} ↻"
        return SystemMessageRegistry.get(directionPhaseKey(dirs), defAr, defEn)
    }

    fun posturePhaseKey(postures: List<BodyPosture>): String {
        val parts = postures
            .filter { it != BodyPosture.UNKNOWN && postureAr(it) != null }
            .map { it.name }
            .sorted()
        return if (parts.isEmpty()) "setup_posture_any"
        else "setup_posture_" + parts.joinToString("_").lowercase()
    }

    fun resolvePosturePhaseTip(postures: List<BodyPosture>): LocalizedText {
        val arParts = postures.mapNotNull { postureAr(it) }
        val enParts = postures.mapNotNull { postureEn(it) }
        val defAr = arParts.joinToString(" أو ")
        val defEn = enParts.joinToString(" or ")
        return SystemMessageRegistry.get(posturePhaseKey(postures), defAr, defEn)
    }

    fun regionPhaseKey(regions: List<VisibleRegion>): String {
        val parts = regions
            .filter { it != VisibleRegion.UNKNOWN && regionAr(it) != null }
            .map { it.name }
            .sorted()
        return if (parts.isEmpty()) "setup_region_any"
        else "setup_region_" + parts.joinToString("_").lowercase()
    }

    fun resolveRegionPhaseTip(regions: List<VisibleRegion>): LocalizedText {
        val arParts = regions.mapNotNull { regionAr(it) }
        val enParts = regions.mapNotNull { regionEn(it) }
        val defAr = "أظهر ${arParts.joinToString(" أو ")}"
        val defEn = "Show ${enParts.joinToString(" or ")}"
        return SystemMessageRegistry.get(regionPhaseKey(regions), defAr, defEn)
    }

    /**
     * PositionValidator scene-axis warnings: same semantics as phase tips; direction line without ↻
     * so overlay text matches previous validator copy.
     */
    fun resolveDirectionAxisWarning(dirs: List<ExpectedDirection>): LocalizedText {
        val key = directionAxisKey(dirs)
        val arParts = dirs.mapNotNull { directionAr(it) }
        val enParts = dirs.mapNotNull { directionEn(it) }
        val defAr = "صوّر من ${arParts.joinToString(" أو ")}"
        val defEn = "Film from ${enParts.joinToString(" or ")}"
        return SystemMessageRegistry.get(key, defAr, defEn)
    }

    private fun directionAxisKey(dirs: List<ExpectedDirection>): String {
        val parts = dirs
            .filter { it != ExpectedDirection.ANY && directionAr(it) != null }
            .map { it.name }
            .sorted()
        return if (parts.isEmpty()) "setup_axis_direction_any"
        else "setup_axis_direction_" + parts.joinToString("_").lowercase()
    }

    fun postureAxisKey(postures: List<BodyPosture>): String {
        val parts = postures
            .filter { it != BodyPosture.UNKNOWN && postureAr(it) != null }
            .map { it.name }
            .sorted()
        return if (parts.isEmpty()) "setup_axis_posture_any"
        else "setup_axis_posture_" + parts.joinToString("_").lowercase()
    }

    fun resolvePostureAxisWarning(postures: List<BodyPosture>): LocalizedText {
        val arParts = postures.mapNotNull { postureAr(it) }
        val enParts = postures.mapNotNull { postureEn(it) }
        val defAr = arParts.joinToString(" أو ")
        val defEn = enParts.joinToString(" or ")
        return SystemMessageRegistry.get(postureAxisKey(postures), defAr, defEn)
    }

    fun regionAxisKey(regions: List<VisibleRegion>): String {
        val parts = regions
            .filter { it != VisibleRegion.UNKNOWN && regionAr(it) != null }
            .map { it.name }
            .sorted()
        return if (parts.isEmpty()) "setup_axis_region_any"
        else "setup_axis_region_" + parts.joinToString("_").lowercase()
    }

    fun resolveRegionAxisWarning(regions: List<VisibleRegion>): LocalizedText {
        val arParts = regions.mapNotNull { regionAr(it) }
        val enParts = regions.mapNotNull { regionEn(it) }
        val defAr = "أظهر ${arParts.joinToString(" أو ")}"
        val defEn = "Show ${enParts.joinToString(" or ")}"
        return SystemMessageRegistry.get(regionAxisKey(regions), defAr, defEn)
    }

    // --- Setup joint angle guidance (ANGLES phase) ---

    private val knownJointBases = setOf(
        "elbow", "shoulder", "knee", "hip", "ankle", "wrist", "spine", "neck"
    )

    /**
     * Matches seeded rows in system-messages-catalog; unknown anatomical codes map to `setup_guidance_other_*`.
     */
    fun jointGuidanceKey(jointCode: String, isRaise: Boolean): String {
        val dir = if (isRaise) "raise" else "lower"
        val normalized = jointCode.trim().lowercase().replace("-", "_")
        val baseOnly = normalized.removePrefix("right_").removePrefix("left_")
        return if (baseOnly in knownJointBases) {
            "setup_guidance_${normalized}_$dir"
        } else {
            "setup_guidance_other_$dir"
        }
    }

    fun resolveJointGuidance(
        jointCode: String,
        direction: Direction?,
        level: GuidanceLevel,
        language: String
    ): LocalizedText {
        if (level == GuidanceLevel.GREEN || direction == null) {
            return SystemMessageRegistry.get(
                "setup_guidance_ok",
                "✓ ممتاز",
                "✓ Good"
            )
        }
        val isRaise = direction == Direction.RAISE
        val key = jointGuidanceKey(jointCode, isRaise)
        val base = jointCode.removePrefix("right_").removePrefix("left_")
        val side = when {
            jointCode.startsWith("left_") -> if (language == "ar") "الأيسر" else "left"
            jointCode.startsWith("right_") -> if (language == "ar") "الأيمن" else "right"
            else -> ""
        }
        val (defAr, defEn) = defaultJointGuidanceText(base, side, isRaise)
        return SystemMessageRegistry.get(key, defAr, defEn)
    }

    private fun defaultJointGuidanceText(base: String, side: String, isRaise: Boolean): Pair<String, String> {
        return when (base) {
            "elbow" -> if (isRaise) Pair(
                "ارفع الكوع $side أكثر",
                "Raise your $side elbow more"
            ) else Pair(
                "اخفض الكوع $side أكثر",
                "Lower your $side elbow more"
            )
            "shoulder" -> if (isRaise) Pair(
                "ارفع الكتف $side",
                "Raise your $side shoulder"
            ) else Pair(
                "اخفض الكتف $side",
                "Lower your $side shoulder"
            )
            "knee" -> if (isRaise) Pair(
                "افرد الركبة $side أكثر",
                "Straighten your $side knee more"
            ) else Pair(
                "اثني الركبة $side أكثر",
                "Bend your $side knee more"
            )
            "hip" -> if (isRaise) Pair(
                "افرد الورك $side",
                "Extend your $side hip"
            ) else Pair(
                "اثني الورك $side أكثر",
                "Bend your $side hip more"
            )
            "ankle" -> if (isRaise) Pair(
                "ارفع الكاحل $side",
                "Raise your $side ankle"
            ) else Pair(
                "اخفض الكاحل $side",
                "Lower your $side ankle"
            )
            "wrist" -> if (isRaise) Pair(
                "ارفع المعصم $side",
                "Raise your $side wrist"
            ) else Pair(
                "اخفض المعصم $side",
                "Lower your $side wrist"
            )
            "spine" -> if (isRaise) Pair(
                "افرد ظهرك أكثر",
                "Straighten your back more"
            ) else Pair(
                "انحني للأمام أكثر",
                "Bend forward more"
            )
            "neck" -> if (isRaise) Pair(
                "ارفع رأسك",
                "Lift your head"
            ) else Pair(
                "اخفض رأسك",
                "Lower your head"
            )
            else -> if (isRaise) Pair("ارفع أكثر", "Raise more") else Pair("اخفض أكثر", "Lower more")
        }
    }

    fun resolveSetupSceneToVisibility(): LocalizedText = SystemMessageRegistry.get(
        "training_setup_scene_to_visibility",
        "الوضع صحيح – جاري التحقق من الرؤية",
        "Position correct – checking visibility"
    )

    private fun directionAr(d: ExpectedDirection) = when (d) {
        ExpectedDirection.FRONT -> "الأمام"
        ExpectedDirection.BACK -> "الخلف"
        ExpectedDirection.SIDE_ANY -> "الجانب"
        ExpectedDirection.SIDE_LEFT -> "الجانب الأيسر"
        ExpectedDirection.SIDE_RIGHT -> "الجانب الأيمن"
        ExpectedDirection.DIAGONAL -> "بزاوية مائلة"
        ExpectedDirection.ANY -> null
    }

    private fun directionEn(d: ExpectedDirection) = when (d) {
        ExpectedDirection.FRONT -> "the front"
        ExpectedDirection.BACK -> "the back"
        ExpectedDirection.SIDE_ANY -> "the side"
        ExpectedDirection.SIDE_LEFT -> "the left side"
        ExpectedDirection.SIDE_RIGHT -> "the right side"
        ExpectedDirection.DIAGONAL -> "an angle"
        ExpectedDirection.ANY -> null
    }

    private fun postureAr(p: BodyPosture) = when (p) {
        BodyPosture.STANDING -> "قف مستقيماً"
        BodyPosture.LYING_PRONE -> "استلقِ على وجهك"
        BodyPosture.LYING_SUPINE -> "استلقِ على ظهرك"
        BodyPosture.LYING_SIDE -> "استلقِ على جنبك"
        BodyPosture.SITTING -> "اجلس"
        BodyPosture.UNKNOWN -> null
    }

    private fun postureEn(p: BodyPosture) = when (p) {
        BodyPosture.STANDING -> "Stand upright"
        BodyPosture.LYING_PRONE -> "Lie face down"
        BodyPosture.LYING_SUPINE -> "Lie face up"
        BodyPosture.LYING_SIDE -> "Lie on your side"
        BodyPosture.SITTING -> "Sit down"
        BodyPosture.UNKNOWN -> null
    }

    private fun regionAr(r: VisibleRegion) = when (r) {
        VisibleRegion.FULL_BODY -> "الجسم بالكامل"
        VisibleRegion.UPPER_BODY -> "الجزء العلوي"
        VisibleRegion.LOWER_BODY -> "الجزء السفلي"
        VisibleRegion.UNKNOWN -> null
    }

    private fun regionEn(r: VisibleRegion) = when (r) {
        VisibleRegion.FULL_BODY -> "your full body"
        VisibleRegion.UPPER_BODY -> "your upper body"
        VisibleRegion.LOWER_BODY -> "your lower body"
        VisibleRegion.UNKNOWN -> null
    }
}
