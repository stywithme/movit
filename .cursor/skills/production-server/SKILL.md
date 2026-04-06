---
name: production-server
description: >-
  Monitor and debug the POSE production server via SSH. Read PM2 logs for
  pose-backend and pose-dashboard, check process status, and track errors.
  Use when the user says logs, server, production, سيرفر, لوقات, or إيرور.
---

# Production Server Monitoring

## Server Info

- **Host**: read `SSH_HOST` from `.env.server`
- SSH key auth is configured — no password needed

### Projects on Server

| Project | PM2 Name | Path |
|---------|----------|------|
| Backend API (NestJS) | `pose-backend` | `/var/www/pose-backend` |
| Admin Dashboard (Next.js) | `pose-dashboard` | `/var/www/pose-dashboard` |

Other PM2 processes also run on this server — do NOT touch them.

## How to Run Commands

SSH with key auth + source nvm so `pm2` is on PATH:

```bash
ssh -o StrictHostKeyChecking=no root@<HOST> "source /root/.nvm/nvm.sh; <pm2 command>" 2>$null
```

Read `SSH_HOST` from `.env.server` before running.

## Common Commands

```bash
# Status
ssh -o StrictHostKeyChecking=no root@<HOST> "source /root/.nvm/nvm.sh; pm2 status" 2>$null

# Backend logs
ssh -o StrictHostKeyChecking=no root@<HOST> "source /root/.nvm/nvm.sh; pm2 logs pose-backend --lines 50 --nostream" 2>$null

# Dashboard logs
ssh -o StrictHostKeyChecking=no root@<HOST> "source /root/.nvm/nvm.sh; pm2 logs pose-dashboard --lines 50 --nostream" 2>$null

# Backend errors only
ssh -o StrictHostKeyChecking=no root@<HOST> "source /root/.nvm/nvm.sh; pm2 logs pose-backend --err --lines 100 --nostream" 2>$null

# Dashboard errors only
ssh -o StrictHostKeyChecking=no root@<HOST> "source /root/.nvm/nvm.sh; pm2 logs pose-dashboard --err --lines 100 --nostream" 2>$null
```

**Always use `--nostream`** to prevent the command from hanging.

## Important Rules

- **NEVER restart, stop, or delete** any PM2 process unless explicitly asked
- **NEVER modify** any files on the server unless explicitly asked
- Only interact with `pose-backend` and `pose-dashboard` — ignore other processes
- When reporting errors, include timestamp, error message, and stack trace
- If log output is very long, summarize key errors and show the most recent ones
