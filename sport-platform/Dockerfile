# Backend image: Spring Boot 3.5 / Java 21.
# Stage 1 builds the jar with Maven (dependencies cached in a separate layer),
# stage 2 is a slim JRE runtime that runs as a non-root user.

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so that source changes do not invalidate this layer.
COPY pom.xml ./
RUN mvn -q -B -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -q -B -DskipTests package \
    && cp target/*.jar app.jar

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is used by the container healthcheck (/actuator/health).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /data/files \
    && chown app:app /data/files

COPY --from=build --chown=app:app /build/app.jar /app/app.jar

USER app
EXPOSE 8080

# Memory limits are passed from compose via JAVA_TOOL_OPTIONS (e.g. -Xmx1024m).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
