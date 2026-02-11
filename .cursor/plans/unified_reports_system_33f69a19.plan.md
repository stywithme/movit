---
name: Unified Reports System
overview: خطة شاملة لتوحيد نظام التقارير على مستوى Backend و Mobile، بحيث تُبنى كل المقاييس من النواة الذرية (Rep) وتتصاعد تلقائياً عبر كل المستويات، مع تجربة مستخدم احترافية تغطي كل شاشات التطبيق.
todos:
  - id: backend-metrics-engine
    content: "Phase 1: Build unified metrics aggregation engine on backend (single endpoint, scope-based filtering, all levels from Rep to Program)"
    status: completed
  - id: dashboard-enhancement
    content: "Phase 2: Enhance ProgramsFragment dashboard with unified endpoint, sparkline, Program Grade, and heatmap dots on calendar"
    status: completed
  - id: session-report-enhancement
    content: "Phase 3: Enhance Session Report screen with comparisons, celebration animations, deltas, and insights"
    status: completed
  - id: reports-hub
    content: "Phase 4: Build new Reports Hub screen with 4 tabs (Overview, Exercises, Trends, Records)"
    status: completed
  - id: detail-screens
    content: "Phase 5: Build Day Detail, enhance Week Detail, and build Exercise History screens"
    status: completed
  - id: advanced-features
    content: "Phase 6: Insights engine, milestone notifications, shareable reports, PDF export"
    status: completed
isProject: false
---

# Unified Reports System - Professional Plan

---

## Part 1: The Core Philosophy - "One Truth, Many Views"

The entire reporting system is built on one principle: **every metric in the app traces back to a single Rep**. A Rep is the atom. Everything above it (Set, Exercise, Session, Day, Week, Program) is simply a **filtered aggregation** of Reps.

This means:

- There is ONE calculation engine on the backend
- There is ONE endpoint that serves all report needs
- The mobile app never computes metrics -- it only **displays** what the backend returns
- Every screen in the app (Dashboard, Day view, Week view, Session report, Exercise history) calls the same endpoint with different **scope** and **filters**

---

## Part 2: The Metrics Hierarchy

### Level 0 -- Rep (The Atom)

Every metric originates here. This is what gets captured during training:

- **Form Score** (0-100): Quality of the movement based on joint states
- **Duration** (ms): Time taken for this rep
- **Worst State**: The worst joint state reached (PERFECT/NORMAL/PAD/WARNING/DANGER)
- **Is Counted**: Whether this rep was valid
- **Phase Timings**: Eccentric / Concentric / Isometric durations (when available)
- **Joint Angles**: Raw angles at key points (for ROM calculation)

### Level 1 -- Set (Aggregation of Reps)


| Metric | How It Is Calculated | Category |
| ------ | -------------------- | -------- |


Actually, let me use bullet lists instead:

**Core Metrics:**

- **Completion Rate**: (counted reps / target reps) x 100
- **Average Form Score**: Mean of all rep scores in this set
- **Total Reps**: Count of valid reps
- **Set Duration**: Sum of rep durations + transition gaps
- **Weight**: User-confirmed weight for this set

**Advanced Metrics:**

- **Time Under Tension (TUT)**: Total active contraction time (sum of rep durations excluding rest)
- **Range of Motion (ROM)**: Max angle - Min angle from primary joint across all reps
- **Fatigue Index**: The rep number where form score dropped below 80% of the first rep's score -- tells the user "you started losing form at rep 8"
- **Form Consistency**: Standard deviation of rep scores -- low = consistent, high = inconsistent
- **Concentric Velocity**: Average speed of the positive phase (from phase timings)
- **Tempo Adherence**: How closely the user followed the prescribed tempo (if set)

### Level 2 -- Exercise (Aggregation of Sets)

**Core Metrics:**

- **Average Form Score**: Weighted average across sets (later sets weighted less if fatigue detected)
- **Average Completion Rate**: Mean completion across sets
- **Total Volume**: Weight x Reps x Sets (for weighted exercises)
- **Sets Completed / Planned**: Completion status
- **Total Reps**: Sum across all sets

**Advanced Metrics:**

- **Best Set**: The set with the highest form score
- **Drop-off Rate**: How much form score decreased from set 1 to last set (fatigue indicator)
- **Weight Progression**: The weight used in each set (for progressive overload tracking)
- **Symmetry Score**: For bilateral exercises -- left vs right performance difference
- **Estimated 1RM**: Calculated from weight and reps (Epley or Brzycki formula)

**Comparison Metrics (requires history):**

- **vs. Last Session**: Form score change, volume change, rep count change for this same exercise
- **vs. Personal Best**: Distance from the user's best-ever session for this exercise
- **Trend Direction**: Improving / Stable / Declining (based on last 5 sessions)

### Level 3 -- Session (Aggregation of Exercises)

**Core Metrics:**

- **Total Duration**: Wall-clock time from start to finish
- **Exercises Completed / Total**: Session completion
- **Total Sets / Reps**: Aggregate counts
- **Average Accuracy**: Mean completion rate across exercises
- **Average Form Score**: Mean quality across exercises
- **Session Rating**: Derived label (Excellent / Good / Solid / Keep Practicing)

**Advanced Metrics:**

- **Muscle Group Coverage**: Which muscle groups were trained and their relative volume
- **Energy Expenditure Estimate**: Based on duration, reps, and exercise types
- **Strongest Exercise**: The exercise with the highest form score
- **Weakest Exercise**: The exercise with the lowest form score (improvement opportunity)

### Level 4 -- Day (Aggregation of Sessions)

**Core Metrics:**

- **Sessions Completed / Planned**: Day completion status
- **Total Training Time**: Sum of all session durations
- **Average Form Score**: Across all sessions
- **Day Rating**: Overall quality label
- **Is Rest Day**: Whether this is a scheduled rest day

**Insight Metrics:**

- **Day Discipline Score**: Did the user complete all planned sessions?
- **Time of Day**: When training happened (morning/afternoon/evening -- for habit tracking)

### Level 5 -- Week (Aggregation of Days)

**Core Metrics:**

- **Days Trained / Total Training Days**: Attendance rate
- **Total Training Time**: Sum for the week
- **Average Form Score**: Weekly quality
- **Total Volume**: Sum of all exercise volumes
- **Total Reps**: Sum across the week

**Trend Metrics:**

- **Accuracy Trend**: Day-by-day form score as a mini series (for sparkline chart)
- **Volume Trend**: Daily volume progression
- **Consistency Score**: How evenly distributed was training across the week
- **Week-over-Week Change**: Comparison with the previous week on key metrics (form score, volume, attendance)

### Level 6 -- Program (Aggregation of Weeks)

**Core Metrics:**

- **Program Progress**: (days completed / total program days) x 100
- **Days Trained**: Total training days completed
- **Total Training Time**: Lifetime for this program
- **Overall Form Score**: Across all training history
- **Overall Volume**: Total weight lifted
- **Current Streak**: Consecutive training days without missing

**Progress Metrics:**

- **Week-by-Week Trend**: Form score per week as a series
- **Improvement Rate**: Percentage improvement from Week 1 to current week
- **Best Week**: The week with highest form score
- **Predicted Completion Date**: Based on current pace
- **Program Grade**: Overall letter grade (A+ to D) based on attendance + form score + consistency

---

## Part 3: The Single Unified Endpoint

### Design: `GET /api/reports/metrics`

One endpoint. All reports. The magic is in the **scope** parameter.

**Parameters:**

- `programId` (required): Which program
- `scope` (required): `program` | `week` | `day` | `session` | `exercise`
- `weekNumber` (optional): Filter to specific week
- `dayNumber` (optional): Filter to specific day
- `sessionId` (optional): Filter to specific session
- `exerciseSlug` (optional): Filter to specific exercise
- `includeHistory` (optional, boolean): Include historical comparison data
- `includeChildren` (optional, boolean): Include breakdown of child level (e.g., scope=week includes day-level breakdown)

**How it works:**

1. Backend fetches all `ProgramSessionReport` records matching the filters
2. Parses the stored JSON `report` field to access Rep-level data
3. Applies aggregation logic based on scope
4. Returns a standardized `MetricsResponse` containing:
  - `summary`: The aggregated metrics for the requested scope
  - `breakdown` (if includeChildren): Array of child-level summaries
  - `comparison` (if includeHistory): Previous period metrics for delta calculation
  - `trends`: Time-series data for charting (form score over time, volume over time)

**Examples of how different screens call this:**

- **Dashboard**: `scope=program&includeChildren=true` (gets program summary + week-by-week)
- **Week Calendar**: `scope=week&weekNumber=2&includeChildren=true` (gets week summary + day-by-day)
- **Day Detail**: `scope=day&weekNumber=2&dayNumber=3&includeChildren=true` (gets day summary + session-by-session)
- **Session Report**: `scope=session&sessionId=xxx&includeChildren=true` (gets session summary + exercise-by-exercise)
- **Exercise History**: `scope=exercise&exerciseSlug=bicep_curl&includeHistory=true` (gets exercise metrics + all historical sessions for this exercise)

---

## Part 4: Mobile App -- Screen-by-Screen Plan

### Screen 1: Programs Dashboard (ProgramsFragment)

**Current State:** Shows basic metrics (days trained, streak, form score)

**Enhanced Design:**

**Section A -- Hero Card:**

- Program name and progress percentage with circular progress indicator
- Current position: "Week 2 of 4 -- Day 3"
- Large, prominent Program Grade (A+, B, etc.)
- Streak flame icon with count

**Section B -- Week Calendar (7 days, Sat-Fri):**

- Each day circle shows: Completed (green check) / Missed (red X) / Rest (gray dash) / Today (outlined) / Future (dimmed)
- Below each day: mini form score dot (green/yellow/red) for visual heatmap
- Tapping a day opens Day Detail

**Section C -- Quick Stats Row (4 cards):**

- Days Trained (with attendance %)
- Average Form Score (with trend arrow up/down)
- Total Volume (kg)
- Total Training Time

**Section D -- Weekly Sparkline:**

- Small line chart showing form score trend for the current week
- Below it: "vs. Last Week: +5%" or similar comparison text

**Section E -- Today's Plan:**

- Remaining sessions for today
- Each session card shows: exercise count, estimated time
- If all done: celebration banner

**Data Source:** `scope=program&includeChildren=true` (one call gets everything)

---

### Screen 2: Reports Hub (NEW Main Screen -- Tab in Bottom Navigation)

This is the crown jewel. A dedicated full-screen reports experience.

**Tab A -- Overview:**

- **Program Progress Card**: Visual timeline of weeks with completion status
- **Key Numbers**: 4 large metric cards (Total Workouts, Total Reps, Total Volume, Total Time)
- **Form Score Journey**: Line chart showing weekly average form score from week 1 to now
- **Consistency Calendar**: Month-view calendar heatmap (like GitHub contribution graph) showing training intensity by color

**Tab B -- Exercises:**

- List of all exercises the user has trained
- Each exercise card shows: Exercise name, times trained, current form score, trend arrow
- Tapping opens Exercise Detail View:
  - Line chart: Form score over time (each session is a data point)
  - Line chart: Volume over time (if weighted)
  - Personal records: Best form score, heaviest weight, most reps
  - Last session details
  - "Weakest Set" insight: which set number tends to have the lowest score

**Tab C -- Trends:**

- **Improvement Rate Chart**: Overall form score progression over the program (big line chart)
- **Volume Progression Chart**: Total weekly volume over time
- **Attendance Chart**: Training days per week over time
- **Fatigue Pattern**: Average form score by set number across all sessions (shows when the user typically fatigues)
- **Best Training Time**: Chart showing performance by time-of-day (morning vs evening)

**Tab D -- Records:**

- **Personal Bests**: List of achievements
  - Highest form score ever (and when)
  - Longest streak
  - Heaviest weight lifted (per exercise)
  - Most reps in a single set
  - Longest training session
  - Best week overall
- **Milestones**: "First 100 reps", "First perfect set", "7-day streak" etc.

**Data Source:** Multiple calls to the unified endpoint with different scopes

---

### Screen 3: Session Report (Post-Training -- ProgramSessionReportActivity)

**Current State:** Basic summary with exercise breakdown

**Enhanced Design:**

**Phase 1 -- Celebration (1-2 seconds):**

- Full-screen animation based on session quality
- Excellent (85+): Confetti / fireworks
- Good (70-84): Stars animation
- Solid (50-69): Thumbs up
- Below 50: Encouraging "Every session counts" message

**Phase 2 -- Session Summary Card:**

- Large Session Rating label (Excellent / Good / Solid / Keep Practicing)
- 3 hero metrics in large font: Duration, Total Reps, Form Score
- "vs. Last Session" comparison below each metric (green up arrow or red down arrow)

**Phase 3 -- Exercise Breakdown:**

- Horizontal scrollable cards, one per exercise
- Each card: Exercise name, form score ring, sets progress (3/3), reps count
- Color-coded border (green/yellow/orange/red based on form score)
- Tap to expand: shows set-by-set breakdown with rep details

**Phase 4 -- Insights:**

- "Your strongest exercise: Bicep Curl (92%)"
- "Form dropped after Set 3 in Push-ups -- consider reducing weight"
- "You improved 8% on Squats since last session"
- These insights are generated by the backend based on comparison logic

**Phase 5 -- Actions:**

- Share (image of summary card)
- View Full Report (opens ReportPagerActivity)
- Done

**Data Source:** `scope=session&sessionId=xxx&includeChildren=true&includeHistory=true`

---

### Screen 4: Day Detail (Tapping a day in the week calendar)

**Design:**

- **Day Header**: "Tuesday, Feb 10" with Day Rating
- **Sessions List**: Each session as a card with: session name, form score, duration, completion status
- **Day Summary**: Total time, total reps, average form score
- **Rest Day View**: If it is a rest day, show motivational message and next training day countdown

**Data Source:** `scope=day&weekNumber=2&dayNumber=3&includeChildren=true`

---

### Screen 5: Week Detail (WeeklyReportActivity -- Enhanced)

**Current State:** Simple list of weeks with basic metrics

**Enhanced Design:**

- **Week Header**: "Week 2 of 4" with large progress bar
- **7-Day Bar Chart**: Vertical bars showing form score per day (visual rhythm of the week)
- **Week Summary**: Attendance (5/5 training days), Total Time, Total Volume, Average Form Score
- **Comparison Banner**: "vs. Week 1: Form +12%, Volume +8%, Attendance same"
- **Day-by-Day Breakdown**: Expandable list of each day with session details

**Data Source:** `scope=week&weekNumber=2&includeChildren=true&includeHistory=true`

---

### Screen 6: Exercise History (Tapping an exercise from Reports Hub)

**Design:**

- **Exercise Header**: Name, muscle group, total times trained
- **Performance Chart**: Line chart with form score trend over all sessions
- **Volume Chart** (if weighted): Weight progression over time
- **Personal Records**: Best form score, heaviest weight, most reps in a set
- **Session History**: Reverse-chronological list of every time this exercise was trained, with: date, form score, sets x reps, weight
- **Improvement Insights**: "Your form on this exercise has improved 23% since you started"

**Data Source:** `scope=exercise&exerciseSlug=bicep_curl&includeHistory=true`

---

## Part 5: Metrics Placement Map

What metrics appear WHERE:

**Programs Dashboard (Quick Glance):**

- Program Grade (A-D)
- Days Trained / Total
- Current Streak
- Average Form Score + trend arrow
- Weekly sparkline

**Reports Hub -- Overview (Deep Dive):**

- All program-level metrics
- Week-by-week trends
- Consistency calendar heatmap

**Reports Hub -- Exercises (Per-Exercise Analysis):**

- Form score trend per exercise
- Volume progression per exercise
- Personal records per exercise

**Reports Hub -- Trends (Time-Series Analysis):**

- Form score improvement over time
- Volume growth over time
- Attendance consistency over time
- Fatigue patterns

**Session Report (Post-Training Feedback):**

- Session rating
- Duration, Reps, Form Score (with vs. previous)
- Per-exercise form score
- Set-by-set breakdown
- Improvement insights

**Day Detail (Daily View):**

- Sessions completed
- Total time
- Average form score
- Per-session summary

**Week Detail (Weekly View):**

- Days completed
- Total time, volume, reps
- Daily form score bar chart
- Week-over-week comparison

---

## Part 6: Backend Architecture Decisions

**Calculation Strategy:**

- All metrics are computed server-side from stored RepDetail data
- The `report` JSON field in `ProgramSessionReport` already contains the full Rep-level data
- Backend reads this JSON, applies aggregation logic, and returns computed metrics
- This ensures consistency: mobile and any future web dashboard see identical numbers

**Caching Strategy:**

- Program-level and week-level metrics can be cached (invalidated when a new session report is submitted)
- Session-level metrics are immutable after completion (cache forever)
- Cache keys: `metrics:{userId}:{programId}:{scope}:{filters}`

**Historical Comparison Logic:**

- "vs. Last Session": Compare with the most recent completed session for the same exercise
- "vs. Last Week": Compare current week's aggregate with previous week
- "Trend": Calculate slope of form score over last N data points
- "Personal Best": Track max values per exercise per user

**Insights Engine (Phase 2):**

- Rule-based insights generated from metric comparisons:
  - "Form dropped after Set X" -> fatigue detection
  - "Improved X% since last session" -> positive reinforcement
  - "Left side weaker than right by X%" -> symmetry alert
  - "You train best in the morning" -> habit insight

---

## Part 7: Mobile Architecture Decisions

**Single Data Layer:**

- Create a `ReportRepository` that wraps the unified endpoint
- All screens call `ReportRepository.getMetrics(scope, filters)`
- Repository handles: caching (offline-first), network fetch, cache invalidation
- UI components are purely presentational -- no metric calculations

**Chart Library:**

- Adopt MPAndroidChart (or similar) for all visualizations
- Standardized chart components: LineChart, BarChart, RadarChart, SparklineView
- Consistent color scheme across all charts

**Offline Strategy:**

- Cache all fetched metrics responses locally
- On app open: show cached data immediately, fetch fresh data in background
- When fresh data arrives: animate the transition if metrics changed

---

## Part 8: Implementation Phases

**Phase 1 -- Foundation (Backend):**

- Build the unified metrics aggregation engine on the backend
- Create the single endpoint with scope-based filtering
- Ensure all existing RepDetail data is accessible for computation
- Add historical comparison logic

**Phase 2 -- Dashboard Enhancement (Mobile):**

- Update ProgramsFragment to use the unified endpoint
- Add sparkline chart for weekly trend
- Add Program Grade display
- Improve week calendar with form score heatmap dots

**Phase 3 -- Session Report Enhancement (Mobile):**

- Enhance ProgramSessionReportActivity with comparison data
- Add celebration animations
- Add "vs. previous" deltas on metrics
- Add AI-generated insights section

**Phase 4 -- Reports Hub (Mobile -- New Screen):**

- Build the dedicated Reports screen with 4 tabs
- Implement Overview tab with key charts
- Implement Exercises tab with per-exercise history
- Implement Trends tab with progression charts
- Implement Records tab with personal bests

**Phase 5 -- Detail Screens (Mobile):**

- Build Day Detail bottom sheet / screen
- Enhance WeeklyReportActivity with charts and comparisons
- Build Exercise History screen with performance charts

**Phase 6 -- Advanced Features:**

- Insights engine on backend
- Push notifications for milestones ("New personal best!")
- Export reports as shareable images
- PDF report generation

