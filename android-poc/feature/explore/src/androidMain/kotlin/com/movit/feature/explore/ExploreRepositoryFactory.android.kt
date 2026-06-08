package com.movit.feature.explore

import com.movit.feature.explore.remote.RemoteExploreRepository

actual fun defaultExploreRepository(): ExploreRepository = RemoteExploreRepository()
