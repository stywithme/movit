# Reports revamp plan

## Goal

Turn the bottom-nav Reports screen from a set of isolated charts into a coach-style dashboard that helps trainees understand:

- what they achieved
- whether they are improving
- where form breaks down
- which exercises need attention
- what to do next

## Current baseline

- Entry point: `HistoryFragment` under the bottom-nav `Reports` item.
- Android data source: `ReportRepository.getProgramMetrics(activeProgram.id, includeChildren = true)`.
- Backend endpoint: `GET /api/mobile/reports/metrics`.
- Current correction: the Reports hub must aggregate both `ProgramSessionReport` and `TrainingSession`.
- Program reports are included when the trainee is enrolled in a program.
- Free single-exercise, quick-start, and workout/explore sessions are included even when no program is active.

## UX structure

1. Overview
   - overall score
   - weekly progress
   - top insights
   - next best action
2. Progress
   - form score trend
   - volume/reps trend
   - attendance and consistency
3. Exercises
   - per-exercise score cards
   - strongest/weakest movement
   - recent trend and focus area
4. Sessions
   - session timeline
   - drill-down into session reports
5. Records
   - personal bests
   - milestones
   - streaks

## Visual component system

- `ReportHeroGauge`: overall training score and status.
- `MetricDeltaCard`: number, previous-period delta, and meaning.
- `CoachInsightCard`: short coaching insight with action.
- `TrendChartCard`: reusable chart shell with metric selector.
- `ExerciseProgressCard`: exercise score, trend, reps, and focus area.
- `SessionTimelineCard`: date, duration, form score, best/weakest exercise.
- `StateDistributionBar`: perfect/normal/warning/danger distribution.
- `MilestoneBadge`: shareable achievement card.

Reuse report-session components where possible:

- `HeroSection`
- `PerformanceCard`
- `QuickInsightCard`
- `StateDistributionBar`
- `KeyMomentsSection`
- `RepsJourneyChart`

## Data contract direction

Add a mobile dashboard endpoint that can aggregate across both report pipelines:

`GET /api/mobile/reports/dashboard`

Suggested query:

- `period`: `7d | 30d | 90d | program | all`
- `source`: `all | program | free | workout | quick | explore`
- `programId`: optional
- `exerciseSlug`: optional

Suggested response groups:

- `summary`
- `trends`
- `exerciseBreakdown`
- `sessionTimeline`
- `records`
- `insights`

## Implementation phases

1. Stabilize the existing hub states.
   - Loading, empty, no active program, locked, error, success.
   - Do not let child tabs show blank pages when the data layer fails.
2. Rename/restructure the Reports hub.
   - Move from `HistoryFragment` naming to `ReportsFragment`.
   - Keep bottom-nav behavior stable.
3. Add dashboard data contract.
   - Backend aggregates `ProgramSessionReport` and `TrainingSession`.
   - Android adds typed models and repository methods.
4. Build the new dashboard feed.
   - Start with Overview and Exercises.
   - Add drill-down cards after the data contract is stable.
5. Add session/exercise detail navigation.
   - Open existing `SessionReportActivity` where possible.
   - Add exercise-level report screen for longitudinal analysis.
