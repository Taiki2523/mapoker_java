#!/bin/sh
# 起動時に環境変数から config.js を生成する
cat > /usr/share/nginx/html/config.js << EOF
window.__CONFIG__ = {
  googleClientId: '${GOOGLE_CLIENT_ID}',
}
EOF
exec nginx -g 'daemon off;'
