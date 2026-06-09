package com.movit.feature.account

import com.movit.core.network.dto.UserPublicDto

object ProfileApiMapper {
    fun map(user: UserPublicDto, appearance: String = "System"): ProfileUi = ProfileUi(
        name = user.name,
        email = user.email,
        avatarUrl = user.avatarUrl,
        isPro = user.isPro,
        subscriptionLabel = if (user.isPro) "WayToFix Pro" else "Free",
        subscriptionRenewal = user.subscriptionExpiry?.let { "Renews $it" },
        language = when (user.preferredLanguage.lowercase()) {
            "ar" -> "Arabic"
            else -> "English"
        },
        appearance = appearance,
        audioCuesEnabled = user.voiceFeedback,
        hapticEnabled = user.notifications,
        trainingProfileSummary = "Goals · equipment · schedule",
    )
}
