package com.movit.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

expect suspend fun localizedString(
    language: String,
    resource: StringResource,
    vararg formatArgs: Any,
): String

@Composable
fun movitText(
    resource: StringResource,
    vararg formatArgs: Any,
): String = stringResource(resource, *formatArgs)

@Composable
fun movitText(
    name: String,
    vararg formatArgs: Any,
): String = stringResource(movitString(name), *formatArgs)
