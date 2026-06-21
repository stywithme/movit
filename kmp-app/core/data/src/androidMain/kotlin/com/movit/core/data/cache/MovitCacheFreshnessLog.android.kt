package com.movit.core.data.cache

internal actual fun logCacheFreshnessLine(tag: String, line: String) {
    android.util.Log.i(tag, line)
}
