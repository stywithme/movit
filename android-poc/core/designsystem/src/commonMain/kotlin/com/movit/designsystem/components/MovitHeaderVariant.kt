package com.movit.designsystem.components

/**
 * Distinguishes the welcoming Home header from standard tab page headers.
 *
 * - [Home]: avatar · time greeting · user name · notifications (Home only).
 * - [TabPage]: avatar · page title · optional subtitle · no notifications by default.
 */
enum class MovitHeaderVariant {
    Home,
    TabPage,
}
