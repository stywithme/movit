package com.trainingvalidator.poc.ui.booking

import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import java.util.Calendar
import java.util.TimeZone

/**
 * Builds a [MaterialDatePicker] whose selectable range is **inclusive** from today through
 * (today + [maxAdvanceDays]) in the **UTC calendar** used by the Material date library.
 *
 * This matches backend validation (`validateBooking`) which uses UTC calendar days for
 * `max_advance_booking_days`.
 *
 * **Confirm (OK) button:** A default [MaterialDatePicker.Builder.setSelection] is set to
 * "today" so the dialog opens with a valid selection; otherwise the positive button can stay
 * disabled until the user taps a day.
 */
object BookingDatePickerHelper {

    fun show(
        fragmentManager: FragmentManager,
        tag: String,
        maxAdvanceDays: Int,
        title: String = "Select date",
        onDateSelected: (selectionUtcMillis: Long) -> Unit,
    ) {
        val safeMax = maxAdvanceDays.coerceAtLeast(0)
        val todayUtc = MaterialDatePicker.todayInUtcMilliseconds()

        val endCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        endCal.timeInMillis = todayUtc
        endCal.add(Calendar.DAY_OF_MONTH, safeMax)
        val endUtc = endCal.timeInMillis

        val constraints = CalendarConstraints.Builder()
            .setStart(todayUtc)
            .setEnd(endUtc)
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(title)
            .setCalendarConstraints(constraints)
            .setSelection(todayUtc)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            if (selection != null) onDateSelected(selection)
        }
        picker.show(fragmentManager, tag)
    }
}
