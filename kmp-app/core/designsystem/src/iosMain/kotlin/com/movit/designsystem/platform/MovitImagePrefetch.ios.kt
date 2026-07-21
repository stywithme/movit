package com.movit.designsystem.platform

actual fun prefetchMovitImageUrls(urls: List<String>) {
    val loader = runCatching { coil3.SingletonImageLoader.get() }.getOrNull() ?: return
    urls.filter { it.isNotBlank() }.forEach { url ->
        val request = coil3.request.ImageRequest.Builder(loader.components.context)
            .data(url)
            .build()
        loader.enqueue(request)
    }
}
