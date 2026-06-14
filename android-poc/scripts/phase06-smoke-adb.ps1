# Phase 06 — release APK smoke helper (adb checklist)
# Usage (from android-poc/):
#   .\scripts\phase06-smoke-adb.ps1
#   .\scripts\phase06-smoke-adb.ps1 -SkipBuild
#
# Prerequisites: USB debugging enabled, device/emulator connected (`adb devices`).

param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$AppId = "com.trainingvalidator.poc"
$LauncherActivity = "com.movit.MovitMainActivity"
$ApkPath = "app\build\outputs\apk\release\app-release.apk"

Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not $SkipBuild) {
    Write-Host ">> Building release APK..." -ForegroundColor Cyan
    .\gradlew.bat --console=plain :app:assembleRelease
}

if (-not (Test-Path $ApkPath)) {
    throw "APK not found at $ApkPath — run build first or check path."
}

Write-Host ""
Write-Host "=== Phase 06 smoke checklist (manual verification on device) ===" -ForegroundColor Yellow
Write-Host ""

$steps = @(
    @{
        Title = "1. Install release APK"
        Cmd   = "adb install -r `"$ApkPath`""
    },
    @{
        Title = "2. Launch app (MovitMainActivity = LAUNCHER)"
        Cmd   = "adb shell am start -n $AppId/$LauncherActivity"
    },
    @{
        Title = "3. Sign in — email or Google (auth inside KMP shell)"
        Cmd   = "# Complete login on device; expect 5-tab shell"
    },
    @{
        Title = "4. Shell — verify Home / Train / Explore / Reports / Profile tabs"
        Cmd   = "# Tap through tabs on device"
    },
    @{
        Title = "5. Train → exercise session → camera training flow"
        Cmd   = "# Start an exercise; confirm KMP training screen + camera"
    },
    @{
        Title = "6. Profile → logout → returns to in-shell auth"
        Cmd   = "# Logout; confirm auth screen inside shell (not legacy Splash)"
    },
    @{
        Title = "7. Subscription deep-link (optional)"
        Cmd   = "adb shell am start -a android.intent.action.VIEW -d waytofix://subscription/result"
    },
    @{
        Title = "8. (debug build only) Design system catalog"
        Cmd   = "adb shell am start -n $AppId/com.movit.debug.MovitDesignSystemCatalogActivity"
    },
    @{
        Title = "9. Capture logcat on failure"
        Cmd   = "adb logcat -d | Select-String -Pattern 'FATAL|AndroidRuntime|Movit'"
    }
)

foreach ($step in $steps) {
    Write-Host $step.Title -ForegroundColor Green
    Write-Host "  $($step.Cmd)" -ForegroundColor DarkGray
    Write-Host ""
}

$install = Read-Host "Run step 1 (adb install) now? [y/N]"
if ($install -eq "y" -or $install -eq "Y") {
    adb install -r $ApkPath
    $launch = Read-Host "Run step 2 (launch MovitMainActivity)? [y/N]"
    if ($launch -eq "y" -or $launch -eq "Y") {
        adb shell am start -n "$AppId/$LauncherActivity"
    }
}

Write-Host "Done. Complete remaining steps manually on the device." -ForegroundColor Cyan
