package com.movit.feature.library

object SessionSwapPreviewData {
    private val all = listOf(
        SessionSwapCandidateUi(
            slug = "goblet-squat",
            name = "Goblet Squat",
            subtitle = "Same muscles · easier on the back",
            badge = "Best",
        ),
        SessionSwapCandidateUi(
            slug = "leg-press",
            name = "Leg Press",
            subtitle = "Machine · weighted",
        ),
        SessionSwapCandidateUi(
            slug = "front-squat",
            name = "Front Squat",
            subtitle = "Quads emphasis · weighted",
        ),
        SessionSwapCandidateUi(
            slug = "box-squat",
            name = "Box Squat",
            subtitle = "Controlled depth · bodyweight",
        ),
    )

    fun candidates(query: String, replacingSlug: String): List<SessionSwapCandidateUi> {
        val filtered = if (query.isBlank()) {
            all.filter { it.slug != replacingSlug }
        } else {
            all.filter {
                it.slug != replacingSlug &&
                    (it.name.contains(query, ignoreCase = true) ||
                        it.subtitle.contains(query, ignoreCase = true))
            }
        }
        return filtered.ifEmpty { all.filter { it.slug != replacingSlug } }
    }
}
