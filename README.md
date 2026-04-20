# recording-downscaler

Микросервис для даунскейла видеозаписей из ozen-broadcast-recorder. Находит записи с качеством выше 240p, скачивает из S3, конвертирует в 240p MP4 через ffmpeg, загружает обратно и обновляет метаданные через API.

## Pipeline

```
GET /api/recordings?status=done&video_quality=360p
         │
         ▼
  Скачать .ts из S3
         │
         ▼
  ffmpeg → 240p .mp4 (faststart)
         │
         ▼
  Загрузить .mp4 в S3 (новый ключ .ts → .mp4)
         │
         ▼
  HEAD проверка размера
         │
         ▼
  Удалить старый .ts из S3
         │
         ▼
  PATCH /api/recordings/{id}
  (video_quality, url, s3_key, file_size_bytes)
```

- Аудио файлы не трогаются
- Обработка идемпотентна — записи с quality=240p пропускаются
- Polling с настраиваемым интервалом и concurrency

## Быстрый старт

```bash
cp .env.example .env
# Заполнить SPACES_KEY, SPACES_SECRET, RECORDER_API_URL, SPACES_CDN_BASE

# CPU (Mac / dev)
docker compose up -d

# GPU (сервер с NVIDIA)
docker compose -f docker-compose.gpu.yml up -d
```

## Тест одной записи

```bash
# Через .env
echo "RECORDING_ID=<uuid>" >> .env
docker compose run downscaler

# Или через -e
docker compose run -e RECORDING_ID=<uuid> downscaler
```

Обработает одну запись и завершится.

## Конфигурация

| Переменная | Обязательная | По умолчанию | Описание |
|---|---|---|---|
| `RECORDER_API_URL` | да | `http://localhost:8080` | URL API рекордера |
| `RECORDER_API_KEY` | нет | — | API ключ (X-API-Key header) |
| `SPACES_ENDPOINT` | нет | `https://fra1.digitaloceanspaces.com` | S3 endpoint |
| `SPACES_BUCKET` | нет | `ozen-recordings` | Имя бакета |
| `SPACES_KEY` | да | — | S3 access key |
| `SPACES_SECRET` | да | — | S3 secret key |
| `SPACES_FILE_ACL` | нет | `private` | ACL для загружаемых файлов |
| `SPACES_CDN_BASE` | нет | `https://ozen-recordings.fra1.cdn.digitaloceanspaces.com` | CDN base URL |
| `CONCURRENCY` | нет | `3` | Параллельная обработка записей |
| `POLL_INTERVAL` | нет | `300` | Секунды между циклами |
| `VIDEO_QUALITIES` | нет | `360p,480p,720p,1080p` | Качества для даунскейла |
| `USE_GPU` | нет | `false` | NVIDIA NVENC ускорение |
| `RECORDING_ID` | нет | — | Обработать одну запись и выйти |

## GPU

Для использования NVIDIA GPU (например RTX 4070 Ti Super):

1. Установить [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html)
2. Использовать `Dockerfile.gpu` / `docker-compose.gpu.yml`
3. Установить `USE_GPU=true`

ffmpeg команды:
- **CPU:** `libx264 -preset ultrafast -crf 40`
- **GPU:** `h264_nvenc -preset p1 -cq 40 -hwaccel cuda`

## Сборка

```bash
# Локально (требует JDK 21)
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home ./gradlew shadowJar

# Docker
docker compose build

# Multi-platform push
docker buildx build --platform linux/amd64,linux/arm64 -t innlabkz/recording-downscaler:latest --push .
```

## Архитектура

```
Main.kt          — точка входа, polling loop, graceful shutdown
Config.kt        — конфигурация из env vars
RecorderApi.kt   — HTTP клиент для API рекордера (GET, PATCH)
S3Storage.kt     — скачивание, загрузка (multipart 64MB), удаление, HEAD
FFmpeg.kt        — обёртка над ffmpeg (CPU/GPU)
Pipeline.kt      — оркестрация: download → transcode → upload → verify → delete → patch
Recording.kt     — data model
```
