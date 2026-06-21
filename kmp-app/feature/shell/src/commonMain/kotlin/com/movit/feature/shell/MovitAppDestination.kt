package com.movit.feature.shell

import androidx.compose.runtime.Composable
import com.movit.resources.movitText

enum class MovitAppDestination(
    val route: String,
    val labelKey: String,
    val subtitleKey: String,
) {
    Home("home", "nav_home", "dest_home_subtitle"),
    Train("train", "nav_train", "dest_train_subtitle"),
    Explore("explore", "nav_explore", "dest_explore_subtitle"),
    Reports("reports", "nav_reports", "dest_reports_subtitle"),
    Profile("profile", "nav_account", "profile_subtitle"),
    ;
}

@Composable
fun MovitAppDestination.localizedLabel(): String = movitText(labelKey)

@Composable
fun MovitAppDestination.localizedSubtitle(): String = movitText(subtitleKey)
