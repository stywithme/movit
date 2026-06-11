package com.movit.feature.training

import androidx.compose.runtime.Composable

@Composable
actual fun rememberOpenAppSettingsAction(): () -> Unit = rememberIosSettingsStub()

@Composable
private fun rememberIosSettingsStub(): () -> Unit = { }
