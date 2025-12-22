Training Validator – Architecture Decisions (C4 Model)

## Purpose
This document captures the current, agreed architecture and technical decisions for the **Training Validator** mobile application.
It is intentionally implementation‑focused and reflects today’s workflow only (no future migrations or speculative evolution).

## Project Overview
Training Validator is a mobile-first fitness feature designed to help users validate their exercise form in real time using their phone camera.
The system acts as a virtual coach that:
- Guides the user to record exercises from the correct angles
- Monitors body movement during execution
- Provides immediate corrective feedback
- Generates a post-workout analytical report highlighting weaknesses and improvement areas

The product targets users training alone, without a personal coach, and focuses on form correctness, injury prevention, and progression.

## User Workflow
**After Auth (Login - Register)**

**Step 1 – Exercise Selection**
The user manually selects the exercise (e.g., Squat, Push-up) before starting.
*Why:* Eliminates ambiguity, enables exercise-specific evaluation logic, and improves accuracy.

**Step 2 – Camera Guidance & Setup**
Before recording starts, the app:
- Shows required camera angle(s)
- Displays a reference skeleton representing the correct starting posture
- Explains positioning briefly (distance, body visibility)
*Impact:* Reduces input errors and increases pose detection reliability.

**Step 3 – Real-time Exercise Recording (Native Layer)**
During the workout:
- The app tracks the user’s skeleton and joint angles
- Repetitions are counted based on motion phases
- Joints are visualized using color coding:
    - Green: correct range
    - Red: incorrect range
*Feedback:* Immediate text/audio cues triggered for critical errors.

**Step 4 – Data Capture (No Video Storage)**
While recording:
- Only structured motion data is stored (skeleton coordinates, joint angles, timing)
- Raw video is **not** saved
*Benefits:* Privacy, lower storage costs, faster processing.

**Step 5 – Post-Workout Report**
After the session:
- The system analyzes the full exercise timeline
- A structured summary is generated (Total reps, Incorrect reps, Issues)
- LLM generates a motivational and actionable report.

---

## 1. System Overview (C4 – Level 1)

### System Goal
A mobile‑only fitness application that validates exercise performance in real time using on‑device pose estimation, provides instant feedback, and generates a structured post‑training report.

### Key Characteristics
- **Hybrid Mobile Architecture:** Flutter (UI/Orchestration) + Native Modules (Core Logic).
- **Offline‑first:** (cache → compute → sync).
- **On‑device computation:** All training logic runs natively (Kotlin/Swift).
- **Backend:** Orchestration, configuration, and reporting only.

### Primary Users
- **End User (Trainee):** Performs exercises and receives feedback.
- **Admin / Trainer:** Defines exercises, rules, and feedback logic.

---

## 2. System Context (C4 – Level 2)

### External Actors
- Mobile User
- Admin (Web Dashboard)
- Mobile App Stores (Apple App Store / Google Play)
- LLM Provider (for report generation)

### External Systems
- Payment providers (via App Stores)
- Cloud storage (optional snapshots)

### Context Diagram (Textual)
```
[User]
  ↓
[Mobile App (Hybrid)]
  ↓ (sync results / fetch configs)
[Backend API]
  ↓
[PostgreSQL Database]
  ↓
[LLM Provider]
```

---

## 3. Container View (C4 – Level 3)

### 3.1 Mobile Application (Hybrid: Flutter + Native)
**Architecture:** Flutter Shell with Native Modules via Platform Channels.

**A. Flutter Layer (Dart)**
- **Responsibilities:**
    - App Navigation & Auth
    - Exercise Selection & Lists
    - Post-workout Reports & Dashboard
    - Local Database (Results & History)
    - Sync Management with Backend
    - Admin-defined rules caching

**B. Native Layer (Kotlin - Android / Swift - iOS)**
- **Responsibilities:**
    - High-performance Camera access
    - MediaPipe BlazePose execution (GPU Accelerated)
    - **Training Engine (The "Brain"):**
        - Angle Calculation
        - Rep Counting Logic
        - Phase Recognition State Machine
        - Error Detection
    - Real-time Skeleton Overlay Rendering

### 3.2 Backend API (NestJS)
- **Responsibilities:** Authentication, Subscription, Exercise definitions distribution, Results ingestion, LLM Orchestration.
- **Non‑Responsibilities:** Real‑time processing, Exercise validation.

### 3.3 Database (PostgreSQL)
- **Stores:** Users, Exercises, Training Summaries, Reports.
- **Does NOT Store:** Raw video.

---

## 4. Component View – Mobile App (C4 – Level 4)

### 4.1 High‑Level Component Breakdown

```
Mobile App Structure
│
├── Flutter Layer (Dart)
│   ├── UI Components
│   │   ├── Auth Screens
│   │   ├── Exercise List
│   │   └── Report Viewer
│   │
│   ├── Data Layer
│   │   ├── Local Storage (Hive/SQLite)
│   │   └── API Client (Sync)
│   │
│   └── Interface Layer
│       └── MethodChannels (Bridge to Native)
│
└── Native Layer (Kotlin / Swift)
    ├── Camera Manager (CameraX / AVFoundation)
    │
    ├── AI Processor
    │   └── MediaPipe BlazePose SDK
    │
    └── Training Logic Engine (Core Business Logic)
        ├── Skeleton Normalizer
        ├── Angle Calculator (Geometry)
        ├── State Machine (Idle -> Down -> Up)
        ├── Rep Counter
        └── Error Detector (Compares stream vs Rules)
```

### 4.2 Data Flow during Workout
1.  **Flutter:** User selects exercise → Sends "Exercise Config & Rules" to Native via Channel.
2.  **Native:** Opens Camera view.
3.  **Native:** MediaPipe processes frames → Generates Landmarks.
4.  **Native (Training Engine):**
    *   Calculates Angles.
    *   Checks against Rules.
    *   Updates State (Rep Count).
    *   Triggers Feedback (Audio/Haptic).
5.  **Native:** Renders Overlay on top of camera feed.
6.  **Native:** On finish → Sends "Session Summary" back to Flutter.
7.  **Flutter:** Saves to DB & Syncs.

---

## 5. Core Training Workflow (Primary Focus)

### 5.1 Exercise Execution Flow
1.  User selects an exercise manually (Flutter).
2.  App passes exercise JSON (angles, phases, errors) to Native Module.
3.  **Native Layer takes over:**
    -   Initializes Camera & MediaPipe.
    -   Runs **Environment Check** (Light/Distance).
    -   Starts Training Loop.
4.  **Real-time Logic (Native):**
    -   Compute Joint Angles.
    -   Determine Motion Phase.
    -   Count Reps.
    -   Detect Errors based on Admin Rules.
5.  **UI Feedback (Native Overlay):**
    -   Skeleton overlay.
    -   Green/Red indicators.
    -   Rep Counter.
6.  **Completion:**
    -   Native returns control to Flutter with result object.

### 5.2 Real‑Time Feedback Rules
-   **Priority System:**
    -   High: Safety/Wrong Form (Audio + Visual).
    -   Low: Optimization (Report only).
-   **Phase-Locked:** Errors are checked only during relevant phases (e.g., knee valgus during "Push" phase).

---

## 6. Reporting Workflow

### 6.1 Data Flow
`Training Metrics (JSON)` → `Backend API` → `LLM Prompt` → `Generated Report` → `User Device`

### 6.2 Report Characteristics
-   Data‑driven (no free hallucination).
-   Motivational tone.
-   Highlights weaknesses and improvements.

---

## 7. Admin Workflow (Exercise Definition)

### Admin Capabilities
-   Create new exercises & inputs (Name, Instructions).
-   Define required **Camera Angles**.
-   Define **Motion Phases** (Start, Eccentric, Concentric, End).
-   Define **Valid Angle Ranges** per phase/joint.
-   Define **Error Messages** & Audio cues.
-   **Structure:** JSON-based configuration initially, moving to Visual Editor later.

---

## 8. Key Architectural Decisions (Summary)

| Area | Decision | Reason |
| :--- | :--- | :--- |
| **Mobile Architecture** | **Hybrid (Flutter + Native)** | Single codebase for UI, Max performance for AI. |
| **Pose Estimation** | **MediaPipe (Native SDK)** | Best-in-class performance & accuracy. |
| **Training Logic** | **Native (Kotlin/Swift)** | Zero-latency feedback, direct access to AI stream. |
| **Data Strategy** | Offline‑first | Reliability in gyms/basements. |
| **Backend Role** | Orchestration only | Scalability & Cost. |
| **Video Storage** | **None** | Privacy & Cost. |

---

## 9. Non‑Goals (Explicitly Out of Scope)
-   No real‑time backend processing.
-   No server‑side pose estimation.
-   No video storage by default.
-   No AR/VR headset support (Mobile only).
