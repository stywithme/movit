# Movit (KMP line)

Standalone repository for the **KMP / new-stack** product line, split from [POSE](https://github.com/stywithme/POSE).

This tree is a full copy of POSE branch **`KMP`** — not only the mobile app:

| Path | Role |
|------|------|
| `android-poc/` | Kotlin Multiplatform mobile (Android + iOS) |
| `backend/` | Node API (mobile sync, auth, training data) |
| `Admin-Dashboard/` | Admin web dashboard |
| `Docs/` | Architecture and migration docs |
| `scripts/`, `tools/` | Repo automation |

The legacy Android path remains on POSE branch **`HA`**.

## Quick start

- **Mobile:** `android-poc/` — see `api.properties.example`, `./gradlew :app:assembleDebug`
- **Backend:** `backend/` — see `backend/README.md`
- **Dashboard:** `Admin-Dashboard/`

## Git

- Upstream before split: `stywithme/POSE` branch `KMP`
- This repo `main` tracks that branch snapshot and history.
