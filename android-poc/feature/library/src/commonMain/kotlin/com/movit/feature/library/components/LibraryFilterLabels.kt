package com.movit.feature.library.components

import androidx.compose.runtime.Composable
import com.movit.feature.library.LibraryFilterChip
import com.movit.resources.movitText

@Composable
fun LibraryFilterChip.label(): String = when (this) {
    LibraryFilterChip.All -> movitText("library_filter_all")
    LibraryFilterChip.LowerBody -> movitText("library_filter_lower_body")
    LibraryFilterChip.Legs -> movitText("library_filter_legs")
    LibraryFilterChip.Core -> movitText("library_filter_core")
    LibraryFilterChip.Mobility -> movitText("library_filter_mobility")
    LibraryFilterChip.Equipment -> movitText("library_filter_equipment")
    LibraryFilterChip.Chest -> movitText("library_filter_chest")
    LibraryFilterChip.Under20Min -> movitText("library_filter_under_20")
}
