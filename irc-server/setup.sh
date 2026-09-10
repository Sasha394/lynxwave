#!/usr/bin/env bash
# Установка и запуск LynxWave IRC-сервера на VPS (Debian/Ubuntu).
# Запускать НА СЕРВЕРЕ:  bash setup.sh [PORT]
# По умолчанию порт 8080. Идемпотентен — можно гонять повторно для обновления.
set -euo pipefail

PORT="${1:-${PORT:-8080}}"
APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="lynxwave-irc"

if [ "$(id -u)" -eq 0 ]; then SUDO=""; else SUDO="sudo"; fi

log() { printf '\033[36m[setup]\033[0m %s\n' "$*"; }

# --- Node.js 20+ ---------------------------------------------------------
need_node=1
if command -v node >/dev/null 2>&1; then
  major="$(node -p 'process.versions.node.split(".")[0]')"
  if [ "$major" -ge 18 ]; then need_node=0; log "Node $(node -v) уже стоит"; fi
fi
if [ "$need_node" -eq 1 ]; then
  log "Ставлю Node.js 20"
  $SUDO apt-get update -qq
  $SUDO apt-get install -y -qq curl ca-certificates
  curl -fsSL https://deb.nodesource.com/setup_20.x | $SUDO -E bash -
  $SUDO apt-get install -y -qq nodejs
fi

# --- Зависимости ---------------------------------------------------------
cd "$APP_DIR"
log "npm install"
if [ -f package-lock.json ]; then npm ci --omit=dev; else npm install --omit=dev; fi

# --- pm2 -----------------------------------------------------------------
if ! command -v pm2 >/dev/null 2>&1; then
  log "Ставлю pm2"
  $SUDO npm install -g pm2 >/dev/null
fi

# --- Запуск --------------------------------------------------------------
log "Стартую $APP_NAME на порту $PORT"
pm2 delete "$APP_NAME" >/dev/null 2>&1 || true
PORT="$PORT" HOST=0.0.0.0 pm2 start server.js --name "$APP_NAME" --time
pm2 save >/dev/null

# автозапуск после ребута
if [ "$(id -u)" -eq 0 ]; then
  pm2 startup systemd -u root --hp /root >/dev/null 2>&1 || true
else
  log "Для автозапуска после ребута выполни от root: pm2 startup"
fi

# --- Фаервол -------------------------------------------------------------
if command -v ufw >/dev/null 2>&1 && $SUDO ufw status 2>/dev/null | grep -q "Status: active"; then
  log "Открываю порт $PORT в ufw"
  $SUDO ufw allow "${PORT}/tcp" >/dev/null
fi

IP="$(curl -fsS --max-time 5 ifconfig.me 2>/dev/null || echo '<IP_СЕРВЕРА>')"
echo
log "Готово."
echo "  Логи:      pm2 logs $APP_NAME"
echo "  Рестарт:   pm2 restart $APP_NAME"
echo "  Статус:    pm2 status"
echo
echo "  В IRC.java:63 пропиши:"
echo "      String SERVER_URI = \"ws://${IP}:${PORT}\";"
