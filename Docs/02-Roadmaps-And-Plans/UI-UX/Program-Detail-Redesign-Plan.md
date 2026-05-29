# Program Detail Page Redesign Plan

## 1. Primary Goal
Transform the "Program Details" page from a basic informational list into an immersive, inspiring landing page for a training program. It should clearly communicate the value of the program (via images and descriptions) and explicitly break down its structure (Weeks > Days > Sessions) so the user knows exactly what to expect before starting.

## 2. Key UI/UX Components

### A. Immersive Hero Header (Collapsing Toolbar)
- **Visuals**: A large, high-quality cover image occupying the top 30-40% of the screen.
- **Behavior**: As the user scrolls down to read the weeks, the image collapses gracefully into a standard top app bar with the program title.
- **Overlay**: A dark gradient overlay at the bottom of the image to ensure text readability (Program Name and Difficulty).

### B. Overview & Quick Stats
- **Stats Row**: A clean row of icons + text directly below the header showing:
  - ⏳ Duration (e.g., 4 Weeks)
  - 📅 Total Days (e.g., 20 Days)
  - 🏋️ Sessions (e.g., 12 Sessions)
  - ⚡ Difficulty (e.g., Advanced)
- **Description**: A well-formatted, readable description explaining the program's goals. If long, it can be truncated with a "Read More" option.

### C. Primary Call to Action (CTA)
- A highly visible, floating or prominent button: **"Start Program"** (if not enrolled) or **"Resume Program"** (if already active). 
- *Currently, we have a "Weekly Report" button which should be secondary or moved.*

### D. The Journey (Syllabus Structure)
Instead of a flat list, we will visualize the program's structure clearly:
- **Week Cards (Expandable/Accordion)**:
  - **Header**: "Week 1: Base Movement" • 5 Sessions, 2 Rest.
  - **Content (when expanded)**: A horizontal scrollable list or a compact vertical timeline of the **Days** within that week.
- **Day Items**:
  - Shows "Day 1" -> "Upper Body Focus" -> "2 Sessions (45 mins)".
  - Visual indicator if it's a "Rest Day" (e.g., a distinct color or icon).

## 3. Implementation Steps

1. **Layout Overhaul (`activity_program_detail.xml`)**:
   - Wrap the entire screen in a `CoordinatorLayout`.
   - Use `AppBarLayout` and `CollapsingToolbarLayout` for the hero image effect.
   - Use a `NestedScrollView` for the scrollable content (Overview + Weeks Recycler).

2. **Item Layouts**:
   - Update `item_program_week.xml` to look like a modern card or a timeline header.
   - Create a new `item_program_day_compact.xml` to represent individual days inside a week.

3. **Logic Update (`ProgramDetailActivity.kt`)**:
   - Bind the new UI components.
   - Update the `WeekAdapter` to handle expanding/collapsing.
   - Nest a horizontal `RecyclerView` (or dynamically add views) inside the `WeekAdapter` to show the days.
   - Add a dummy/placeholder image loading mechanism for the cover image (since actual images might not be in the mock data yet).