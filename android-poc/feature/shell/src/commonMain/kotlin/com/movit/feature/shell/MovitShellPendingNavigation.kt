package com.movit.feature.shell

/**
 * One-shot deep link consumed when [MovitAppShellViewModel] starts (legacy Android → KMP handoff).
 */
object MovitShellPendingNavigation {
    private var pendingRoutes: List<MovitInnerRoute>? = null

    fun set(routes: List<MovitInnerRoute>) {
        pendingRoutes = routes
    }

    fun consume(): List<MovitInnerRoute> {
        val routes = pendingRoutes.orEmpty()
        pendingRoutes = null
        return routes
    }
}
