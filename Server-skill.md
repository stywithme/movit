---
name: smart-delivery-server
description: Connects to and operates  production Ubuntu server at 46.101.185.241. Use when the user asks about SSH access, PM2 apps, Nginx reverse proxy, deployment checks, logs, restarts, ports, or server troubleshooting for Smart Delivery, MCPBOT, ARD_AI, SD-back, SD-front, pose-backend, pose-dashboard, mongz.online, or system.sdam.sa.
---

# Smart Delivery Server

## Use This Skill

Use this skill whenever the user asks to access, inspect, troubleshoot, restart, deploy, or explain the production server at `46.101.185.241`, including PM2, Nginx, app logs, ports, `mongz.online`, and `system.sdam.sa` domains.

## Access

SSH directly as root:

```bash
ssh root@46.101.185.241
```

The server uses key/token authentication and normally does not ask for a password.

For non-interactive SSH commands, PM2 needs the Node/NVM path added first:

```bash
export PATH=/root/.nvm/versions/node/v24.6.0/bin:$PATH
```

Example:

```bash
ssh root@46.101.185.241 'export PATH=/root/.nvm/versions/node/v24.6.0/bin:$PATH; pm2 status'
```

Do not print or copy `.env` files, `pm2 env`, private keys, certificate keys, tokens, or database credentials unless the user explicitly asks and it is necessary.

## Server Shape

- Hostname: `MCPBOT`
- OS: Ubuntu server
- Public IP: `46.101.185.241`
- Process manager: `pm2-root.service`, enabled and running as `root`
- PM2 binary: `/root/.nvm/versions/node/v24.6.0/bin/pm2`
- Node version used by PM2 apps: `24.6.0`
- PM2 home/logs: `/root/.pm2`
- Reverse proxy: Nginx, enabled and running
- Local services seen on the server: PostgreSQL on `127.0.0.1:5432`, Redis on `127.0.0.1:6379`
- Public listeners: SSH `22`, Nginx `80/443`

## App Map

- `ard-ai`
  - Directory: `/var/www/ARD_AI`
  - Script: `dist/apps/agent-server/main.js`
  - Args: `--port=3003`
  - PM2 mode: fork
  - Nginx: `ardai.mongz.online` and HTTPS by IP proxy to `localhost:3003`

- `chatbot-bot`
  - Directory: `/var/www/MCPBOT`
  - Script: `dist/apps/chat-bot/main.js`
  - PM2 mode: fork
  - Nginx: `mongz.online` and `www.mongz.online` proxy to `localhost:3000`

- `chatbot-agent`
  - Directory: `/var/www/MCPBOT`
  - Script: `dist/apps/agent-server/main.js`
  - PM2 mode: fork
  - Listening port observed: `3001`

- `chat-front`
  - Directory: `/var/www/minimalised-chatbot/dist`
  - Script: PM2 static serve (`Serve.js`)
  - PM2 mode: fork
  - Nginx: `app.mongz.online` proxy to `localhost:3002`

- `pose-backend`
  - Directory: `/var/www/pose-backend`
  - Script: `dist/main.js`
  - PM2 mode: cluster
  - Production port: `4000`
  - Nginx: `back.mongz.online` proxy to `localhost:4000`
  - Nginx allows uploads up to `100M`

- `pose-dashboard`
  - Directory: `/var/www/pose-dashboard`
  - Script: `node_modules/.bin/next`
  - Args: `start`
  - PM2 mode: cluster
  - Production port: `4001`
  - Nginx: `dash.mongz.online` proxy to `localhost:4001`
  - Current Nginx config references the certificate path under `/etc/letsencrypt/live/back.mongz.online`; verify certificates before changing SSL config.

- `sd-back`
  - Directory: `/var/www/SD-back`
  - Script: `dist/src/main.js`
  - PM2 mode: fork
  - Production port: `4102`
  - Environment overrides in PM2: `NODE_ENV=production`, `HOST=127.0.0.1`, `PORT=4102`, `BASE_URL=https://system.sdam.sa`
  - Nginx: `system.sdam.sa` proxies `/api/`, `/socket.io/`, and `/uploads/` to `127.0.0.1:4102`

- `SD-front`
  - Directory: `/var/www/SD-front`
  - Build output: `/var/www/SD-front/dist`
  - Served directly by Nginx at `https://system.sdam.sa`
  - Build-time frontend env used for deployment: `VITE_API_BASE_URL=https://system.sdam.sa/api`, `VITE_API_URL=https://system.sdam.sa`
  - SSL certificate: `/etc/letsencrypt/live/system.sdam.sa/`

## Read-Only Checks

Start every server investigation with safe status checks:

```bash
ssh root@46.101.185.241
export PATH=/root/.nvm/versions/node/v24.6.0/bin:$PATH
pm2 status
systemctl status nginx --no-pager -l
nginx -t
ss -ltnp
```

Useful PM2 inspection:

```bash
pm2 describe <app-name>
pm2 logs <app-name> --lines 100
pm2 monit
```

Useful Nginx inspection:

```bash
ls -la /etc/nginx/sites-enabled /etc/nginx/sites-available
nginx -T
```

## Safe Operations

Restart one app:

```bash
export PATH=/root/.nvm/versions/node/v24.6.0/bin:$PATH
pm2 restart <app-name>
pm2 logs <app-name> --lines 100
pm2 save
```

Reload cluster apps when appropriate:

```bash
pm2 reload pose-backend
pm2 reload pose-dashboard
pm2 save
```

After any Nginx change, always test before reload:

```bash
nginx -t
systemctl reload nginx
```

Avoid rebooting the server, changing firewall rules, editing `.env`, running database migrations, deleting PM2 apps, or changing SSL certificates unless the user explicitly asks.

## Deployment Pattern

Only deploy when the user asks. First inspect the target app directory:

```bash
cd /var/www/<app-directory>
git status
```

Then follow the app's existing scripts. Common patterns on this server:

```bash
npm install
npm run build
pm2 restart <app-name>
pm2 save
```

For ecosystem-based apps:

```bash
pm2 start ecosystem.config.js --env production
pm2 save
```

Validate after deploy:

```bash
pm2 status
pm2 logs <app-name> --lines 100
nginx -t
```
