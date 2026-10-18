# Backend (Spring Boot) image for Amazon ECS
#
# Multi-stage build: stage 1 compiles the application with the full JDK, stage 2 copies only the
# resulting JAR into a smaller image with just the Java runtime (JRE). Build tools and source code
# never reach the image that runs in production.
#
# Configuration and secrets (DB_URL, DB_USERNAME, DB_PASSWORD, APP_MAIL_FROM, APP_OTP_HMAC_KEY,
# SPRING_MAIL_*, ...) are passed as environment variables by the ECS task definition;
# nothing secret is copied into the image (see .dockerignore).
#
# Build locally:  docker build -t cross-border-payment-backend ./backend

# =====================================
# 1) Build stage (JDK 17 + Gradle Wrapper)
# =====================================
# Full JDK image (Ubuntu 22.04 "jammy"); "AS build" names the stage so stage 2 can copy from it
FROM eclipse-temurin:17-jdk-jammy AS build

# All following commands run in this directory inside the build container
WORKDIR /workspace

# Copy only the Gradle Wrapper and build scripts first.
# Docker caches each layer: as long as these files do not change, the dependency download
# below is reused and only the source code layers are rebuilt.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle

# Download all dependencies into the layer cache (output hidden; only the download matters).
# The Gradle Wrapper downloads the exact Gradle version the project uses (gradle-wrapper.properties).
RUN ./gradlew --no-daemon dependencies > /dev/null

# Copy the application source code (changes often, so it comes after the dependency layer)
COPY src src

# Build the executable Spring Boot JAR. Tests are not run here: CircleCI runs them before building the image.
# Gradle also writes a "-plain.jar" (classes only, not executable), so the executable one is copied to app.jar.
RUN ./gradlew --no-daemon bootJar \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar$')" app.jar

# =====================================
# 2) Runtime stage (JRE 17 only)
# =====================================
# Smaller image with only the Java runtime: no compiler, no Gradle, no source code
FROM eclipse-temurin:17-jre-jammy

# Directory of the application inside the running container
WORKDIR /app

# Create an unprivileged system user "app" without a home directory or login shell,
# so that the application does not run as root
RUN groupadd --system app && useradd --system --gid app --no-create-home --shell /usr/sbin/nologin app

# Copy only the JAR from the build stage. It stays owned by root (read-only for "app"),
# so the running application cannot modify its own code.
COPY --from=build --chown=root:root /workspace/app.jar app.jar

# All following instructions and the container itself run as the unprivileged user
USER app

# Documents the port Spring Boot listens on (the ECS task definition / load balancer maps it)
EXPOSE 8080

# Start the application. MaxRAMPercentage lets the JVM use up to 75% of the container's memory limit.
# user.timezone=UTC: date-times are stored in MySQL DATETIME columns (no time zone) and must always be UTC.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
