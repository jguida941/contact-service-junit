# =============================================================================
# Multi-Stage Dockerfile for CS320 Contact Service
# =============================================================================
# This Dockerfile uses a multi-stage build approach to:
# 1. Build the application with Maven in a full JDK image
# 2. Extract JAR layers for optimal Docker layer caching
# 3. Run the application in a minimal JRE image as a non-root user
#
# Benefits:
# - Smaller final image size (~200MB vs ~700MB with full JDK)
# - Faster rebuilds by leveraging Docker layer caching
# - Enhanced security by running as non-root user
# - Separation of build-time and runtime dependencies
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build Stage
# -----------------------------------------------------------------------------
# Use Eclipse Temurin (formerly AdoptOpenJDK) for predictable, long-term
# support. Version 17 matches our target Java version.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy AS builder

# Set working directory for the build
WORKDIR /build

# Copy Maven wrapper and POM first for better layer caching.
# These files change infrequently, so Docker can reuse this layer
# when only source code changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies in a separate layer for caching.
# This step is cached unless pom.xml changes, significantly
# speeding up rebuilds during active development.
RUN ./mvnw dependency:go-offline -B

# Copy application source code
COPY src/ ./src/
COPY config/ ./config/
# Copy frontend source for the Vite/React build (frontend-maven-plugin expects ui/contact-app)
COPY ui/ ./ui/

# Build the application JAR
# - clean: Remove previous build artifacts
# - package: Compile, test, and package into JAR
# - -DskipTests: Skip tests in Docker build (run in CI instead)
# - -Dpit.skip=true: Skip mutation testing (expensive, run in CI)
# - -Dspotbugs.skip=true: Skip static analysis (run in CI)
# - -Ddependency.check.skip=true: Skip OWASP scan (run in CI)
RUN ./mvnw clean package -DskipTests \
    -Dpit.skip=true \
    -Dspotbugs.skip=true \
    -Ddependency.check.skip=true \
    -Dcheckstyle.skip=true

# Extract JAR layers for optimal Docker layer caching.
# Spring Boot's layered JAR format separates:
# - dependencies: External libraries (changes rarely)
# - spring-boot-loader: Boot loader classes (changes rarely)
# - snapshot-dependencies: SNAPSHOT deps (changes occasionally)
# - application: Our code (changes frequently)
#
# This allows Docker to reuse dependency layers across builds,
# dramatically reducing image pull and push times in CI/CD.
RUN mkdir -p target/extracted && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# -----------------------------------------------------------------------------
# Stage 2: Runtime Stage
# -----------------------------------------------------------------------------
# Use Eclipse Temurin JRE for a minimal runtime environment.
# JRE is ~60% smaller than JDK and contains only what's needed to run Java.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy

# Create a non-root user to run the application.
# Security best practice: Never run containers as root.
# - UID 1001: Arbitrary non-privileged user ID
# - -r: System user (no login shell needed)
# - -s /bin/false: Disable shell access for this user
RUN useradd -r -u 1001 -s /bin/false appuser

# Set working directory
WORKDIR /app

# Copy extracted JAR layers from builder stage.
# Order matters: copy least-frequently-changed layers first.
COPY --from=builder --chown=appuser:appuser /build/target/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appuser /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appuser /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appuser /build/target/extracted/application/ ./

# Switch to non-root user
USER appuser

# Expose port 8080 for the Spring Boot application.
# This is a documentation directive; actual port binding happens at runtime.
EXPOSE 8080

# Configure JVM options via environment variable.
# Defaults optimize for containerized environments:
# - XX:+UseContainerSupport: Respect container memory limits
# - XX:MaxRAMPercentage=75.0: Use up to 75% of container memory for heap
# - XX:+ExitOnOutOfMemoryError: Kill process on OOM (let orchestrator restart)
# - Dspring.profiles.active: Default to 'prod' profile if not overridden
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Dspring.profiles.active=prod"

# Health check using actuator endpoint.
# Container orchestrators (Docker Compose, Kubernetes) use this to
# determine if the container is healthy and ready to receive traffic.
# - interval: Check every 30 seconds
# - timeout: Wait up to 3 seconds for response
# - start-period: Wait 60 seconds before first check (app startup time)
# - retries: Mark unhealthy after 3 consecutive failures
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application using Spring Boot's JarLauncher.
# This is more efficient than 'java -jar' because layers are already extracted.
# $JAVA_OPTS allows runtime JVM tuning via docker-compose or K8s env vars.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
