# Planned Workout Details & Customization (Day View) Redesign Plan

## 1. Primary Goal
Transform the "Day View" (`ProgramWorkoutActivity`) into a highly interactive, premium training dashboard. The user should be able to clearly see their planned workouts, understand the exercises at a glance (with images and specific metrics), and easily customize the workout (edit, swap, reorder) through a smart, context-aware interface.

## 2. Key UI/UX Components

### A. The Master Header
- Keep the clean header from the previous steps, showing the Day title and a clear "Edit Mode" toggle button.

### B. Planned Workout Cards (`item_day_workout_card.xml`)
- When collapsed: Looks like the clean cards we designed earlier.
- When expanded: Reveals a beautiful vertical timeline.
- **Edit Mode Bar**: Should feel integrated, not like an afterthought. Clear icons for Rename, Delete, and Reorder (Up/Down) the entire workout run.

### C. Timeline Items (`item_workout_timeline_item.xml`)
- **Visuals**: A prominent thumbnail of the exercise.
- **Metrics**: Clear typography differentiating the exercise name from its metrics (e.g., "3 Sets x 10 Reps • 20 kg").
- **Edit State**: When edit mode is active, smoothly slide in the edit/delete/drag icons without breaking the layout.

### D. Smart Edit Dialog (`dialog_workout_line_item_edit.xml`)
This is the most critical fix. The current dialog is a generic list of inputs.
- **Smart Fields**: 
  - If the exercise is *time-based* (e.g., Plank), hide the "Reps" field and show "Duration".
  - If it's *rep-based* (e.g., Pushups), hide "Duration" and show "Reps".
  - If it's *bodyweight*, hide the "Weight" field.
- **Layout**: Use a modern Bottom Sheet. Place inputs side-by-side (e.g., Sets next to Reps) to save vertical space.
- **Exercise Selector**: Replace the basic Spinner with a modern `AutoCompleteTextView` (Exposed Dropdown Menu) for a premium feel.

## 3. Implementation Steps

1. **Update `dialog_workout_line_item_edit.xml`**: Restructure into a clean, grid-like form using `TextInputLayout`.
2. **Update `item_workout_timeline_item.xml`**: Make the exercise thumbnail larger, improve typography.
3. **Refactor `ProgramWorkoutActivity.kt` (Edit Logic)**:
   - Modify `showEditExerciseDialog` to listen to the selected exercise.
   - Dynamically toggle the visibility of the Reps, Duration, and Weight input fields based on the `ExerciseConfig.repCountingConfig` and `supportsWeight` properties.
4. **Update `item_day_workout_card.xml`**: Refine the padding and shadows to match the premium aesthetic.