# Multi-stage image for service-scheduler-server (design D10):
# Gradle bootJar inside the container, then a slim JRE 25 runtime.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew :service-scheduler-server:bootJar --no-daemon -x check

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/service-scheduler-server/build/libs/service-scheduler-server-1.0.0-SNAPSHOT.jar app.jar

RUN useradd --system --uid 1001 scheduler
USER scheduler

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
