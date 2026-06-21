package com.movit.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class MovitApiConfigTest {

    @Test
    fun getEffectiveBaseUrl_usesOverrideWhenSet() {
        val previous = MovitApiConfig.overrideBaseUrl
        try {
            MovitApiConfig.overrideBaseUrl = "https://override.test/"
            assertEquals("https://override.test/", MovitApiConfig.getEffectiveBaseUrl())
        } finally {
            MovitApiConfig.overrideBaseUrl = previous
        }
    }
}
