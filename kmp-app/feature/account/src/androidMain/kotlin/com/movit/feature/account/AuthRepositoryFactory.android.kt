package com.movit.feature.account

actual fun defaultAuthRepository(): AuthRepository = SharedAuthRepository()
