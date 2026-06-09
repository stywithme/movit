package com.movit.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun MovitAuthScreen(
    state: MovitAuthUiState,
    onEvent: (MovitAuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(MovitSpacing.lg),
    ) {
        when (state.screen) {
            AuthScreen.Splash -> AuthSplashPanel()
            AuthScreen.Intro -> AuthIntroPanel(
                page = state.introPage,
                onContinue = { onEvent(MovitAuthEvent.IntroContinueClicked) },
                onSkip = { onEvent(MovitAuthEvent.IntroSkipClicked) },
            )
            AuthScreen.SignIn -> AuthSignInPanel(state, onEvent)
            AuthScreen.SignUp -> AuthSignUpPanel(state, onEvent)
            AuthScreen.Forgot -> AuthForgotPanel(state, onEvent)
        }
    }
}

@Composable
private fun AuthSplashPanel() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(
            text = movitText("auth_app_name"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            modifier = Modifier.padding(top = MovitSpacing.lg),
        )
        Text(
            text = movitText("auth_tagline"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.movitColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MovitSpacing.sm),
        )
        LinearProgressIndicator(
            modifier = Modifier
                .padding(top = MovitSpacing.xl)
                .width(120.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(MovitRadius.full)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.movitColors.surface2,
        )
        Text(
            text = movitText("auth_version"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.movitColors.textTertiary,
            modifier = Modifier.padding(top = 40.dp),
        )
    }
}

@Composable
private fun AuthIntroPanel(
    page: Int,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val (icon, title, subtitle) = when (page) {
        0 -> Triple(Icons.Default.MonitorHeart, movitText("auth_intro_title_1"), movitText("auth_intro_sub_1"))
        1 -> Triple(Icons.Default.Insights, movitText("auth_intro_title_2"), movitText("auth_intro_sub_2"))
        else -> Triple(Icons.Default.FitnessCenter, movitText("auth_intro_title_3"), movitText("auth_intro_sub_3"))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = RoundedCornerShape(MovitRadius.xl),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.W800,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MovitSpacing.xl),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.movitColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MovitSpacing.sm),
        )
        IntroDots(activeIndex = page, count = MovitAuthViewModel.INTRO_PAGE_COUNT)
        MovitButton(
            text = movitText("auth_continue"),
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.xl),
        )
        MovitButton(
            text = movitText("auth_skip"),
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
            variant = MovitButtonVariant.Text,
        )
    }
}

@Composable
private fun IntroDots(activeIndex: Int, count: Int) {
    Row(
        modifier = Modifier.padding(top = MovitSpacing.xl),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        repeat(count) { index ->
            val active = index == activeIndex
            Surface(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (active) 22.dp else 8.dp),
                shape = RoundedCornerShape(MovitRadius.full),
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.movitColors.stroke
                },
            ) {}
        }
    }
}

@Composable
private fun AuthLogoHeader(title: String, subtitle: String) {
    Surface(
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 8.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.W800,
        modifier = Modifier.padding(top = MovitSpacing.lg),
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.movitColors.textSecondary,
        modifier = Modifier.padding(top = MovitSpacing.xs),
    )
}

@Composable
private fun AuthSignInPanel(
    state: MovitAuthUiState,
    onEvent: (MovitAuthEvent) -> Unit,
) {
    AuthFormScaffold(
        errorMessage = state.errorMessage,
        infoMessage = state.infoMessage,
    ) {
        AuthLogoHeader(
            title = movitText("auth_welcome_back"),
            subtitle = movitText("auth_sign_in_sub"),
        )
        AuthTextField(
            label = movitText("auth_email"),
            value = state.email,
            onValueChange = { onEvent(MovitAuthEvent.EmailChanged(it)) },
            keyboardType = KeyboardType.Email,
            modifier = Modifier.padding(top = MovitSpacing.xl),
        )
        AuthTextField(
            label = movitText("auth_password"),
            value = state.password,
            onValueChange = { onEvent(MovitAuthEvent.PasswordChanged(it)) },
            isPassword = true,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.rememberMe,
                    onCheckedChange = { onEvent(MovitAuthEvent.RememberMeChanged(it)) },
                )
                Text(
                    text = movitText("auth_remember_me"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { onEvent(MovitAuthEvent.ForgotPasswordLinkClicked) }) {
                Text(text = movitText("auth_forgot_password"))
            }
        }
        MovitButton(
            text = movitText("auth_sign_in"),
            onClick = { onEvent(MovitAuthEvent.SignInClicked) },
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.lg),
        )
        AuthDivider()
        MovitButton(
            text = movitText("auth_google"),
            onClick = { onEvent(MovitAuthEvent.SignInClicked) },
            variant = MovitButtonVariant.Outlined,
            modifier = Modifier.fillMaxWidth(),
        )
        AuthFooterLink(
            prompt = movitText("auth_no_account"),
            action = movitText("auth_sign_up"),
            onClick = { onEvent(MovitAuthEvent.GoToSignUpClicked) },
        )
    }
}

@Composable
private fun AuthSignUpPanel(
    state: MovitAuthUiState,
    onEvent: (MovitAuthEvent) -> Unit,
) {
    AuthFormScaffold(errorMessage = state.errorMessage) {
        AuthLogoHeader(
            title = movitText("auth_create_account"),
            subtitle = movitText("auth_sign_up_sub"),
        )
        AuthTextField(
            label = movitText("auth_full_name"),
            value = state.name,
            onValueChange = { onEvent(MovitAuthEvent.NameChanged(it)) },
            modifier = Modifier.padding(top = MovitSpacing.xl),
        )
        AuthTextField(
            label = movitText("auth_email"),
            value = state.email,
            onValueChange = { onEvent(MovitAuthEvent.EmailChanged(it)) },
            keyboardType = KeyboardType.Email,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        AuthTextField(
            label = movitText("auth_password"),
            value = state.password,
            onValueChange = { onEvent(MovitAuthEvent.PasswordChanged(it)) },
            isPassword = true,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        MovitButton(
            text = movitText("auth_create_account"),
            onClick = { onEvent(MovitAuthEvent.CreateAccountClicked) },
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.xl),
        )
        AuthFooterLink(
            prompt = movitText("auth_have_account"),
            action = movitText("auth_sign_in"),
            onClick = { onEvent(MovitAuthEvent.GoToSignInClicked) },
        )
    }
}

@Composable
private fun AuthForgotPanel(
    state: MovitAuthUiState,
    onEvent: (MovitAuthEvent) -> Unit,
) {
    AuthFormScaffold(errorMessage = state.errorMessage) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { onEvent(MovitAuthEvent.BackFromForgotClicked) }) {
                Text(text = movitText("auth_back"))
            }
        }
        Text(
            text = movitText("auth_reset_password"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.W800,
        )
        Text(
            text = movitText("auth_reset_sub"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.movitColors.textSecondary,
            modifier = Modifier.padding(top = MovitSpacing.xs),
        )
        AuthTextField(
            label = movitText("auth_email"),
            value = state.email,
            onValueChange = { onEvent(MovitAuthEvent.EmailChanged(it)) },
            keyboardType = KeyboardType.Email,
            modifier = Modifier.padding(top = MovitSpacing.xl),
        )
        MovitButton(
            text = movitText("auth_send_reset"),
            onClick = { onEvent(MovitAuthEvent.ForgotSubmitClicked) },
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.xl),
        )
    }
}

@Composable
private fun AuthFormScaffold(
    errorMessage: String?,
    infoMessage: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MovitSpacing.sm),
            )
        }
        if (infoMessage != null) {
            Text(
                text = infoMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MovitSpacing.sm),
            )
        }
        content()
    }
}

@Composable
private fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.W600,
            modifier = Modifier.padding(bottom = MovitSpacing.xs),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = ImeAction.Next,
            ),
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun AuthDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MovitSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = movitText("auth_or"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.movitColors.textTertiary,
            modifier = Modifier.padding(horizontal = MovitSpacing.md),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AuthFooterLink(
    prompt: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = MovitSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = prompt, style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.width(MovitSpacing.xs))
        TextButton(onClick = onClick) {
            Text(text = action, fontWeight = FontWeight.W700)
        }
    }
}
