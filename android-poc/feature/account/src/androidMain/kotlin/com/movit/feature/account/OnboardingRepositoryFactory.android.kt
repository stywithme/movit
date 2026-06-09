package com.movit.feature.account

actual fun defaultOnboardingRepository(): OnboardingRepository = SharedOnboardingRepository()
