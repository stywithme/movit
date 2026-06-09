package com.movit.feature.shell

enum class MovitAppDestination(
    val route: String,
    val label: String,
) {
    Home("home", "Home"),
    Train("train", "Train"),
    Explore("explore", "Explore"),
    Reports("reports", "Reports"),
    Profile("profile", "Account"),
    ;

    val pageTitle: String
        get() = label

    val pageSubtitle: String
        get() = when (this) {
            Home -> "Your daily training dashboard."
            Train -> "Your program and today's plan."
            Explore -> "Browse workouts, exercises and programs."
            Reports -> "Session history and performance insights."
            Profile -> "Account settings and subscription."
        }
}
