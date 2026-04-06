---
name: git-push-workflow
description: >-
  Manages the Git push workflow for the POSE monorepo. Merges Base branch into
  MO and HA, resolves conflicts, pushes all main repo branches, then pushes
  Admin-Dashboard and backend nested repos from HA branch to their main branch,
  then checks out Base again for continued work. Use when pushing to GitHub,
  merging branches, syncing repos, or when the user says push, upload, deploy,
  or رفع.
---

# Git Push Workflow

## Project Structure

- **Main repo (POSE)**: branches `Base`, `MO`, `HA` — remote: `origin`
- **Admin-Dashboard/**: nested independent repo (POSE-dashboard) — push to `main`
- **backend/**: nested independent repo (POSE-backend) — push to `main`
- **android-poc/**: part of main POSE repo (no separate git)

## Full Workflow

Execute steps in order. Use the todo list to track progress.

### Step 1 — Commit & Push Base

Ensure all work on `Base` is committed and pushed:

```bash
git checkout Base
git add .
git commit -m "<descriptive message>"
git push origin Base
```

### Step 2 — Merge Base → MO

```bash
git checkout MO
git merge Base
```

If conflicts occur, follow **Conflict Resolution** below. Then:

```bash
git push origin MO
```

### Step 3 — Merge Base → HA

```bash
git checkout HA
git merge Base
```

If conflicts occur, follow **Conflict Resolution** below. Then:

```bash
git push origin HA
```

### Step 4 — Push Sub-Repos (MUST be on HA)

Verify current branch is `HA` before proceeding. This is mandatory.

#### Admin-Dashboard (Frontend)

```bash
cd Admin-Dashboard
git add .
git status
git commit -m "<descriptive message about synced changes>"
git push origin main
cd ..
```

#### backend

```bash
cd backend
git add .
git status
git commit -m "<descriptive message about synced changes>"
git push origin main
cd ..
```

### Step 5 — Return to Base

After sub-repo pushes complete, switch the main repo back to `Base` so daily work continues on the shared branch:

```bash
git checkout Base
```

## Conflict Resolution Strategy

When merging `Base` into `MO` or `HA`:

1. **Base = NEW**: incoming Base changes are always the latest, authoritative updates
2. **Branch code = OLD**: existing MO/HA code that conflicts is considered older
3. **Resolution rules**:
   - Accept Base (new) changes as the primary source of truth
   - Logically reconcile old branch-specific code with new changes
   - **NEVER** remove or break functionality unique/independent to the target branch
   - If old code has branch-specific logic that doesn't logically conflict with Base, preserve it
   - When in doubt, keep both — integrate Base changes while preserving branch-specific behavior

## Important Rules

- Sub-repos are pushed **only from HA branch**, never from MO
- After the workflow finishes, the main repo must be on **`Base`** (Step 5)
- Always verify the current branch before every operation
- `android-poc` has no separate repo — it's tracked by the main POSE repo only
- Write descriptive commit messages reflecting the actual changes, not generic ones
- Run `git status` before each commit to review what will be committed
