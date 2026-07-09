package com.movit.core.network.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackendMobileContractCoverageTest {

    @Test
    fun everyBackendMobileRouteIsClassified() {
        val inventory = MobileApiContractRegistry.backendMobileRouteInventory
        val classified = MobileApiContractRegistry.kmpCoveredEndpoints +
            MobileApiContractRegistry.deferredEndpointKeys +
            MobileApiContractRegistry.backendOnlyEndpointKeys

        val unclassified = inventory - classified
        assertTrue(
            unclassified.isEmpty(),
            "Backend mobile routes missing classification: $unclassified",
        )
    }

    @Test
    fun backendOnlyEndpointsDoNotOverlapKmpOrDeferred() {
        val backendOnly = MobileApiContractRegistry.backendOnlyEndpointKeys
        val overlapKmp = backendOnly intersect MobileApiContractRegistry.kmpCoveredEndpoints
        val overlapDeferred = backendOnly intersect MobileApiContractRegistry.deferredEndpointKeys

        assertTrue(overlapKmp.isEmpty(), "Backend-only overlaps KMP coverage: $overlapKmp")
        assertTrue(overlapDeferred.isEmpty(), "Backend-only overlaps deferred: $overlapDeferred")
    }

    @Test
    fun deferredListExcludesImplementedRoutes() {
        val deferred = MobileApiContractRegistry.deferredEndpointKeys
        assertTrue("GET api/exercises/{id}/substitutions" !in deferred, "admin route moved to backend-only")
    }

    @Test
    fun backendOnlyInventoryMatchesParityAuditCount() {
        assertEquals(17, MobileApiContractRegistry.backendOnlyEndpoints.size)
    }
}
