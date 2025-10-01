# Multi-stage build for optimal image size and security
FROM eclipse-temurin:17-jdk-jammy AS builder

# Install build dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    wget \
    unzip && \
    rm -rf /var/lib/apt/lists/*

# Set up working directory
WORKDIR /workspace/app

# Copy gradle wrapper and build files
COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .
COPY gradle.properties .

# Copy subproject build files
COPY catalog-service/build.gradle catalog-service/

# Make gradlew executable
RUN chmod +x ./gradlew

# Download dependencies (layer caching optimization)
RUN ./gradlew :catalog-service:dependencies --no-daemon

# Copy source code
COPY catalog-service/src catalog-service/src

# Build application
RUN ./gradlew :catalog-service:clean :catalog-service:build -x test --no-daemon

# Extract layers for better caching
RUN mkdir -p build/dependency && (cd build/dependency; jar -xf ../../catalog-service/build/libs/*.jar)

# Production stage
FROM eclipse-temurin:17-jre-jammy

# Install runtime dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    curl \
    dumb-init \
    fontconfig \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r astro && useradd -r -g astro astro

# Set up application directory
WORKDIR /app

# Copy application layers from builder stage
COPY --from=builder --chown=astro:astro /workspace/app/build/dependency/BOOT-INF/lib lib
COPY --from=builder --chown=astro:astro /workspace/app/build/dependency/META-INF META-INF
COPY --from=builder --chown=astro:astro /workspace/app/build/dependency/BOOT-INF/classes .

# Create logs directory
RUN mkdir -p /app/logs && chown astro:astro /app/logs

# Switch to non-root user
USER astro

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Environment variables
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseContainerSupport" \
    SPRING_PROFILES_ACTIVE=prod \
    TZ=UTC

# Expose port
EXPOSE 8080

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Run application
CMD ["sh", "-c", "java $JAVA_OPTS -cp /app org.stsci.astro.catalog.CatalogServiceApplication"]

# Metadata
LABEL maintainer="STScI Demo Project" \
      version="1.0.0" \
      description="Astronomical Catalog Management Microservice" \
      org.opencontainers.image.title="Astro Catalog Service" \
      org.opencontainers.image.description="Spring Boot microservice for managing astronomical object catalogs" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="STScI Demo" \
      org.opencontainers.image.licenses="MIT"