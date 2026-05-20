#!/bin/sh
# 起動時に環境変数から config.js を生成し、ログディレクトリを作成してから nginx を起動する

cat > /usr/share/nginx/html/config.js << EOF
window.__CONFIG__ = {
  googleClientId: '${GOOGLE_CLIENT_ID}',
}
EOF

# ログディレクトリを作成
mkdir -p "${LOGS_LOCATION:-/data/logs}/frontend"

# nginx の access_log / error_log のパスを環境変数に合わせて書き換え
NGINX_CONF=/etc/nginx/conf.d/default.conf
if [ -n "${LOGS_LOCATION}" ]; then
  sed -i "s|/data/logs/frontend|${LOGS_LOCATION}/frontend|g" "${NGINX_CONF}"
fi

# crond をバックグラウンドで起動（logrotate の定期実行）
# 毎日 00:05 に実行
echo "5 0 * * * /usr/sbin/logrotate /etc/logrotate.d/nginx-app" | crontab -
crond -b

exec nginx -g 'daemon off;'
