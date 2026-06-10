package com.movit.core.network.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyKmpContractParityTest {

    @Test
    fun everyLegacyEndpointIsCoveredOrExplicitlyDeferred() {
        val uncovered = MobileApiContractRegistry.legacyEndpoints -
            MobileApiContractRegistry.kmpCoveredEndpoints -
            MobileApiContractRegistry.deferredEndpointKeys

        assertTrue(
            uncovered.isEmpty(),
            "Legacy endpoints missing from KMP and not deferred: $uncovered",
        )
    }

    @Test
    fun deferredEndpointsDoNotOverlapKmpCoverage() {
        val overlap = MobileApiContractRegistry.kmpCoveredEndpoints intersect
            MobileApiContractRegistry.deferredEndpointKeys
        assertTrue(
            overlap.isEmpty(),
            "Endpoint listed as both KMP-covered and deferred: $overlap",
        )
    }

    @Test
    fun kmpRegistryMatchesMovitMobileApiSource() {
        val extracted = MovitMobileApiPathExtractor.extractFromSource()
        val registry = MobileApiContractRegistry.kmpCoveredEndpoints

        val missingFromRegistry = extracted - registry
        val staleInRegistry = registry - extracted - MobileApiContractRegistry.kmpOnlyEndpoints

        assertTrue(
            missingFromRegistry.isEmpty(),
            "MovitMobileApi paths not in kmpCoveredEndpoints: $missingFromRegistry",
        )
        assertTrue(
            staleInRegistry.isEmpty(),
            "kmpCoveredEndpoints lists paths absent from MovitMobileApi: $staleInRegistry",
        )
    }

    @Test
    fun legacyCatalogMatchesRetrofitSourceFiles() {
        val extracted = RetrofitContractPathExtractor.extractAll()
        assertEquals(
            MobileApiContractRegistry.legacyEndpoints,
            extracted,
            "Retrofit source changed — update MobileApiContractRegistry.legacyEndpoints",
        )
    }
}
