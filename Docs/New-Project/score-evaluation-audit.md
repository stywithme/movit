# Score and Evaluation Audit

Reviewed on `2026-03-22`.

This document audits every major score/evaluation concept currently used by the Android app, from engine calculation through report generation, persistence, analytics, assessment, and UI display.

## Executive Summary

The app does **not** currently operate on a single unified "score".

There are multiple score families in active use:

1. `RepResult.score` / `RepTimelineEntry.score`
   The per-rep form quality score.
2. `SessionSummary.averageScore` / `PerformanceSummary.averageScore`
   The arithmetic mean of completed rep scores.
3. `OverallQualityScore.score`
   A composite report-time score combining form, safety, and control.
4. Card scores from `PerformanceMetricsBuilder`
   `formCard`, `safetyCard`, and `controlCard` overall scores.
5. Accuracy / clean ratio
   Valid-rep ratio, not the same as form quality score.
6. Assessment / level / backend metrics
   `bodyScore`, `domainScores`, backend `averageFormScore`, and level profile scores.

Because these families are mixed across screens, the user concern is valid:

- The first report screen can show a different score concept than the chart.
- The chart and `best/worst` are aligned with rep scores.
- The overview session badge can show a different composite score.
- Quick insights may be generated from a different score than the one shown.
- Program session summary uses accuracy in places where the wording suggests quality/form.

## Score Families

### 1. Per-rep score

Primary fields:

- `training/models/TrainingSession.kt` -> `RepResult.score`
- `training/report/PostTrainingReport.kt` -> `RepTimelineEntry.score`
- `training/report/PostTrainingReport.kt` -> `BestRepHighlight.score`
- `training/report/PostTrainingReport.kt` -> `WorstRepHighlight.score`

Meaning:

- A `0..100` score representing the quality of a single completed rep.
- This is the core atomic score for rep-based training.

Source:

- Calculated in `training/engine/ScoreCalculator.kt`
- Finalized and adjusted in `training/engine/RepCounter.kt`

### 2. Average rep score

Primary fields:

- `training/models/TrainingSession.kt` -> `SessionSummary.averageScore`
- `training/report/PostTrainingReport.kt` -> `PerformanceSummary.averageScore`

Meaning:

- Arithmetic mean of all completed rep scores.
- This is the closest thing to "average rep rating".

Source:

- `training/engine/RepCounter.kt` -> `getAverageScore()`
- `training/TrainingEngine.kt` -> `SessionSummary.averageScore = repCounter.getAverageScore()`
- `training/report/ReportGenerator.kt` copies it into `PerformanceSummary.averageScore`

Relationship:

- `SessionSummary.averageScore == PerformanceSummary.averageScore`
- They represent the same value in two model layers.

### 3. Composite report score

Primary field:

- `training/report/PostTrainingReport.kt` -> `OverallQualityScore.score`

Meaning:

- A weighted score built at report generation time.
- Not a plain average of rep scores.

Formula source:

- `training/report/ReportGenerator.kt` -> `calculateOverallQuality(...)`
- `training/report/PostTrainingReport.kt` -> `OverallQualityScore.calculate(...)`

Rep-based exercise weights:

- Form: `40%`
- Safety: `35%`
- Control: `25%`

Hold-based exercise weights:

- Form: `35%`
- Safety: `40%`
- Control: `25%`

### 4. Ratings

There are two active rating systems:

- `training/report/PostTrainingReport.kt` -> `PerformanceRating`
- `training/report/PostTrainingReport.kt` -> `QualityRating`

They are not the same thing.

#### `PerformanceRating`

Meaning:

- Session label derived from `averageScore` plus clean/count ratio.

Source:

- `PerformanceRating.fromScoreAndRatio(averageScore, countedRatio)`

Thresholds:

- `EXCELLENT`: `averageScore >= 80` and `countedRatio >= 0.9`
- `GOOD`: `averageScore >= 60` and `countedRatio >= 0.75`
- `FAIR`: `averageScore >= 40` and `countedRatio >= 0.6`
- else `NEEDS_WORK`

#### `QualityRating`

Meaning:

- Label for `OverallQualityScore.score`

Source:

- `QualityRating.fromScore(score)`

Thresholds:

- `EXCELLENT`: `>= 90`
- `GOOD`: `>= 70`
- `FAIR`: `>= 50`
- else `NEEDS_WORK`

### 5. Accuracy / clean ratio

Primary fields:

- `training/engine/RepCounter.kt` -> `getAccuracy()`
- `training/models/TrainingSession.kt` -> `SessionSummary.cleanRatio`
- `training/report/PostTrainingReport.kt` -> `PerformanceSummary.cleanRatio`
- `training/session/SessionTrainingEngine.kt` -> `SetMetrics.accuracy`, `ExerciseReport.averageAccuracy`, `SessionReport.averageAccuracy`

Meaning:

- Valid/clean/count-related ratio.
- This is **not** the same as form score.

## End-to-End Calculation Pipeline

### A. Rep score calculation

#### `training/engine/ScoreCalculator.kt`

Main role:

- Defines the base numeric scoring logic.

Important methods:

- `calculateRepScore(jointStates, primaryJoints)`
- `calculateHoldScore(stateTimeMs)`
- `getScoreRate(state)`

Observed behavior:

- Primary joints have stronger weight than secondary joints.
- `TRANSITION` states are skipped.
- Danger joints apply extra penalties.

### B. Rep finalization and penalties

#### `training/engine/RepCounter.kt`

Main role:

- Final rep score is finalized here.

Important behavior:

- Calls `ScoreCalculator.calculateRepScore(...)`
- Applies position penalties after the base score:
  - position errors: `15f` per error
  - warning ids: `6f` per warning id
- Stores final score in `RepResult.score`
- Adds score to `totalScore`

Key consequence:

- `RepResult.score` is not just the raw state score.
- It is the final post-penalty rep quality score.

### C. Session summary generation

#### `training/TrainingEngine.kt`

When training stops:

- Builds `SessionSummary`
- Sets:
  - `averageScore = repCounter.getAverageScore()`
  - `cleanRatio`
  - `repDetails = repCounter.repResults`

This is the first session-level rollup of rep scoring.

### D. Report summary generation

#### `training/report/ReportGenerator.kt`

Important flow:

1. `generateStateSummary(...)`
2. `findBestRepsByScore(...)`
3. `findWorstRep(...)`
4. `generateStateTimeline(...)`
5. `calculateOverallQuality(...)`
6. Create `PostTrainingReport`

Key score mappings:

- `PerformanceSummary.averageScore = summary.averageScore`
- `RepTimelineEntry.score = rep.score`
- `BestRepHighlight.score = rep.score`
- `WorstRepHighlight.score = rep.score`

### E. Overall quality calculation

#### `training/report/ReportGenerator.kt`

`calculateOverallQuality(...)` uses three legs:

- `formScore`
- `safetyScore`
- `controlScore`

#### Form leg

Source:

- `calculateFormScoreForOverall(...)`

Behavior:

- If `repTimeline` exists, uses average of `timeline.map { it.score }`
- Otherwise falls back to state-distribution scoring

This means the form leg is closely tied to average rep scores.

#### Safety leg

Source:

- `calculateSafetyScoreForOverall(...)`

Behavior:

- Starts from `100`
- Applies penalties based on:
  - warning event count
  - danger rep count

Important:

- This safety formula is **not the same** as the safety card formula in `PerformanceMetricsBuilder`.

#### Control leg

Source:

- `calculateControlScoreForOverall(...)`
- Delegates to `PerformanceMetricsBuilder.calculateControlScoreValue(...)`

Behavior:

- Uses consistency, fatigue, tempo, velocity loss, and form consistency

Important:

- Control is relatively unified between overall quality and the control card.

## Persistence and Storage

### Local report persistence

#### `storage/ReportStorage.kt`

Storage model:

- Persists `PostTrainingReport` as JSON via Gson

Persisted score-bearing fields include:

- `summary.averageScore`
- `summary.rating`
- `repTimeline[].score`
- `bestReps[].score`
- `worstRep.score`
- `overallQuality.score`
- `overallQuality.rating`

### Engine/session model storage layers

#### `training/models/TrainingSession.kt`

Transient runtime models:

- `SessionSummary`
- `RepResult`

#### `training/session/SessionTrainingEngine.kt`

Session-mode aggregates:

- `RepDetail.score`
- `SetMetrics.formScore`
- `ExerciseReport.averageFormScore`
- `SessionReport.averageFormScore`
- Also parallel `accuracy` fields

Important:

- Session mode has its own aggregation layer separate from `PostTrainingReport`.

### Analytics storage layer

#### `training/analytics/MotionRecorder.kt`

Behavior:

- Stores form score into analytics/rep metrics on a `x10` scale

Meaning:

- This is another storage representation of score for analytics/metrics calculations.

### Backend/network model storage

#### `network/ReportsModels.kt`

Backend metrics carry separate score fields:

- `overallFormScore`
- `averageFormScore`
- `averageAccuracy`
- `RepMetrics.formScore`

These are used in reports hub and other summaries.

#### `network/AssessmentModels.kt`

Assessment-related score fields:

- `bodyScore`
- `mobilityScore`
- `controlScore`
- `symmetryScore`
- `safetyScore`

#### `network/LevelProfileModels.kt`

Level-profile score fields:

- `bodyScore`
- `DomainLevelData.score`
- `RegionLevelData.score`

## Report UI Audit

## Screen 1: Hero / Exercise Summary

### Live screen

Files:

- `ui/report/RepPagerAdapter.kt`
- `ui/report/ReportPageFragment.kt`
- `ui/report/MetricDisplayBuilder.kt`

Actual live flow:

- `RepPagerAdapter` creates `ReportPageFragment`
- `ReportPageFragment.bindData()` calls `MetricDisplayBuilder.buildPrimaryStats(report, isArabic)`

Displayed quality value:

- `report.overallQuality?.score ?: report.summary.averageScore`

Displayed label/status:

- Derived from that chosen float through `MetricDisplayBuilder.getStatusFromScore(...)`

Conclusion:

- The first report screen currently prefers the composite `overallQuality` score, not the average rep score.

### Legacy/alternate hero component

File:

- `ui/report/components/HeroSection.kt`

Behavior:

- Displays `summary.averageScore`
- Uses `summary.rating` (`PerformanceRating`)

Important:

- This component is not the one used by the V2 pager flow.
- It represents an alternate score story that differs from the actual live Hero screen.

## Screen 2: Performance Overview

File:

- `ui/report/PerformanceOverviewFragment.kt`

### Session score badge

Displayed score:

- `report.overallQuality?.score ?: averageSectionScore(metrics)`

Important:

- Its fallback is **not** `summary.averageScore`
- Fallback is the mean of:
  - Form card score
  - Safety card score
  - Control card score

### Rating label

Displayed rating:

- `report.overallQuality?.rating ?: QualityRating.fromScore(overallScore)`

Important:

- This uses `QualityRating`, not `PerformanceRating`

### Chart

File:

- `ui/report/components/RepsJourneyChart.kt`

Displayed values:

- Each point uses `RepTimelineEntry.score`
- Average line uses arithmetic mean of `timeline.map { it.score }`

Conclusion:

- The chart is aligned with average rep score semantics.

## Screen 3: Best vs Worst

File:

- `ui/report/BestWorstComparisonFragment.kt`

Displayed values:

- Best rep score comes from `bestRep.score`
- Worst rep score comes from `worstRep.score`
- Fallback path uses `RepTimelineEntry.score`

Selection rules:

- Best is not purely max score:
  - prefers `CLEAN`
  - then higher score
  - then fewer issues
- Worst is not purely min score:
  - if any `DANGER` reps exist, selection is taken from that pool first
  - then lower score
  - then worse quality / more issues / timing tie-breaks

Conclusion:

- The score shown is still a rep score.
- The selected rep is influenced by additional logic beyond score alone.

## Screens 4-6: Form / Safety / Control details

Files:

- `ui/report/FormDetailsFragment.kt`
- `ui/report/SafetyDetailsFragment.kt`
- `ui/report/ControlFatigueFragment.kt`
- `training/report/PerformanceMetricsBuilder.kt`

Displayed score families:

- Form details -> `formCard.getCardScore()`
- Safety details -> `safetyCard.getCardScore()`
- Control details -> `controlCard.getCardScore()`

These are metric-card scores, not plain average rep score.

Important:

- These cards are derived metrics.
- They should not be assumed equal to `summary.averageScore`
- They should not be assumed equal to `overallQuality.score`

## Tips and Share

Files:

- `ui/report/TipsExportFragment.kt`
- `ui/report/ReportPagerActivity.kt`

Displayed/used score:

- `report.overallQuality?.score ?: report.summary.averageScore`

Conclusion:

- Tips/share follow the same composite-first policy as Hero.

## Quick Insight

File:

- `training/report/QuickInsightGenerator.kt`

Observed behavior:

- Uses `report.summary.averageScore` in several branches
- Does not primarily use `overallQuality.score`

Examples:

- celebration logic
- focus/improvement ranges

Conclusion:

- The narrative insight can be based on a different score than the Hero value shown to the user.

## Other UI and Product Surfaces

## Program session report

File:

- `ui/programs/ProgramSessionReportActivity.kt`

Important finding:

- The top session summary uses `avgAccuracy`
- The strongest/weakest exercise insight uses `averageFormScore`

Examples:

- hero badge/rating/message -> `avgAccuracy`
- session share text -> `avgAccuracy`
- exercise row quality label -> `averageAccuracy`
- strongest/weakest exercise -> `averageFormScore`

Conclusion:

- This screen mixes accuracy and form score inside the same experience.
- This is a separate inconsistency from the rich report itself.

## Reports hub / exercise list

File:

- `ui/reports/ReportsExercisesFragment.kt`

Displayed value:

- Backend `averageFormScore`
- If backend aggregate missing, client computes average of `ex.averageFormScore`

Conclusion:

- This screen uses backend form-score aggregates, not local `PostTrainingReport.overallQuality`.

## Assessment pipeline

Files:

- `assessment/engine/AssessmentEngine.kt`
- `assessment/engine/DomainScoreCalculator.kt`
- `assessment/engine/ConfidenceCalculator.kt`
- `assessment/ui/AssessmentResultActivity.kt`

Observed score families:

- Mobility domain:
  - `metrics.formCard.rom?.value`
  - fallback `summary.avgROM`
  - fallback `summary.averageScore`
- Control domain:
  - control card overall score
- Safety domain:
  - safety card overall score
- Symmetry domain:
  - form symmetry metric
- Confidence:
  - based partly on rep score standard deviation

Assessment result UI displays:

- `bodyScore`
- domain scores (`mobility`, `control`, `symmetry`, `safety`)

Important:

- `bodyScore` is a new score family above report scores.
- Assessment explicitly says the big body score is motivational and decisions should use domain scores below.

## Level profile

Files:

- `ui/level/LevelProfileActivity.kt`
- `network/LevelProfileModels.kt`

Displayed score families:

- `profile.bodyScore`
- `domainLevels[].score`
- `regionLevels[].score`

Conclusion:

- Level profile is another layer using backend/body-scan derived scoring, not report `overallQuality` or average rep score.

## Confirmed Inconsistencies

## Finding 1: The report headline score is not the average of rep scores

Affected files:

- `ui/report/MetricDisplayBuilder.kt`
- `ui/report/ReportPageFragment.kt`

Reason:

- First screen prefers `overallQuality.score`
- `overallQuality.score` is a weighted composite, not average rep score

Impact:

- The headline can differ from:
  - chart average
  - best/worst rep scores
  - `summary.averageScore`

## Finding 2: Overview session score can diverge from Hero

Affected file:

- `ui/report/PerformanceOverviewFragment.kt`

Reason:

- Overview uses `overallQuality.score`
- If missing, it falls back to `averageSectionScore(metrics)`
- Hero falls back to `summary.averageScore`

Impact:

- Hero and Overview can disagree even when `overallQuality` is absent

## Finding 3: Quick insight is driven by a different score family

Affected file:

- `training/report/QuickInsightGenerator.kt`

Reason:

- Insight uses `summary.averageScore`
- Hero uses `overallQuality.score` when available

Impact:

- The text message can describe the session using one score while the UI shows another

## Finding 4: Two different rating systems are active

Affected file:

- `training/report/PostTrainingReport.kt`

Reason:

- `PerformanceRating` and `QualityRating` use different semantics and thresholds

Impact:

- Labels like `Excellent`, `Good`, and `Fair` may mean different things depending on screen

## Finding 5: Overall safety and Safety card are not the same formula

Affected files:

- `training/report/ReportGenerator.kt`
- `training/report/PerformanceMetricsBuilder.kt`

Reason:

- `overallQuality` safety leg uses warning/danger penalties
- safety card uses a different blended metric strategy

Impact:

- "Overall session quality" and "Safety score" can drift in ways that are not intuitive

## Finding 6: Program session report mixes accuracy and form quality

Affected file:

- `ui/programs/ProgramSessionReportActivity.kt`

Reason:

- Top summary uses `avgAccuracy`
- strongest/weakest insight uses `averageFormScore`

Impact:

- Same screen uses two score families as if they were one

## Finding 7: Legacy `HeroSection` tells a different story than live Hero

Affected file:

- `ui/report/components/HeroSection.kt`

Reason:

- It uses `summary.averageScore` and `PerformanceRating`
- Current live Hero uses `overallQuality` and `MetricStatus`

Impact:

- If reused later, it will reintroduce inconsistency

## Finding 8: Assessment and level profile introduce separate top-level scores

Affected files:

- `assessment/ui/AssessmentResultActivity.kt`
- `ui/level/LevelProfileActivity.kt`

Reason:

- Use `bodyScore`, domain scores, and level profile scores

Impact:

- Product contains several top-level score concepts with different semantics

## Single Source of Truth Status

There is a partial single-source-of-truth structure, but only at some levels.

### What is mostly unified

- Per-rep form score:
  - `RepResult.score`
  - reused in timeline
  - reused in best/worst
  - reused for average rep score

### What is not unified

- Session headline score
- Session rating label
- Safety scoring logic
- Program session summary semantics
- Assessment/body score semantics

## Recommended Unification Directions

## Option A: Make the report headline equal to average rep score

If product intent is:

- "The first report score should equal the average of rep ratings"

Then the canonical value should be:

- `report.summary.averageScore`

And the following should be aligned to it:

- `ui/report/MetricDisplayBuilder.kt`
- `ui/report/PerformanceOverviewFragment.kt` session summary badge
- `training/report/QuickInsightGenerator.kt`
- `ui/report/TipsExportFragment.kt`
- `ui/report/ReportPagerActivity.kt` share text

`overallQuality` can still exist, but must be presented under a different label such as:

- `Session Quality Index`
- `Composite Quality`
- `Form/Safety/Control Composite`

## Option B: Keep composite score, but stop calling it the same thing

If product intent is:

- "The first screen should show a richer session-quality score, not just average reps"

Then:

- Hero and Overview should consistently use `overallQuality`
- Chart and best/worst should continue using rep scores
- UI labels must explicitly distinguish:
  - `Average Rep Score`
  - `Composite Session Quality`
- Quick insight must be changed to use the same family as the headline, or explicitly state that it is talking about average rep score

## Option C: Fix session-mode summary separately

`ProgramSessionReportActivity` should choose one:

- quality = `averageFormScore`
- validity/completion = `averageAccuracy`

But it should not brand both as the same "quality" concept on one screen.

## Minimal Safe Refactor Plan

1. Decide one canonical report headline score.
2. Standardize one rating enum for that headline.
3. Rename other score families in UI to their actual meaning.
4. Document the meaning of:
   - `averageScore`
   - `overallQuality`
   - `accuracy`
   - `bodyScore`
5. Add a small internal architecture note so future screens do not accidentally mix them again.

## Bottom Line

The current implementation contains **multiple legitimate score systems**, but they are not consistently labeled or separated.

The most important confirmed mismatch is:

- `summary.averageScore` = average of rep ratings
- `overallQuality.score` = composite report score
- both are currently presented as if they were interchangeable in different parts of the report

So the core concern is confirmed:

- there is more than one active source of evaluation
- some UI surfaces mix them
- and this can produce incorrect or misleading interpretation unless the app chooses one canonical headline score or clearly names each score family
