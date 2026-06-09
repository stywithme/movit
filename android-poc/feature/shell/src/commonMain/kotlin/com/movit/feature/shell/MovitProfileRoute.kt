package com.movit.feature.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.movit.core.data.MovitData
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitListRow
import com.movit.designsystem.components.MovitScaffold
import com.movit.resources.movitText

@Composable
fun MovitProfileRoute(
    modifier: Modifier = Modifier,
) {
    val account = remember {
        if (MovitData.isInstalled) {
            val platform = MovitData.requirePlatform()
            ProfileAccountState(
                displayName = platform.userDisplayName(),
                language = platform.preferredLanguage(),
                isPro = platform.isProUser(),
                isSignedIn = platform.authHeader() != null,
            )
        } else {
            ProfileAccountState(
                displayName = "Athlete",
                language = "en",
                isPro = false,
                isSignedIn = false,
            )
        }
    }

    MovitScaffold(
        modifier = modifier,
        title = movitText("profile_title"),
        subtitle = movitText("profile_subtitle"),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
        ) {
            MovitListRow(
                modifier = Modifier.fillMaxWidth(),
                title = account.displayName,
                subtitle = if (account.isSignedIn) {
                    movitText("profile_signed_in")
                } else {
                    movitText("profile_sign_in_prompt")
                },
                icon = Icons.Default.Person,
                showChevron = false,
            )
            MovitListRow(
                modifier = Modifier.fillMaxWidth(),
                title = movitText("profile_language"),
                subtitle = languageLabel(account.language),
                icon = Icons.Default.Language,
                showChevron = false,
            )
            MovitListRow(
                modifier = Modifier.fillMaxWidth(),
                title = movitText("profile_subscription"),
                subtitle = if (account.isPro) {
                    movitText("profile_pro_active")
                } else {
                    movitText("profile_free_plan")
                },
                icon = Icons.Default.Star,
                showChevron = false,
            )
        }
    }
}

@Composable
private fun languageLabel(code: String): String = when (code.lowercase()) {
    "ar" -> movitText("profile_language_ar")
    "en" -> movitText("profile_language_en")
    else -> code.uppercase()
}

private data class ProfileAccountState(
    val displayName: String,
    val language: String,
    val isPro: Boolean,
    val isSignedIn: Boolean,
)
