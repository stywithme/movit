package com.movit.core.data.repository

/**
 * Parses day-customization cache keys and maps legacy [programId] segments to [userProgramId].
 */
class DayCustomizationKeyResolver(
    private val enrollments: UserProgramEnrollmentLocalStore,
) {
    fun programIdForUserProgram(userProgramId: String): String? =
        enrollments.get(userProgramId)?.programId?.takeIf { it.isNotBlank() }
    fun resolveCanonicalUserProgramId(enrollmentOrProgramId: String): String {
        if (enrollmentOrProgramId.isBlank()) return enrollmentOrProgramId
        if (enrollments.get(enrollmentOrProgramId) != null) return enrollmentOrProgramId
        return enrollments.resolveActiveUserProgramId(enrollmentOrProgramId) ?: enrollmentOrProgramId
    }

    fun isCanonicalUserProgramId(userProgramId: String): Boolean =
        userProgramId.isNotBlank() && enrollments.get(userProgramId) != null

    companion object {
        fun parseDayCustomizationKey(key: String): DayCustomizationKeyParts? {
            if (!key.startsWith(DAY_KEY_PREFIX)) return null
            val body = key.removePrefix(DAY_KEY_PREFIX)
            val parts = body.split("_")
            if (parts.size < 3) return null
            val dayNumber = parts.last().toIntOrNull() ?: return null
            val weekNumber = parts[parts.size - 2].toIntOrNull() ?: return null
            val enrollmentOrProgramId = parts.dropLast(2).joinToString("_")
            if (enrollmentOrProgramId.isBlank()) return null
            return DayCustomizationKeyParts(
                enrollmentOrProgramId = enrollmentOrProgramId,
                weekNumber = weekNumber,
                dayNumber = dayNumber,
            )
        }

        private const val DAY_KEY_PREFIX = "day_"
    }
}

data class DayCustomizationKeyParts(
    val enrollmentOrProgramId: String,
    val weekNumber: Int,
    val dayNumber: Int,
)
