package com.trainingvalidator.poc.network

/**
 * Booking rules from GET /api/bookings/rules (aligned with backend validateBooking).
 */
data class BookingRulesDto(
    val allowBooking: Boolean,
    val maxAdvanceBookingDays: Int,
    val minBookingHours: Int,
    val bookingDurationMinutes: Int,
)
