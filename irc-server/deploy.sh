#!/usr/bin/env bash
# Заливает IRC-сервер на VPS и запускает его там.
# Запускать ЛОКАЛЬНО (Git Bash) из папки irc-server:
#     ./deploy.sh root@1.2.3.4 [PORT] [SSH_PORT]
set -euo pipefail

TARGET="${1:-}"
PORT="${2:-8080}"
SSH_PORT="${3:-22}"
REMOTE_DIR="/opt/lynxwave-irc"

if [ -z "$TARGET" ]; then
  echo "usage: ./deploy.sh user@host [PORT] [SSH_PORT]" >&2
  exit 1
fi

cd "$(dirname "${BASH_SOURCE[0]}")"

log() { printf '\033[36m[deploy]\033[0m %s\n' "$*"; }

log "Создаю $REMOTE_DIR на $TARGET"
ssh -p "$SSH_PORT" "$TARGET" "mkdir -p '$REMOTE_DIR'"

# users.json НЕ трогаем — аккаунты остаются на сервере
log "Копирую файлы"
scp -P "$SSH_PORT" server.js package.json package-lock.json setup.sh "$TARGET:$REMOTE_DIR/"

log "Запускаю setup.sh (порт $PORT)"
ssh -p "$SSH_PORT" "$TARGET" "cd '$REMOTE_DIR' && sed -i 's/\r\$//' setup.sh && bash setup.sh '$PORT'"

echo
log "Проверка снаружи: npx wscat -c ws://${TARGET#*@}:${PORT}"
