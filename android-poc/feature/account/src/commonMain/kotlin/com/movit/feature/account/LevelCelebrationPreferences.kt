package com.movit.feature.account

import com.movit.core.data.MovitData

class LevelCelebrationPreferences(
    private val readLastSeenLevel: () -> Int,
    private val writeLastSeenLevel: (Int) -> Unit,
) {
    fun lastSeenLevel(): Int = readLastSeenLevel()

    fun markLevelSeen(level: Int) {
        writeLastSeenLevel(level)
    }

    companion object {
        const val STORE = "movit_level"
        const val LAST_SEEN_LEVEL_KEY = "last_seen_level"

        fun fromMovitData(): LevelCelebrationPreferences {
            if (!MovitData.isInstalled) {
                return LevelCelebrationPreferences(readLastSeenLevel = { 0 }, writeLastSeenLevel = {})
            }
            val platform = MovitData.requirePlatform()
            return LevelCelebrationPreferences(
                readLastSeenLevel = {
                    platform.readCache(STORE, LAST_SEEN_LEVEL_KEY)?.toIntOrNull() ?: 0
                },
                writeLastSeenLevel = { level ->
                    platform.writeCache(STORE, LAST_SEEN_LEVEL_KEY, level.toString())
                },
            )
        }
    }
}

data class LevelUpCelebrationUi(
    val fromLevel: Int,
    val toLevel: Int,
    val levelName: String,
)
