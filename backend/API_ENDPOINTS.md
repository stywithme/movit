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

## 🏃 Workouts (`/workouts`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/workouts` | List workouts |
| POST | `/workouts` | Create a new workout |
| GET | `/workouts/:id` | Get workout details (includes exercises) |
| PUT | `/workouts/:id` | Update workout |
| DELETE | `/workouts/:id` | Delete a workout |
| POST | `/workouts/:id/publish` | Publish a workout |
| DELETE | `/workouts/:id/publish` | Unpublish a workout |
| POST | `/workouts/:id/duplicate` | Duplicate an existing workout |

---

## 📅 Programs (`/programs`)

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| GET | `/programs` | List programs |
| POST | `/programs` | Create a new program |
| GET | `/programs/:id` | Get program full details (weeks, days, sessions) |
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
| **Sessions** | | |
| POST | `/programs/:programId/weeks/:weekId/days/:dayId/sessions` | Add session to a day |
| PUT | `/programs/:programId/sessions/:sessionId` | Update session |
| DELETE | `/programs/:programId/sessions/:sessionId` | Delete a session |
| POST | `/programs/:programId/sessions/:sessionId/import-workout/:workoutId` | Import all exercises from a workout into a session |

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
