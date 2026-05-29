# POSE Docs reorganization
$ErrorActionPreference = "Stop"
$Docs = "d:\laragon\www\POSE-2\Docs"

function Ensure-Dir($p) { if (-not (Test-Path $p)) { New-Item -ItemType Directory -Path $p -Force | Out-Null } }

$dirs = @(
    "$Docs\00-Active-Reference\Engine",
    "$Docs\00-Active-Reference\Metrics",
    "$Docs\00-Active-Reference\Contracts",
    "$Docs\00-Active-Reference\Architecture-As-Built",
    "$Docs\00-Active-Reference\Product-Master",
    "$Docs\00-Active-Reference\Operations\Payment-gateway",
    "$Docs\01-Business-Planning",
    "$Docs\02-Roadmaps-And-Plans\Platform",
    "$Docs\02-Roadmaps-And-Plans\UI-UX",
    "$Docs\02-Roadmaps-And-Plans\Engine-Future",
    "$Docs\03-Implemented-Archive\Hold",
    "$Docs\03-Implemented-Archive\Position-Checks",
    "$Docs\03-Implemented-Archive\Feedback",
    "$Docs\03-Implemented-Archive\Post-Training-Report",
    "$Docs\03-Implemented-Archive\Admin-Exercise",
    "$Docs\03-Implemented-Archive\Video-Mode",
    "$Docs\04-Research\ACSM-Prescription",
    "$Docs\04-Research\AI-Models",
    "$Docs\04-Research\Body-Scan",
    "$Docs\04-Research\Training-Programs",
    "$Docs\04-Research\Joint-Angle",
    "$Docs\04-Research\Misc",
    "$Docs\05-Guides\Trainer-Guide",
    "$Docs\05-Guides\MediaPipe-Wiki",
    "$Docs\06-Assets\UI-Docs",
    "$Docs\06-Assets\Examples",
    "$Docs\06-Assets\ML-Training-Images",
    "$Docs\06-Assets\Config-Samples",
    "$Docs\99-Archive\Superseded",
    "$Docs\99-Archive\Legacy-Plans",
    "$Docs\99-Archive\Criticism-Reviews",
    "$Docs\99-Archive\Cancelled-Features"
)
foreach ($d in $dirs) { Ensure-Dir $d }

function Move-IfExists($src, $dest) {
    if (Test-Path -LiteralPath $src) {
        Ensure-Dir (Split-Path $dest -Parent)
        if (Test-Path -LiteralPath $dest) { Remove-Item -LiteralPath $dest -Force }
        Move-Item -LiteralPath $src -Destination $dest -Force
    }
}

function Move-DirContents($srcDir, $destDir) {
    if (-not (Test-Path -LiteralPath $srcDir)) { return }
    Ensure-Dir $destDir
    Get-ChildItem -LiteralPath $srcDir | ForEach-Object {
        Move-IfExists $_.FullName (Join-Path $destDir $_.Name)
    }
}

# Top-level folder moves
if (Test-Path "$Docs\American-College-Prescription") {
    Move-IfExists "$Docs\American-College-Prescription" "$Docs\04-Research\ACSM-Prescription"
}
Move-DirContents "$Docs\AI-Models-Reasearch" "$Docs\04-Research\AI-Models"
if (Test-Path "$Docs\AI-Models-Reasearch") { Remove-Item "$Docs\AI-Models-Reasearch" -Recurse -Force -ErrorAction SilentlyContinue }
Move-DirContents "$Docs\Trainer-Guide" "$Docs\05-Guides\Trainer-Guide"
if (Test-Path "$Docs\Trainer-Guide") { Remove-Item "$Docs\Trainer-Guide" -Recurse -Force -ErrorAction SilentlyContinue }
Move-DirContents "$Docs\MediaPipe-Wiki" "$Docs\05-Guides\MediaPipe-Wiki"
if (Test-Path "$Docs\MediaPipe-Wiki") { Remove-Item "$Docs\MediaPipe-Wiki" -Recurse -Force -ErrorAction SilentlyContinue }
Move-DirContents "$Docs\UI-Docs" "$Docs\06-Assets\UI-Docs"
if (Test-Path "$Docs\UI-Docs") { Remove-Item "$Docs\UI-Docs" -Recurse -Force -ErrorAction SilentlyContinue }
Move-IfExists "$Docs\train" "$Docs\06-Assets\ML-Training-Images"
Move-IfExists "$Docs\google-auth-json" "$Docs\06-Assets\Config-Samples\google-auth-json"
Move-DirContents "$Docs\Training_reasearch" "$Docs\04-Research\Misc"
if (Test-Path "$Docs\Training_reasearch") { Remove-Item "$Docs\Training_reasearch" -Recurse -Force -ErrorAction SilentlyContinue }

Move-DirContents "$Docs\New-Project\Planning" "$Docs\01-Business-Planning"

# ASCII-named file moves from New-Project and Docs root
$moves = @{
    "$Docs\New-Project\training-engine.md" = "$Docs\00-Active-Reference\Engine\training-engine.md"
    "$Docs\New-Project\pose-scene-detection-how-it-works.md" = "$Docs\00-Active-Reference\Engine\pose-scene-detection-how-it-works.md"
    "$Docs\New-Project\Positions-Check-Concept.md" = "$Docs\00-Active-Reference\Engine\Positions-Check-Concept.md"
    "$Docs\New-Project\Bilateral-Design.md" = "$Docs\00-Active-Reference\Engine\Bilateral-Design.md"
    "$Docs\New-Project\Metrics-Complete-Reference.md" = "$Docs\00-Active-Reference\Metrics\Metrics-Complete-Reference.md"
    "$Docs\New-Project\Metrics-Final-Framework.md" = "$Docs\00-Active-Reference\Metrics\Metrics-Final-Framework.md"
    "$Docs\Exercise-JSON-Schema.md" = "$Docs\00-Active-Reference\Contracts\Exercise-JSON-Schema.md"
    "$Docs\New-Project\API_ENDPOINTS.md" = "$Docs\00-Active-Reference\Contracts\API_ENDPOINTS.md"
    "$Docs\research-arabic-tts-mobile-fallback.md" = "$Docs\00-Active-Reference\Contracts\research-arabic-tts-mobile-fallback.md"
    "$Docs\trainee-journey-current-state" = "$Docs\00-Active-Reference\Architecture-As-Built\trainee-journey-current-state"
    "$Docs\New-Project\session-training-ux-flow.md" = "$Docs\00-Active-Reference\Architecture-As-Built\session-training-ux-flow.md"
    "$Docs\New-Project\app_navigation_diagram.md" = "$Docs\00-Active-Reference\Architecture-As-Built\app_navigation_diagram.md"
    "$Docs\New-Project\New-Project-Structure.md" = "$Docs\00-Active-Reference\Architecture-As-Built\New-Project-Structure.md"
    "$Docs\C4 Model.md" = "$Docs\00-Active-Reference\Architecture-As-Built\C4-Model.md"
    "$Docs\New-Project\Unified-User-Journey-Plan.md" = "$Docs\00-Active-Reference\Product-Master\Unified-User-Journey-Plan.md"
    "$Docs\New-Project\Post-Training-Report-Review.md" = "$Docs\00-Active-Reference\Product-Master\Post-Training-Report-Review.md"
    "$Docs\New-Project\System-Architecure-planing.md" = "$Docs\02-Roadmaps-And-Plans\Platform\System-Architecure-planing.md"
    "$Docs\New-Project\Body-Scan-Plan.md" = "$Docs\02-Roadmaps-And-Plans\Platform\Body-Scan-Plan.md"
    "$Docs\New-Project\Data_Extraction.md" = "$Docs\02-Roadmaps-And-Plans\Platform\Data_Extraction.md"
    "$Docs\New-Project\Program-Workout-Plan.md" = "$Docs\02-Roadmaps-And-Plans\Platform\Program-Workout-Plan.md"
    "$Docs\New-Project\Programs-System-Recovery-Plan.md" = "$Docs\02-Roadmaps-And-Plans\Platform\Programs-System-Recovery-Plan.md"
    "$Docs\New-Project\Program-Opimize-Plan.md" = "$Docs\02-Roadmaps-And-Plans\Platform\Program-Opimize-Plan.md"
    "$Docs\Hold-Exercise-Analysis.md" = "$Docs\03-Implemented-Archive\Hold\Hold-Exercise-Analysis.md"
    "$Docs\Feedback-System-Improvement-Plan.md" = "$Docs\03-Implemented-Archive\Feedback\Feedback-System-Improvement-Plan.md"
    "$Docs\New-Project\Feedback-Messages-Refactor-Plan.md" = "$Docs\03-Implemented-Archive\Feedback\Feedback-Messages-Refactor-Plan.md"
    "$Docs\Post-Training-Report-Plan.md" = "$Docs\03-Implemented-Archive\Post-Training-Report\Post-Training-Report-Plan.md"
    "$Docs\Post-Training-Report-Enhancement-Plan.md" = "$Docs\03-Implemented-Archive\Post-Training-Report\Post-Training-Report-Enhancement-Plan.md"
    "$Docs\New-Project\Reports-History-Plan.md" = "$Docs\03-Implemented-Archive\Post-Training-Report\Reports-History-Plan.md"
    "$Docs\Back-Excersise.md" = "$Docs\03-Implemented-Archive\Admin-Exercise\Exercise-Creation-Admin-Plan.md"
    "$Docs\New-Project\Body-Scan-Deep-research.md" = "$Docs\04-Research\Body-Scan\Body-Scan-Deep-research.md"
    "$Docs\New-Project\Body-Scan-Lite-Research.md" = "$Docs\04-Research\Body-Scan\Body-Scan-Lite-Research.md"
    "$Docs\Angle.md" = "$Docs\04-Research\Misc\Angle-solutions-catalog.md"
    "$Docs\New-Project\joint-debug.md" = "$Docs\99-Archive\Superseded\joint-debug-angle-lab-dump.md"
    "$Docs\State-Machine-Unified-Plan.md" = "$Docs\02-Roadmaps-And-Plans\Engine-Future\State-Machine-Unified-Plan.md"
    "$Docs\Session-Supervisor-Plan.md" = "$Docs\02-Roadmaps-And-Plans\Engine-Future\Session-Supervisor-Plan.md"
    "$Docs\Refactor-Visuals.md" = "$Docs\02-Roadmaps-And-Plans\Engine-Future\Refactor-Visuals.md"
    "$Docs\Improvements-Roadmap.md" = "$Docs\02-Roadmaps-And-Plans\Engine-Future\Improvements-Roadmap.md"
    "$Docs\Future-Features-Roadmap.md" = "$Docs\02-Roadmaps-And-Plans\Engine-Future\Future-Features-Roadmap.md"
    "$Docs\Mobile-UI.md" = "$Docs\99-Archive\Superseded\Mobile-UI.md"
}

$uiPlans = @(
    "Explore-Page-Redesign-Plan.md", "Train-Page-Redesign-Plan.md", "Week-Plan-Redesign-Plan.md",
    "Session-Details-Redesign-Plan.md", "Program-Detail-Redesign-Plan.md", "Mobile-UX-Enhancement-Plan.md",
    "Setup-Pose-Enhancement-Plan.md", "reports-revamp-plan.md", "Cache-pro.md", "Flexibility-Upgrade-Plan.md"
)
foreach ($f in $uiPlans) { $moves["$Docs\New-Project\$f"] = "$Docs\02-Roadmaps-And-Plans\UI-UX\$f" }

$superseded = @(
    "Technical-Architecture.md", "FlexFit-Strategic-Plan.md", "Metrics-Architecture-Plan.md",
    "Current-Metrics-Analysis.md", "training-system-audit.md", "score-evaluation-audit.md",
    "System-Architecture-Summary-AR.md", "Main-Metrics.md", "Body-Scan-Ideas.md"
)
foreach ($f in $superseded) { $moves["$Docs\New-Project\$f"] = "$Docs\99-Archive\Superseded\$f" }

$moves["$Docs\01-Business-Planning\Buyer-Persona.md"] = "$Docs\99-Archive\Superseded\Buyer-Persona.md"
$moves["$Docs\New-Project\criticism.MD"] = "$Docs\99-Archive\Criticism-Reviews\criticism-system-architecture.md"

foreach ($kv in $moves.GetEnumerator()) { Move-IfExists $kv.Key $kv.Value }

# Payment gateway
if (Test-Path "$Docs\New-Project\Payment-gateway") {
    Get-ChildItem -LiteralPath "$Docs\New-Project\Payment-gateway" -File | ForEach-Object {
        if ($_.Name -match "booking") {
            Move-IfExists $_.FullName "$Docs\99-Archive\Cancelled-Features\$($_.Name)"
        } else {
            Move-IfExists $_.FullName "$Docs\00-Active-Reference\Operations\Payment-gateway\$($_.Name)"
        }
    }
}

Move-DirContents "$Docs\New-Project\Training-Programs" "$Docs\04-Research\Training-Programs"
Move-DirContents "$Docs\New-Project\Joint-Debug" "$Docs\04-Research\Joint-Angle"
Move-IfExists "$Docs\04-Research\Joint-Angle\elbow-solution-knowledge-base.md" "$Docs\99-Archive\Superseded\elbow-solution-knowledge-base.md"
Move-IfExists "$Docs\New-Project\Example" "$Docs\06-Assets\Examples"

# Arabic / special root files by pattern
Get-ChildItem -LiteralPath $Docs -File | ForEach-Object {
    $n = $_.Name
    if ($n -like "*Hold*" -and $n -like "*") {
        if ($n -match "Hold-Mode" -or $n -match "Hold-Mode") { Move-IfExists $_.FullName "$Docs\03-Implemented-Archive\Hold\$n" }
        elseif ($n -match "Hold" -and $n -notmatch "Exercise") { Move-IfExists $_.FullName "$Docs\03-Implemented-Archive\Hold\$n" }
    }
    if ($n -like "*Position*") { Move-IfExists $_.FullName "$Docs\03-Implemented-Archive\Position-Checks\$n" }
    if ($n -like "*Visuals*") { Move-IfExists $_.FullName "$Docs\99-Archive\Superseded\$n" }
    if ($n -like "*Arc*") { Move-IfExists $_.FullName "$Docs\02-Roadmaps-And-Plans\Engine-Future\$n" }
    if ($n -like "*Engine*") { Move-IfExists $_.FullName "$Docs\02-Roadmaps-And-Plans\Engine-Future\$n" }
    if ($n -like "*Video*" -or $n -like "*video*") { Move-IfExists $_.FullName "$Docs\03-Implemented-Archive\Video-Mode\$n" }
}

# criticism arabic file
Get-ChildItem -LiteralPath "$Docs\New-Project" -File -ErrorAction SilentlyContinue | Where-Object { $_.Extension -match "\.md$" -and $_.Name -notmatch "^[A-Za-z0-9_-]" } | ForEach-Object {
    Move-IfExists $_.FullName "$Docs\99-Archive\Criticism-Reviews\criticism-body-scan-plan.md"
}

# Delete logs
$deletePatterns = @("*LOG*", "*log.md", "replay-log.md", "Payment-log.md", "training-log.md", "Exercise-Log.md", "Audio-log.md", "joint-debug.md")
Get-ChildItem -LiteralPath $Docs -Recurse -File -ErrorAction SilentlyContinue | Where-Object {
    $name = $_.Name
    ($name -match "cursor_" -and $_.DirectoryName -match "AI-Models") -or
    ($name -eq "State_Range.md") -or
    ($name -eq "Greet-Feedback-Ideas.md") -or
    ($name -eq "elbow-debug.md") -or
    ($name -eq "On-Camera-Log.md") -or
    ($name -eq "A13-LOG-Full_Line.md") -or
    ($name -eq "S22U-LOG-ARC_Heavy.md") -or
    ($name -eq "replay-log.md") -or
    ($name -eq "Payment-log.md") -or
    ($name -eq "training-log.md") -or
    ($name -eq "Exercise-Log.md") -or
    ($name -eq "Audio-log.md")
} | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }

if (Test-Path "$Docs\New-Project") {
    $left = @(Get-ChildItem -LiteralPath "$Docs\New-Project" -Recurse -ErrorAction SilentlyContinue)
    if ($left.Count -eq 0) { Remove-Item -LiteralPath "$Docs\New-Project" -Recurse -Force }
    else { Write-Host "Remaining in New-Project:"; $left | ForEach-Object { $_.FullName } }
}

Write-Host "Done."
