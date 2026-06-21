package com.movit.core.data.local

import com.movit.core.data.repository.FakeMovitPlatformBindings

class FakeMovitLocalStore(
    val platform: FakeMovitPlatformBindings = FakeMovitPlatformBindings(),
    private val delegate: InMemoryMovitLocalStore = InMemoryMovitLocalStore(),
) : MovitLocalStore by delegate
