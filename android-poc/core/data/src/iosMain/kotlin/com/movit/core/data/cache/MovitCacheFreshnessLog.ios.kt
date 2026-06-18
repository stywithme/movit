package com.movit.core.data.cache

internal actual fun logCacheFreshnessLine(tag: String, line: String) {
    println("[$tag] $line")
}
