package com.movit.feature.account

actual fun defaultLevelRepository(): LevelRepository = SharedLevelRepository()
