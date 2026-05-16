# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml ./
COPY src ./src
COPY .mvn ./.mvn
COPY mvnw ./

RUN ./mvnw clean package -DskipTests -Dspring.profiles.active=postgresql

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# su-exec: root → appuser へ安全に権限降格するユーティリティ
RUN apk add --no-cache su-exec

WORKDIR /app

COPY --from=builder /build/target/mapoker-backend-*.jar app.jar

# non-root ユーザーを作成（entrypoint が su-exec で切り替える）
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# root で起動し、entrypoint がディレクトリ権限を設定してから appuser に切り替える
ENTRYPOINT ["/docker-entrypoint.sh"]
CMD []
