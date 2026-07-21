package com.movit.designsystem.platform

import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade

actual fun prefetchMovitImageUrls(urls: List<String>) {
    val context = movitCoilApplicationContext() ?: return
    val loader = runCatching { SingletonImageLoader.get(context) }.getOrNull() ?: return
    urls.filter { it.isNotBlank() }.forEach { url ->
        val request = ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .build()
        loader.enqueue(request)
    }
}
