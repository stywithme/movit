package com.movit.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * Cross-platform remote image (Coil 3 / Compose Multiplatform).
 *
 * Loads real network images on **Android and iOS** from `commonMain` (Coil 3 + Ktor network
 * fetcher); falls back to a letter [MovitMediaPlaceholder] only when the URL is null/blank.
 *
 * Previously this was `expect/actual`: Android used Coil 2 (`AsyncImage`) while iOS rendered a
 * placeholder unconditionally. Coil 3 unifies both behind one commonMain implementation.
 *
 * Disk cache: [com.movit.designsystem.platform.MovitImageCachePolicy.DISK_MAX_BYTES] (64 MiB) via
 * [com.movit.designsystem.platform.installMovitCoilImageLoader] on Android shell startup.
 */
@Composable
fun MovitRemoteImage(
    imageUrl: String?,
    contentDescription: String?,
    placeholderLabel: String,
    modifier: Modifier = Modifier,
) {
    if (imageUrl.isNullOrBlank()) {
        MovitMediaPlaceholder(label = placeholderLabel, modifier = modifier)
        return
    }
    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
