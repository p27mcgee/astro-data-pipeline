# Multi-stage build for optimal image size and security
FROM openjdk:17-jdk-slim as builder

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
COPY build.gradle .
COPY settings.gradle .

# Make gradlew executable
RUN chmod +x ./gradlew

# Download dependencies (layer caching optimization)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src

# Build application
RUN ./gradlew clean build -x test --no-daemon

# Extract layers for better caching
RUN mkdir -p build/dependency && (cd build/dependency; jar -xf ../libs/*.jar)

# Production stage
FROM openjdk:17-jre-slim

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
CMD ["sh", "-c", "java $JAVA_OPTS -cp /app org.stsci.astro.processor.ImageProcessorApplication"]

# Metadata
LABEL maintainer="STScI Demo Project" \
      version="1.0.0" \
      description="Astronomical Image Processing Microservice" \
      org.opencontainers.image.title="Astro Image Processor" \
      org.opencontainers.image.description="Spring Boot microservice for processing astronomical FITS images" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="STScI Demo" \
      org.opencontainers.image.licenses="MIT"