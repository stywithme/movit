// Top-level build file where you can add configuration options common to all sub-projects/modules.

tasks.register<Exec>("docsStats") {
    group = "documentation"
    description = "Generate injectable UI/UX doc stats (i18n keys, test counts, Movit* components)."
    val script = layout.projectDirectory.file("scripts/generate-docs-stats.ps1")
    inputs.file(script)
    inputs.dir(layout.projectDirectory.dir("core/resources/src/commonMain/composeResources"))
    inputs.dir(layout.projectDirectory.dir("core/designsystem/src/commonMain"))
    inputs.dir(layout.projectDirectory.dir("feature"))
    inputs.dir(layout.projectDirectory.dir("core"))
    outputs.file(
        layout.projectDirectory.file(
            "../Docs/02-Roadmaps-And-Plans/UI-UX/generated/Docs-Stats-Snapshot.md",
        ),
    )
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        script.asFile.absolutePath,
    )
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
}
