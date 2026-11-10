# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy

# curl is used by the container health check (GET /api/v1/health).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home mte

WORKDIR /app

# The deployment sets SPRING_PROFILES_ACTIVE (staging or prod) and the DB_* and MTE_* variables.
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

COPY --from=build /workspace/target/pdg-mte-api-0.0.1-SNAPSHOT.jar /app/app.jar

USER mte

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS "http://localhost:${PORT:-8081}/api/v1/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar /app/app.jar"]
