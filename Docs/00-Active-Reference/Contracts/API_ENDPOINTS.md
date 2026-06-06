# POSE System API Documentation

**Base URL:** `http://localhost:4000/api`

---

## 🔐 Admin Authentication (`/admin/auth`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| POST | `/admin/auth/login` | Login to admin dashboard |
| POST | `/admin/auth/logout` | Logout and clear cookies |
| GET | `/admin/auth/profile` | Get current logged-in admin profile |
| PUT | `/admin/auth/profile` | Update admin profile |
| POST | `/admin/auth/request-reset` | Request password reset email |
| POST | `/admin/auth/reset-password` | Reset password using token |

---

## � Mobile User Authentication (`/mobile/auth`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| POST | `/mobile/auth/register` | Create a new user account |
| POST | `/mobile/auth/login` | Login with email and password |
| POST | `/mobile/auth/google` | Social login/register with Google |
| POST | `/mobile/auth/refresh` | Refresh access token using refresh token |
| POST | `/mobile/auth/logout` | Logout (revoke refresh token) |
| DELETE | `/mobile/auth/logout` | Logout from all devices (revoke all tokens) |
| GET | `/mobile/auth/profile` | Get current user profile |
| PATCH | `/mobile/auth/profile` | Update profile info |
| PATCH | `/mobile/auth/settings` | Update app settings (language, voice, etc.) |
| POST | `/mobile/auth/change-password` | Change password for logged-in user |
| POST | `/mobile/auth/forgot-password` | Request password reset email |
| POST | `/mobile/auth/reset-password` | Reset password with token |
| DELETE | `/mobile/auth/account` | Permanently delete user account |

---

## �👮 Admin Management (`/admins`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/admins` | List all admins (search, status filter, pagination) |
| POST | `/admins` | Create a new admin |
| GET | `/admins/:id` | Get admin details by ID |
| PUT | `/admins/:id` | Update admin info or status |
| PUT | `/admins/:id/password` | Update admin password |
| DELETE | `/admins/:id` | Delete (soft delete) an admin |

---

## 🔑 Permissions & Roles (`/admin/permissions`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/admin/permissions` | List all system permissions |
| GET | `/admin/permissions/roles` | List all roles |
| GET | `/admin/permissions/roles/:id` | Get role details with permissions |
| POST | `/admin/permissions/roles` | Create a new role |
| PUT | `/admin/permissions/roles/:id` | Update role name/permissions |
| DELETE | `/admin/permissions/roles/:id` | Delete a role |

---

## 🏋️ Exercises (`/exercises`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/exercises` | List exercises (search, status, category, pagination) |
| POST | `/exercises` | Create a new exercise |
| GET | `/exercises/published` | List only published exercises |
| GET | `/exercises/:id` | Get exercise details |
| PUT | `/exercises/:id` | Update exercise |
| DELETE | `/exercises/:id` | Delete an exercise |
| PUT | `/exercises/:id/publish` | Publish an exercise |
| DELETE | `/exercises/:id/publish` | Unpublish an exercise |
| GET | `/exercises/:id/config` | Get Android-compatible config for the exercise |

---

## 🏃 Workout Templates (`/workout-templates`)

Catalog presets (formerly `/workouts`). See [Workout-Domain-Naming.md](Workout-Domain-Naming.md).

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/workout-templates` | List workout templates |
| POST | `/workout-templates` | Create a template |
| GET | `/workout-templates/:id` | Get template details (includes exercises) |
| PUT | `/workout-templates/:id` | Update template |
| DELETE | `/workout-templates/:id` | Delete a template |
| POST | `/workout-templates/:id/publish` | Publish template |
| DELETE | `/workout-templates/:id/publish` | Unpublish template |
| POST | `/workout-templates/:id/duplicate` | Duplicate template |

---

## 📅 Programs (`/programs`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/programs` | List programs |
| POST | `/programs` | Create a new program |
| GET | `/programs/:id` | Get program full details (weeks, days, planned workouts) |
| PUT | `/programs/:id` | Update program metadata |
| DELETE | `/programs/:id` | Delete a program |
| POST | `/programs/:id/publish` | Publish program |
| POST | `/programs/:id/duplicate` | Duplicate a program |
| **Weeks** | | |
| POST | `/programs/:id/weeks` | Add a week to program |
| PUT | `/programs/:id/weeks/:weekId` | Update week info |
| DELETE | `/programs/:id/weeks/:weekId` | Delete a week |
| POST | `/programs/:id/weeks/:weekId/copy-to/:targetWeek` | Copy week structure to another week |
| **Days** | | |
| POST | `/programs/:programId/weeks/:weekId/days` | Add a day to a week |
| PUT | `/programs/:programId/weeks/:weekId/days/:dayId` | Update day info |
| DELETE | `/programs/:programId/weeks/:weekId/days/:dayId` | Delete a day |
| **Planned workouts** | | |
| POST | `/programs/:programId/weeks/:weekId/days/:dayId/planned-workouts` | Add planned workout to a day |
| PUT | `/programs/:programId/planned-workouts/:plannedWorkoutId` | Update planned workout |
| DELETE | `/programs/:programId/planned-workouts/:plannedWorkoutId` | Delete a planned workout |
| POST | `/programs/:programId/planned-workouts/:plannedWorkoutId/items` | Add item to planned workout |
| PUT | `/programs/:programId/planned-workouts/:plannedWorkoutId/items/:itemId` | Update planned workout item |
| DELETE | `/programs/:programId/planned-workouts/:plannedWorkoutId/items/:itemId` | Delete planned workout item |
| POST | `/programs/:programId/planned-workouts/:plannedWorkoutId/import-workout-template/:workoutTemplateId` | Import template exercises into planned workout |

---

## 📱 Mobile — Workout executions (`/mobile/workout-executions`)

Per-exercise runs and history (formerly `/mobile/sessions`).

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| POST | `/mobile/workout-executions` | Upload single exercise execution + `executionMetrics` |
| POST | `/mobile/workout-executions/explore` | Upload grouped explore/quick-start workout (`executions[]`) |
| GET | `/mobile/workout-executions` | List execution history |
| GET | `/mobile/workout-executions/exercise/:exerciseId` | Exercise history + aggregates |

## 📱 Mobile — Planned workouts (`/mobile/planned-workouts`)

Program block lifecycle (start/complete report).

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| POST | `/mobile/planned-workouts/:plannedWorkoutId/start` | Start planned workout report |
| POST | `/mobile/planned-workouts/:plannedWorkoutId/complete` | Complete planned workout + progression |
| POST | `/mobile/planned-workouts/:plannedWorkoutId/report` | Alias for complete (legacy clients) |

## 📱 Mobile — Workout templates (`/mobile/workout-templates`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/mobile/workout-templates` | Sync catalog templates |

## 📱 Mobile — Home & plan

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/mobile/home` | Home data; `todayWorkout`, `trainMode`, `recentWorkoutExecutions` |
| GET | `/mobile/user-programs/:id/today` | Today plan; `currentProgram.plannedWorkouts[]` |
| GET | `/mobile/user-programs/:id/effective-plan` | Merged template + overrides; `plannedWorkouts[]` |
| GET | `/mobile/progression/planned-workout/:plannedWorkoutId` | Progression changes for a completed block |

---

## 👤 App Users (`/users`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/users` | List mobile app users |
| POST | `/users` | Create a user manually |
| PUT | `/users/:id` | Update user info or Pro status |
| DELETE | `/users/:id` | Delete a user |

---

## ⚙️ Attributes & Config (`/attributes`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/attributes` | List all attribute types (Muscles, Equipment, etc.) |
| GET | `/attributes/lookup` | Get all lookup data for dropdowns (Categories, Joints, etc.) |
| GET | `/attributes/:code/values` | Get specific values for an attribute code (e.g., muscles) |
| POST | `/attributes/:code/values` | Add a new value to an attribute type |

---

## 📍 Pose Positions (`/pose-positions`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/pose-positions` | List available pose positions |
| POST | `/pose-positions` | Create a new pose position |
| GET | `/pose-positions/:id` | Get pose position details |
| PUT | `/pose-positions/:id` | Update pose position |
| DELETE | `/pose-positions/:id` | Delete a pose position |

---

## 🩺 Doctor Work Times (`/admin/doctor-work-time`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/admin/doctor-work-time` | List all work times |
| GET | `/admin/doctor-work-time/mine` | List doctor's own work times (Doctor only) |
| GET | `/admin/doctor-work-time/:adminId` | List work times for specific doctor |
| POST | `/admin/doctor-work-time` | Create a work time |
| PUT | `/admin/doctor-work-time/:id` | Update a work time |
| DELETE | `/admin/doctor-work-time/:id` | Delete a work time |

---

## 🏖️ Close Times (`/admin/close-time`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/admin/close-time` | List all close times |
| POST | `/admin/close-time` | Create a close time (adminId null = global) |
| PUT | `/admin/close-time/:id` | Update a close time |
| DELETE | `/admin/close-time/:id` | Delete a close time |

---

## 🗓️ Bookings Admin/Doctor (`/admin/bookings`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/admin/bookings` | List all bookings |
| GET | `/admin/bookings/mine` | List doctor's own bookings (Doctor only) |
| GET | `/admin/bookings/:id` | Get booking details |
| POST | `/admin/bookings` | Create a booking |
| POST | `/admin/bookings/follow-up` | Create a follow-up booking (Doctor only) |
| PUT | `/admin/bookings/:id` | Update a booking |
| PUT | `/admin/bookings/:id/status` | Update booking status (Doctor only) |
| PUT | `/admin/bookings/:id/notes` | Update booking notes (Doctor only) |
| DELETE | `/admin/bookings/:id` | Soft delete a booking |

---

## 📱 User Bookings (`/bookings`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/bookings/available-doctors` | Get doctors with available slots for a selected date |
| GET | `/bookings/available-slots/:adminId` | Get available slots for a specific doctor as `startAt`, `endAt`, `durationMinutes` |
| GET | `/bookings/my` | Get user's current and upcoming bookings with backend-calculated `allowedActions` |
| GET | `/bookings/history` | Get user's past/cancelled bookings with backend-calculated `allowedActions` |
| POST | `/bookings` | Create a new booking |
| PUT | `/bookings/:id/reschedule` | Reschedule a booking when `allowedActions.canReschedule` is true |
| PUT | `/bookings/:id/cancel` | Cancel a booking when `allowedActions.canCancel` is true (`payment_pending`, `pending`) |

---

Booking response notes:

- Booking payloads returned by user booking endpoints include `allowedActions.canCancel`, `allowedActions.canReschedule`, and `allowedActions.canJoin`.
- Slot payloads returned by `/bookings/available-slots/:adminId` use `startAt` and `endAt` only.
- User cancellation is allowed for `payment_pending` and `pending` bookings.

## 📝 Booking Reports (`/admin/booking-reports`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/admin/booking-reports` | List all booking reports |
| GET | `/admin/booking-reports/mine` | List doctor's own booking reports (Doctor only) |
| GET | `/admin/booking-reports/:id` | Get a booking report by ID |
| POST | `/admin/booking-reports` | Create a booking report |
| PUT | `/admin/booking-reports/:id` | Update a booking report |
| DELETE | `/admin/booking-reports/:id` | Delete a booking report |

Admin Dashboard:

GET /admin/doctor-work-time / GET /admin/doctor-work-time/mine
GET /admin/close-time / POST /admin/close-time
GET /admin/bookings / POST /admin/bookings
PUT /admin/bookings/:id/status (Doctor) / PUT /admin/bookings/:id/notes (Doctor)
POST /admin/bookings/follow-up (Doctor)
GET /admin/booking-reports / POST /admin/booking-reports
Mobile App:

GET /bookings/available-doctors?date=
GET /bookings/available-slots/:adminId?date=
GET /bookings/my / GET /bookings/history
POST /bookings / PUT /bookings/:id/reschedule / PUT /bookings/:id/cancel
GET /bookings/:id/report
