FROM gradle:8.7.0-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon :platform-core:bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/platform-core/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
