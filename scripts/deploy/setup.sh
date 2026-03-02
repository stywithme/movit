#!/bin/bash
# ============================================================
# POSE - Server Deployment Script
# back.mongz.online  →  Backend  (NestJS  - port 4000)
# dash.mongz.online  →  Dashboard (Next.js - port 4001)
#
# Server info:
#   - Node.js v24.6.0 (NVM)  ✓
#   - PM2 installed           ✓
#   - Nginx installed         ✓
#   - PostgreSQL :5432        ✓
#   - Redis :6379             ✓
#
# Existing apps on server (DO NOT touch):
#   - MCPBOT        → ports 3000, 3001
#   - chat-front    → port 3002
#   - ard-ai        → port 3003
# ============================================================

set -e

BACKEND_DIR="/var/www/pose-backend"
DASHBOARD_DIR="/var/www/pose-dashboard"
BACKEND_REPO="https://github.com/stywithme/POSE-backend.git"
DASHBOARD_REPO="https://github.com/stywithme/POSE-dashboard.git"
BACKEND_DOMAIN="back.mongz.online"
DASHBOARD_DOMAIN="dash.mongz.online"
BACKEND_PORT=4000
DASHBOARD_PORT=4001

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[✓] $1${NC}"; }
warn() { echo -e "${YELLOW}[!] $1${NC}"; }
err()  { echo -e "${RED}[✗] $1${NC}"; exit 1; }

# ── Load NVM (already installed) ────────────────────────────
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && source "$NVM_DIR/nvm.sh"
log "Node.js: $(node -v) | PM2: $(pm2 -v)"

# ── 1. Clone / Pull Repos ────────────────────────────────────
clone_or_pull() {
    local dir=$1
    local repo=$2
    if [ -d "$dir/.git" ]; then
        log "Pulling latest: $dir"
        git -C "$dir" pull
    else
        log "Cloning: $repo → $dir"
        git clone "$repo" "$dir"
    fi
}

mkdir -p /var/www
clone_or_pull "$BACKEND_DIR"   "$BACKEND_REPO"
clone_or_pull "$DASHBOARD_DIR" "$DASHBOARD_REPO"

# ── 2. Backend .env ──────────────────────────────────────────
if [ ! -f "$BACKEND_DIR/.env" ]; then
    warn "Creating backend .env — FILL IN YOUR VALUES!"
    cp "$BACKEND_DIR/.env.example" "$BACKEND_DIR/.env"
    # Set correct values for production
    sed -i "s|PORT=.*|PORT=$BACKEND_PORT|"                             "$BACKEND_DIR/.env"
    sed -i "s|ADMIN_APP_ORIGIN=.*|ADMIN_APP_ORIGIN=https://$DASHBOARD_DOMAIN|" "$BACKEND_DIR/.env"
    sed -i "s|REDIS_HOST=.*|REDIS_HOST=127.0.0.1|"                    "$BACKEND_DIR/.env"
    sed -i "s|REDIS_PORT=.*|REDIS_PORT=6379|"                         "$BACKEND_DIR/.env"

    echo ""
    warn ">>> nano $BACKEND_DIR/.env"
    warn "    Fill in: DATABASE_URL, JWT_SECRET, ADMIN_JWT_SECRET,"
    warn "             JWT_REFRESH_SECRET, GEMINI_API_KEY, GCS_CREDENTIALS_JSON"
    echo ""
    read -rp "Press ENTER after editing .env to continue..."
else
    log "Backend .env already exists — skipping"
fi

# ── 3. Dashboard .env ────────────────────────────────────────
if [ ! -f "$DASHBOARD_DIR/.env" ]; then
    warn "Creating dashboard .env — FILL IN YOUR VALUES!"
    cp "$DASHBOARD_DIR/.env.example" "$DASHBOARD_DIR/.env"
    sed -i "s|BACKEND_URL=.*|BACKEND_URL=http://localhost:$BACKEND_PORT|" "$DASHBOARD_DIR/.env"

    echo ""
    warn ">>> nano $DASHBOARD_DIR/.env"
    warn "    Fill in: ADMIN_JWT_SECRET (must match backend)"
    echo ""
    read -rp "Press ENTER after editing .env to continue..."
else
    log "Dashboard .env already exists — skipping"
fi

# ── 4. Install & Build Backend ───────────────────────────────
log "Installing backend dependencies..."
cd "$BACKEND_DIR"
npm ci --omit=dev
npx prisma generate
npm run build

log "Starting backend with PM2..."
pm2 delete pose-backend 2>/dev/null || true
pm2 start ecosystem.config.js --env production
pm2 save

# ── 5. Install & Build Dashboard ─────────────────────────────
log "Installing dashboard dependencies..."
cd "$DASHBOARD_DIR"
npm ci
npm run build

log "Starting dashboard with PM2..."
pm2 delete pose-dashboard 2>/dev/null || true
pm2 start ecosystem.config.js --env production
pm2 save

# ── 6. Nginx configs ─────────────────────────────────────────
log "Configuring Nginx..."

cat > /etc/nginx/sites-available/pose-backend <<NGINX
server {
    listen 80;
    server_name $BACKEND_DOMAIN;
    client_max_body_size 100M;
    location / {
        proxy_pass http://localhost:$BACKEND_PORT;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_cache_bypass \$http_upgrade;
        proxy_read_timeout 300s;
        proxy_connect_timeout 300s;
    }
}
NGINX

cat > /etc/nginx/sites-available/pose-dashboard <<NGINX
server {
    listen 80;
    server_name $DASHBOARD_DOMAIN;
    location / {
        proxy_pass http://localhost:$DASHBOARD_PORT;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_cache_bypass \$http_upgrade;
    }
}
NGINX

ln -sf /etc/nginx/sites-available/pose-backend   /etc/nginx/sites-enabled/pose-backend
ln -sf /etc/nginx/sites-available/pose-dashboard /etc/nginx/sites-enabled/pose-dashboard

nginx -t && systemctl reload nginx
log "Nginx configured and reloaded"

# ── 7. SSL with Certbot ──────────────────────────────────────
log "Getting SSL certificates..."
certbot --nginx \
    -d "$BACKEND_DOMAIN" \
    -d "$DASHBOARD_DOMAIN" \
    --non-interactive \
    --agree-tos \
    --email alustadh.manager@gmail.com \
    --redirect

# ── 8. PM2 startup on reboot ─────────────────────────────────
pm2 save
log "PM2 saved"

# ── Done ─────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  Deployment Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "  Backend   → https://$BACKEND_DOMAIN"
echo "  Dashboard → https://$DASHBOARD_DOMAIN"
echo ""
echo "  Ports used:  Backend=$BACKEND_PORT  Dashboard=$DASHBOARD_PORT"
echo "  PM2 status:  pm2 list"
echo "  PM2 logs:    pm2 logs pose-backend"
echo "               pm2 logs pose-dashboard"
echo ""
