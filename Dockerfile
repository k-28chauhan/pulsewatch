# syntax=docker/dockerfile:1

# ---------- Stage 1: build the jar ----------
# The JDK (compiler) and Maven's downloads live only in this stage; none of it reaches the final image.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy ONLY the build definition first and download dependencies. This layer is cached until pom.xml changes,
# so editing Java code does not re-download the internet.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B dependency:go-offline

COPY src src
# Tests run in CI (they need Docker for Testcontainers), not inside the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -DskipTests package \
 && java -Djarmode=tools -jar target/pulseWatch-*.jar extract --destination /extracted

# ---------- Stage 2: a small runtime image ----------
# Only a JRE (no compiler, no Maven, no source code), on Alpine Linux.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# A normal user instead of root: if the app were ever compromised, the attacker would not own the container.
RUN addgroup -S app && adduser -S app -G app
USER app

# Dependencies and application code in separate layers: a code change re-uploads a few KB, not 60 MB of libraries.
COPY --from=build --chown=app:app /extracted/lib ./lib
COPY --from=build --chown=app:app /extracted/pulseWatch-*.jar ./app.jar

# Use at most 75% of the container's memory limit for the heap; exit (and let Docker restart us) on OutOfMemoryError.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
