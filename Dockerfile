# syntax=docker/dockerfile:1
# One image for both services: the API runs as-is, the worker with SPRING_PROFILES_ACTIVE=worker.
# Build on this VM with: docker build --network host   (see scripts/ecr-push.sh)

# ---- build: JDK, Maven wrapper, jar split into layers ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
# The Maven repository lives in a BuildKit cache mount, so dependencies download once, not on every build.
# Tests are skipped here on purpose: CI runs `./mvnw verify` (with Testcontainers) before an image is built.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/storefront-*.jar extract --layers --destination /extracted

# ---- runtime: JRE only, non-root ----
FROM eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
# Least-changing layers first so a code-only change reuses the dependency layers.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

USER 10001:10001
EXPOSE 8080
# Heap as a share of the container limit. 70 is a starting value, not a measured one (CLAUDE.md section 5).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["java", "-jar", "/app/storefront-0.0.1-SNAPSHOT.jar"]
