# Build stage
FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Build arguments
ARG VITE_API_BASE=http://localhost:8080
ARG VITE_GOOGLE_CLIENT_ID=""
ARG VITE_APP_ENV=production

ENV VITE_API_BASE=${VITE_API_BASE}
ENV VITE_GOOGLE_CLIENT_ID=${VITE_GOOGLE_CLIENT_ID}
ENV VITE_APP_ENV=${VITE_APP_ENV}

RUN npm run build

# Runtime stage - nginx
FROM nginx:alpine

# Copy built files
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:80/index.html || exit 1

# Port
EXPOSE 80

# Run nginx
CMD ["nginx", "-g", "daemon off;"]
