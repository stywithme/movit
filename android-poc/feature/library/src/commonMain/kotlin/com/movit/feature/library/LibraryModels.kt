package com.movit.feature.library

enum class LibraryFilterChip {
    All,
    LowerBody,
    Legs,
    Core,
    Mobility,
    Equipment,
    Chest,
    Under20Min,
    ;

    companion object {
        fun defaults(kind: LibraryListKind): List<LibraryFilterChip> = when (kind) {
            LibraryListKind.Exercises -> listOf(All, LowerBody, Core, Mobility, Equipment)
            LibraryListKind.Workouts -> listOf(All, Legs, Core, Chest, Mobility, Under20Min)
        }

        /** Prototype "is-key" accent chip — second filter in the strip. */
        fun accent(kind: LibraryListKind): LibraryFilterChip = when (kind) {
            LibraryListKind.Exercises -> LowerBody
            LibraryListKind.Workouts -> Legs
        }
    }
}
