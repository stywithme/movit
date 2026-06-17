package com.movit.feature.library

sealed interface LibraryListEffect {
    data class OpenItem(val itemId: String) : LibraryListEffect
}
