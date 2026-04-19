# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy pom and source
COPY pom.xml ./
COPY src ./src
COPY .mvn ./.mvn
COPY mvnw ./

# Build with Maven
RUN ./mvnw clean package -DskipTests -Dspring.profiles.active=postgresql

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/target/mapoker-backend-*.jar app.jar

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Port
EXPOSE 8080

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD []
