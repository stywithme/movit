package com.movit.feature.shell

enum class MovitAppDestination(
    val route: String,
    val label: String,
) {
    Home("home", "Home"),
    Train("train", "Train"),
    Explore("explore", "Explore"),
    Reports("reports", "Reports"),
    Profile("profile", "Profile"),
    ;

    val placeholderTitle: String
        get() = when (this) {
            Home -> "Home"
            Train -> "Train"
            Explore -> "Explore"
            Reports -> "Reports"
            Profile -> "Profile"
        }

    val placeholderSubtitle: String
        get() = when (this) {
            Home -> "Your daily training dashboard."
            Train -> "Your training dashboard will live here."
            Explore -> "Browse workouts, exercises and programs."
            Reports -> "Session history and performance insights."
            Profile -> "Account, goals and preferences."
        }
}
