# Phase 06 — release APK smoke helper (adb checklist)
# Usage (from android-poc/):
#   .\scripts\phase06-smoke-adb.ps1 -BuildFlagOn
#   .\scripts\phase06-smoke-adb.ps1 -BuildFlagOn -SkipBuild
#
# Prerequisites: USB debugging enabled, device/emulator connected (`adb devices`).

param(
    [switch]$BuildFlagOn,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$AppId = "com.trainingvalidator.poc"
$ApkPath = "app\build\outputs\apk\release\app-release.apk"

Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not $SkipBuild) {
    if ($BuildFlagOn) {
        Write-Host ">> Building release APK (movit.shell.launcher.enabled=true)..." -ForegroundColor Cyan
        .\gradlew.bat --console=plain -Pmovit.shell.launcher.enabled=true :app:assembleRelease
    } else {
        Write-Host ">> Building release APK (flag off — legacy)..." -ForegroundColor Cyan
        .\gradlew.bat --console=plain :app:assembleRelease
    }
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
        Title = "2. Launch app (SplashActivity = LAUNCHER)"
        Cmd   = "adb shell am start -n $AppId/com.trainingvalidator.poc.ui.auth.SplashActivity"
    },
    @{
        Title = "3. (flag on) Sign in — email or Google via legacy auth"
        Cmd   = "# Complete login on device; expect navigation to MovitMainActivity / 5 tabs"
    },
    @{
        Title = "4. (flag on) Shell — verify Home / Train / Explore / Reports / Profile tabs"
        Cmd   = "# Tap through tabs on device"
    },
    @{
        Title = "5. (flag on) Train → Program → Session → Start exercise → TrainingActivity (camera)"
        Cmd   = "# Start an exercise; confirm legacy camera opens"
    },
    @{
        Title = "6. (flag on) Profile → logout → returns to SplashActivity"
        Cmd   = "# Logout from shell; confirm legacy auth screen"
    },
    @{
        Title = "7. (QA pilot, flag off build) Debug shell entry"
        Cmd   = "adb shell am start -n $AppId/com.movit.debug.MovitShellPilotActivity"
    },
    @{
        Title = "8. Capture logcat on failure"
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
    $launch = Read-Host "Run step 2 (launch SplashActivity)? [y/N]"
    if ($launch -eq "y" -or $launch -eq "Y") {
        adb shell am start -n "$AppId/com.trainingvalidator.poc.ui.auth.SplashActivity"
    }
}

Write-Host "Done. Complete remaining steps manually on the device." -ForegroundColor Cyan
