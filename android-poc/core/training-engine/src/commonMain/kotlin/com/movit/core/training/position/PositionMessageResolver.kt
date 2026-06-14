package com.movit.core.training.position

import com.movit.core.training.config.LocalizedText

/**
 * KMP resolver for position/scene axis guidance (subset of legacy MobileMessageResolver).
 */
object PositionMessageResolver {

    fun resolvePostureAxisWarning(postures: List<BodyPosture>): LocalizedText {
        val en = postures.filter { it != BodyPosture.UNKNOWN }
            .joinToString(" or ") { it.name.lowercase().replace('_', ' ') }
            .ifBlank { "any posture" }
        val ar = postures.filter { it != BodyPosture.UNKNOWN }
            .joinToString(" أو ") { postureAr(it) ?: it.name }
            .ifBlank { "أي وضعية" }
        return LocalizedText(ar = ar, en = en)
    }

    fun resolveDirectionAxisWarning(directions: List<ExpectedDirection>): LocalizedText {
        val en = directions.filter { it != ExpectedDirection.ANY }
            .joinToString(" or ") { it.code.replace('_', ' ') }
            .ifBlank { "any direction" }
        val ar = directions.filter { it != ExpectedDirection.ANY }
            .joinToString(" أو ") { directionAr(it) ?: it.code }
            .ifBlank { "أي اتجاه" }
        return LocalizedText(ar = "صوّر من $ar", en = "Film from $en")
    }

    fun resolveRegionAxisWarning(regions: List<VisibleRegion>): LocalizedText {
        val en = regions.filter { it != VisibleRegion.UNKNOWN }
            .joinToString(" or ") { it.name.lowercase().replace('_', ' ') }
            .ifBlank { "full body" }
        val ar = regions.filter { it != VisibleRegion.UNKNOWN }
            .joinToString(" أو ") { regionAr(it) ?: it.name }
            .ifBlank { "الجسم كاملاً" }
        return LocalizedText(ar = "أظهر $ar", en = "Show $en")
    }

    /** Spoken when scene axes pass and setup moves into joint-angle validation (legacy MO). */
    fun resolveSetupSceneToVisibility(): LocalizedText = LocalizedText(
        ar = "الوضع صحيح – جاري التحقق من الرؤية",
        en = "Position correct – checking visibility",
    )

    private fun postureAr(posture: BodyPosture): String? = when (posture) {
        BodyPosture.STANDING -> "واقفاً"
        BodyPosture.SITTING -> "جالساً"
        BodyPosture.LYING_PRONE -> "مستلقياً على البطن"
        BodyPosture.LYING_SUPINE -> "مستلقياً على الظهر"
        BodyPosture.LYING_SIDE -> "مستلقياً على الجانب"
        BodyPosture.UNKNOWN -> null
    }

    private fun directionAr(direction: ExpectedDirection): String? = when (direction) {
        ExpectedDirection.FRONT -> "الأمام"
        ExpectedDirection.BACK -> "الخلف"
        ExpectedDirection.SIDE_ANY -> "الجانب"
        ExpectedDirection.SIDE_LEFT -> "الجانب الأيسر"
        ExpectedDirection.SIDE_RIGHT -> "الجانب الأيمن"
        ExpectedDirection.DIAGONAL -> "زاوية"
        ExpectedDirection.ANY -> null
    }

    private fun regionAr(region: VisibleRegion): String? = when (region) {
        VisibleRegion.FULL_BODY -> "الجسم كاملاً"
        VisibleRegion.UPPER_BODY -> "الجزء العلوي"
        VisibleRegion.LOWER_BODY -> "الجزء السفلي"
        VisibleRegion.UNKNOWN -> null
    }
}
