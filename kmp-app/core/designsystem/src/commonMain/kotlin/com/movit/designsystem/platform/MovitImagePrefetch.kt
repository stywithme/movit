package com.movit.designsystem.platform

/**
 * Warms Coil disk cache for catalog / program images (R7).
 * No-op when Coil is not installed.
 */
expect fun prefetchMovitImageUrls(urls: List<String>)
