# Week Plan (ProgramDayActivity) Redesign Plan

## 1. Primary Goal
Align the "Week Plan" page with the new modern design language established in the `Train` and `Program Details` pages. The focus is on clarity, visualizing progress, and using premium, interactive components for the planned workouts.

## 2. Key UI/UX Components

### A. Clean Header
- **Top Bar**: Simple "Week Plan" title with a back button.
- **Hero Title**: Large typography displaying "Week [X]" and a subtitle showing the week's focus or progress.

### B. Day Sections (Grouping)
- Instead of wrapping an entire day (and its planned workouts) in a single massive card, we will treat **Days as Section Headers**.
- **Header Design**: A clean row with the Day number (e.g., "Day 1"), the day's name/focus, and an icon indicating if it's a workout day or a rest day.

### C. Interactive Workout Cards
- We will reuse the design concept from the `TrainFragment` (`item_interactive_workout_card.xml`).
- Each planned workout will be a standalone `MaterialCardView` with rounded corners.
- **Content**: 
  - Icon on the left.
  - Workout name and details (e.g., "6 exercises • 25 min").
  - Status indicator (Checkmark if completed).
- **Behavior**: Clicking the card starts/resumes the planned workout. Completed planned workouts will appear slightly dimmed with a "Completed" status, but can still be clicked or have a secondary option to restart.

## 3. Implementation Steps

1. **Update `activity_program_day.xml`**:
   - Enhance the header with larger typography and better spacing.

2. **Update `item_program_day.xml`**:
   - Change from a `MaterialCardView` wrapper to a transparent `LinearLayout` acting as a section header for the day.

3. **Update `item_program_workout.xml`**:
   - Transform it into a modern `MaterialCardView` matching the `item_interactive_workout_card.xml` style.
   - Remove clunky default buttons; the entire card becomes clickable to start the planned workout.

4. **Refactor `ProgramDayActivity.kt`**:
   - Bind the new UI elements.
   - Calculate estimated duration if available.
   - Implement the visual logic for completed vs. pending planned workouts (alpha changes, icon visibility).