package com.movit.feature.account

actual fun defaultProfileRepository(): ProfileRepository = SharedProfileRepository()
