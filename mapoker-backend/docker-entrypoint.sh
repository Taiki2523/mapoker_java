#!/bin/sh
# ディレクトリ作成・権限設定を root で行い、その後 appuser で起動する

# アバター
mkdir -p /data/uploads/avatars
chown -R appuser:appgroup /data/uploads 2>/dev/null || true
chmod -R 755 /data/uploads 2>/dev/null || true

# ログ
mkdir -p "${LOGS_LOCATION:-/data/logs}/backend"
chown -R appuser:appgroup "${LOGS_LOCATION:-/data/logs}" 2>/dev/null || true
chmod -R 755 "${LOGS_LOCATION:-/data/logs}" 2>/dev/null || true

exec su-exec appuser java \
  -Dlogging.file.dir="${LOGS_LOCATION:-/data/logs}" \
  -jar /app/app.jar
