# ─────────────────────────────────────────────────────────────
# Stage 1: Build
# ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy only dependency manifests first to leverage layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build the uber-jar, skipping tests (tests run in CI)
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─────────────────────────────────────────────────────────────
# Stage 2: Runtime
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Security: run as non-root user (uid 1001)
RUN addgroup -S appgroup && adduser -S -G appgroup -u 1001 appuser

WORKDIR /app

# Copy the built fat jar from Stage 1
COPY --from=builder /build/target/*.jar app.jar

# Fix ownership
RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

# JVM tuning: container-aware memory + reasonable defaults
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.profiles.active=dev"

HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
