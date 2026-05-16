#!/bin/sh
# アップロードディレクトリを root で作成・権限設定してから appuser で起動する
mkdir -p /data/uploads/avatars
chown -R appuser:appgroup /data/uploads 2>/dev/null || true
chmod -R 755 /data/uploads 2>/dev/null || true
exec su-exec appuser java -jar /app/app.jar
