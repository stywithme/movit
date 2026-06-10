package com.movit.feature.reports

import kotlin.math.abs
import kotlin.math.round

internal object ReportsFormatting {

    fun formatReps(reps: Int): String {
        return if (reps >= 1_000) {
            reps.toString().reversed().chunked(3).joinToString(",").reversed()
        } else {
            reps.toString()
        }
    }

    fun formatVolume(volume: Float): String {
        return if (volume >= 1_000f) {
            val scaled = (volume / 100f).toInt() / 10f
            "${scaled}k"
        } else {
            volume.toInt().toString()
        }
    }

    fun formatOneDecimal(value: Float): String {
        val tenths = round(value * 10f).toInt()
        val intPart = tenths / 10
        val decPart = abs(tenths % 10)
        return "$intPart.$decPart"
    }

    fun formatDuration(durationMs: Long): String {
        val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes.coerceAtLeast(0)}m"
        }
    }
}
