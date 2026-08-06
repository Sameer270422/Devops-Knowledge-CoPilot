# Uses the committed wrapper (backend/gradlew) instead of a gradle:8.9-jdk17 image now
# that one exists — one less place the Gradle version has to be kept in sync by hand.
# See docs/06-security-hardening.md for why the wrapper exists at all: a system-wide
# Gradle 9.x install is incompatible with Spring Boot 3.3.2's Gradle plugin, which is
# exactly the failure a missing wrapper used to leave every contributor to hit blind.
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Refresh Alpine's own packages at build time rather than trusting whatever the base
# image tag happened to be pinned to when it was last published — this is what actually
# fixes OS-level CVEs Trivy finds (e.g. libexpat, p11-kit), since those aren't touched by
# bumping Spring Boot at all. Runs on every build, so it never goes stale the way a fixed
# base image tag would.
RUN apk update && apk upgrade --no-cache
# Run as a dedicated non-root user rather than the default root — per the security
# plan's "non-root containers" commitment. /data/documents is created and chowned here
# (rather than left to whatever the docker-compose volume mount defaults to) so that
# when Compose first creates that named volume, it inherits the right ownership from the
# image instead of showing up owned by root.
RUN addgroup -S copilot && adduser -S -G copilot copilot \
    && mkdir -p /data/documents \
    && chown -R copilot:copilot /app /data/documents
COPY --from=build --chown=copilot:copilot /app/build/libs/*.jar app.jar
USER copilot
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
