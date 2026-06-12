package com.movit.feature.account

import com.movit.core.data.platform.MovitThemeModeStorage
import com.movit.core.network.dto.UserPublicDto

object ProfileApiMapper {
    fun map(
        user: UserPublicDto,
        themeMode: String = MovitThemeModeStorage.SYSTEM,
        trainingProfileSummary: String = TrainingProfileSummaryMapper.EMPTY_SUMMARY_KEY,
    ): ProfileUi = ProfileUi(
        name = user.name,
        email = user.email,
        avatarUrl = user.avatarUrl,
        isPro = user.isPro,
        subscriptionLabel = if (user.isPro) "WayToFix Pro" else "Free",
        subscriptionRenewal = formatSubscriptionRenewal(user.subscriptionExpiry, user.isPro),
        languageCode = user.preferredLanguage.lowercase().takeIf { it == "ar" } ?: "en",
        themeMode = themeMode,
        audioCuesEnabled = user.voiceFeedback,
        hapticEnabled = user.notifications,
        trainingProfileSummary = trainingProfileSummary,
    )

    private fun formatSubscriptionRenewal(expiryIso: String?, isPro: Boolean): String? {
        if (!isPro || expiryIso.isNullOrBlank()) return null
        val formatted = formatProfileExpiryIso(expiryIso)
        return "Renews $formatted · Monthly"
    }

    private fun formatProfileExpiryIso(iso: String): String {
        val cleaned = iso.take(19).replace('T', ' ')
        val parts = cleaned.take(10).split('-')
        if (parts.size != 3) return iso
        val monthNames = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
        val month = parts[1].toIntOrNull()?.let { monthNames.getOrNull(it - 1) } ?: parts[1]
        val day = parts[2].toIntOrNull() ?: return iso
        return "$month $day, ${parts[0]}"
    }
}
