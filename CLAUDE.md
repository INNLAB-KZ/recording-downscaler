# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

recording-downscaler is a Kotlin microservice that downscales video recordings from ozen-broadcast-recorder. It polls the recorder API for recordings with quality above 240p, downloads the .ts files from S3 (DigitalOcean Spaces), transcodes them to 240p using ffmpeg, re-uploads to the same S3 key, and updates video_quality via PATCH API.

## Build & Run

```bash
# Build (requires JDK 21 — Kotlin 2.1.20 doesn't support JDK 26)
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew build

# Build fat JAR
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew shadowJar

# Run
java -jar build/libs/recording-downscaler-1.0.0-all.jar

# Docker
docker build -t recording-downscaler .
docker run --env-file .env recording-downscaler
```

## Architecture

Single polling loop (`Main.kt`) with concurrent processing via coroutines + semaphore:

- **Config** — env var parsing (RECORDER_API_URL, SPACES_*, CONCURRENCY, POLL_INTERVAL, VIDEO_QUALITIES)
- **RecorderApi** — HTTP client for recorder API (GET recordings, PATCH video_quality) using java.net.http
- **S3Storage** — AWS SDK for Kotlin with DigitalOcean Spaces; multipart upload with 64MB parts for large files
- **FFmpeg** — subprocess wrapper for ffmpeg transcoding to 240p
- **Pipeline** — orchestrates download → transcode → upload → verify (HEAD size check) → cleanup → PATCH per recording
- **Recording** — kotlinx.serialization data model matching recorder API response

## Key Behaviors

- Overwrites the same S3 key (URL doesn't change), audio files are never touched
- Idempotent: skips recordings already at 240p (filtered by API query)
- Graceful shutdown on SIGTERM/SIGINT via coroutine cancellation
- Structured JSON logging via logback + logstash-logback-encoder
- Aborts multipart uploads on failure to avoid orphaned parts

## Required Environment Variables

`SPACES_KEY` and `SPACES_SECRET` are required; all others have defaults. See `Config.kt` for full list.
