# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 --mount=type=secret,id=proxy_ca --mount=type=secret,id=maven_settings \
    if [ -f /run/secrets/proxy_ca ]; then \
      cp "$JAVA_HOME/lib/security/cacerts" /tmp/build-cacerts; \
      keytool -importcert -noprompt -alias session-proxy -file /run/secrets/proxy_ca \
        -keystore /tmp/build-cacerts -storepass changeit; \
      export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/tmp/build-cacerts -Djavax.net.ssl.trustStorePassword=changeit"; \
    fi; \
    if [ -f /run/secrets/maven_settings ]; then set -- -s /run/secrets/maven_settings; else set --; fi; \
    mvn "$@" -B -DskipTests package && rm -f /tmp/build-cacerts

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/sport-platform-0.0.1-SNAPSHOT.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["java", "-jar", "app.jar"]
