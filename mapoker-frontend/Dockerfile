# Build stage
FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

RUN npm run build

# Runtime stage - nginx
FROM nginx:alpine

# logrotate と dcron（Alpine の cron 実装）をインストール
RUN apk add --no-cache logrotate dcron

# Copy built files
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# logrotate 設定
COPY logrotate-nginx.conf /etc/logrotate.d/nginx-app

# Copy entrypoint (generates config.js from env vars at startup)
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:80/index.html || exit 1

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
