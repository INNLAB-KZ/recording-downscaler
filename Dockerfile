FROM gradle:8.12-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY src/ src/
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]

# Single platform:
#   docker build -t innlabkz/recording-downscaler:latest .
#   docker push innlabkz/recording-downscaler:latest
#
# Multi-platform (amd64 + arm64):
#   docker buildx build --platform linux/amd64,linux/arm64 -t innlabkz/recording-downscaler:latest --push .
