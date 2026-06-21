package com.movit.designsystem

enum class MovitThemeMode {
    Light,
    Dark,
    System,
    ;

    fun toStorageKey(): String = when (this) {
        Light -> STORAGE_LIGHT
        Dark -> STORAGE_DARK
        System -> STORAGE_SYSTEM
    }

    companion object {
        const val STORAGE_LIGHT = "light"
        const val STORAGE_DARK = "dark"
        const val STORAGE_SYSTEM = "system"

        fun fromStorageKey(key: String?): MovitThemeMode = when (key?.lowercase()) {
            STORAGE_LIGHT -> Light
            STORAGE_DARK -> Dark
            else -> System
        }
    }
}
