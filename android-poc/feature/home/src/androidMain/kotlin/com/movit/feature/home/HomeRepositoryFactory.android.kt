package com.movit.feature.home

import com.movit.feature.home.remote.RemoteHomeRepository

actual fun defaultHomeRepository(): HomeRepository = RemoteHomeRepository()
