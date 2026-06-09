package com.movit.feature.home

actual fun defaultHomeRepository(): HomeRepository = SharedHomeRepository()
