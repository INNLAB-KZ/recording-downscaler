# Project Rules

### Backend
- Kotlin / Spring Boot 4
- Package-by-feature: kz.innlab.{projectName}.{feature}/
- Postgres 18
- Build: `./mvnw clean package`
- For production: docker-compose.yml
- User hypersistence-utils-hibernate for JSONB columns

## Conventions
- UUID v7 for IDs and Bigint

## Structure
- /src/main/kotlin/...
- /docker-compose.yml

## Commands
- Build: ./mvnw clean package
- Run: docker compose up -d

## Package Structure
Use package-by-feature, NOT package-by-layer.

## Profiles
- `application.yml` — общие настройки (shared)
- `application-dev.yml` — локальная разработка (postgres DB/localhost)
- `application-prod.yml` — продакшн (реальная postgres DB, secrets через env vars)
- Активация: `spring.profiles.active=dev` или `prod`
- Секреты (пароли, ключи) только в prod через `${ENV_VAR}`, никогда хардкод
