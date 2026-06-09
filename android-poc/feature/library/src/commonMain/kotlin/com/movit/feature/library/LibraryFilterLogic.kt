package com.movit.feature.library

import com.movit.feature.explore.ExploreContentFilter
import com.movit.feature.explore.ExploreItemUi

object LibraryFilterLogic {

    fun filterItems(
        items: List<ExploreItemUi>,
        kind: LibraryListKind,
        chip: LibraryFilterChip,
        query: String,
    ): List<ExploreItemUi> {
        return items
            .asSequence()
            .filter { matchesChip(it, chip, kind) }
            .filter { ExploreContentFilter.matchesQuery(it, query) }
            .toList()
    }

    fun matchesChip(item: ExploreItemUi, chip: LibraryFilterChip, kind: LibraryListKind): Boolean {
        if (chip == LibraryFilterChip.All) return true
        val haystack = (item.title + " " + item.subtitle + " " + item.metadata.joinToString()).lowercase()
        return when (chip) {
            LibraryFilterChip.All -> true
            LibraryFilterChip.LowerBody,
            LibraryFilterChip.Legs,
            -> haystack.contains("leg") ||
                haystack.contains("squat") ||
                haystack.contains("lunge") ||
                haystack.contains("glute") ||
                haystack.contains("quad")
            LibraryFilterChip.Core -> haystack.contains("core") || haystack.contains("abs")
            LibraryFilterChip.Mobility -> haystack.contains("mobil") || haystack.contains("recovery")
            LibraryFilterChip.Chest -> haystack.contains("chest") || haystack.contains("push")
            LibraryFilterChip.Equipment -> haystack.contains("dumbbell") ||
                haystack.contains("barbell") ||
                haystack.contains("weight") ||
                haystack.contains("equipment")
            LibraryFilterChip.Under20Min -> {
                val underTwenty = item.durationMinutes?.let { it <= 20 } == true
                underTwenty || item.metadata.any { meta ->
                    meta.contains("12") || meta.contains("15") || meta.contains("18") ||
                        meta.contains("~12") || meta.contains("~15") || meta.contains("~18")
                }
            }
        }
    }
}
