FROM gradle:8.7.0-jdk17 AS builder
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon :data-generator:installDist

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/data-generator/build/install/data-generator /app/data-generator
ENV RATE=1000
ENV DURATION=60
ENV BOOTSTRAP_SERVERS=kafka:9092
ENV TOPIC=transaction-submitted
ENTRYPOINT ["/app/data-generator/bin/data-generator"]
