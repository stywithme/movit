package com.trainingvalidator.poc

import android.app.Application

/**
 * Minimal [Application] for Robolectric unit tests.
 * Avoids [PoseApp.onCreate] side effects (WorkManager, Coil, sync scheduler, etc.).
 */
class UnitTestApplication : Application()
