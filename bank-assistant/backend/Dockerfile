# ============================================================
# Backend — Spring Boot 4 (Java 21)
# Multi-stage: build the JAR, then ship only the JRE + artifact.
# ============================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# The commit this image was built from, published at /actuator/info (see the
# build-info execution in pom.xml) and stamped on the image label. Passed by
# deploy/promote.sh; `unknown` in a plain local build.
ARG GIT_SHA=unknown
ARG GIT_BRANCH=unknown

# Resolve dependencies first so they cache independently of source changes.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Tests run in CI; skip here to keep image builds fast and offline-friendly.
RUN mvn -B -ntp clean package -DskipTests -Dgit.sha="${GIT_SHA}" -Dgit.branch="${GIT_BRANCH}"

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-jammy

ARG GIT_SHA=unknown
LABEL org.opencontainers.image.revision="${GIT_SHA}"

WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/*.jar app.jar

# Seed directory for the `uploads` named volume (see docker-compose.yml).
# Docker copies this ownership onto a fresh volume, so it must stay
# appuser-owned — the container writes here as uid 1000.
RUN mkdir -p /app/uploads

RUN useradd --create-home --uid 1000 appuser \
 && chown -R appuser:appuser /app
USER appuser

EXPOSE 8081

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=6 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
