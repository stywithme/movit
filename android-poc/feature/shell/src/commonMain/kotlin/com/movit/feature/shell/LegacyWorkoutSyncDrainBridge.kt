package com.movit.feature.shell

/** Platform hook: Android drains on install; iOS re-checks on resume for parity. */
internal expect fun drainLegacyWorkoutExecutionsOnResume()
