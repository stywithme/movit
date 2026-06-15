package com.movit.feature.shell

import com.movit.designsystem.components.MovitNavDestination

fun MovitAppDestination.toFloatingNav(): MovitNavDestination =
    MovitNavDestination.valueOf(name)

fun MovitNavDestination.toAppDestination(): MovitAppDestination =
    MovitAppDestination.valueOf(name)

/** Main tabs in the ink floating nav — profile is reached via header avatar, not the bar. */
val MovitShellFloatingDestinations: List<MovitNavDestination> = listOf(
    MovitAppDestination.Home,
    MovitAppDestination.Train,
    MovitAppDestination.Explore,
    MovitAppDestination.Reports,
).map { it.toFloatingNav() }
