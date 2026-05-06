# 🌐 Web-Based Language Translator

A **production-grade, full-stack language translation platform** built with Spring Boot 3, React 18, and AWS-native infrastructure. Features a multi-tier caching pipeline, multi-provider failover, JWT-based authentication, async bulk translation via SQS, full observability with Micrometer/Prometheus, and Infrastructure-as-Code with Terraform.

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Project Structure](#-project-structure)
- [Features](#-features)
- [API Reference](#-api-reference)
- [Local Development](#-local-development)
- [Environment Variables](#-environment-variables)
- [Testing](#-testing)
- [CI/CD Pipeline](#-cicd-pipeline)
- [Infrastructure (Terraform)](#-infrastructure-terraform)
- [Makefile Commands](#-makefile-commands)
- [Contributing](#-contributing)

---

## 🔍 Overview

This application provides real-time and batch text translation across 75+ language pairs. It is designed for horizontal scalability and production reliability with:

- **Multi-layer caching**: L1 in-process Caffeine cache (10 min TTL) → L2 Redis distributed cache (24 h TTL)
- **Provider failover chain**: AWS Translate → LibreTranslate → Mock (dev-only), ordered by priority
- **Resilience**: Resilience4j circuit breaker + exponential back-off retry on every translation call
- **Async bulk jobs**: SQS-backed queue for processing large batches without blocking the API
- **Full observability**: Micrometer counters/timers exposed as Prometheus metrics, structured JSON logging via Logback + Logstash encoder, MDC request-id tracing
- **Zero-trust security**: Stateless JWT (access + refresh token) with BCrypt password hashing and role-based access control (USER / ADMIN)
- **Flyway-managed schema**: Repeatable, version-controlled DB migrations

---

## 🛠 Tech Stack

### Backend
| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2.5 |
| Language | Java 17 |
| Security | Spring Security + JJWT 0.12.5 |
| Database | PostgreSQL 16 + Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Cache L1 | Caffeine (in-process) |
| Cache L2 | Redis 7 (Spring Data Redis) |
| Messaging | AWS SQS |
| File Storage | AWS S3 |
| Secrets | AWS Secrets Manager |
| Translation APIs | AWS Translate, LibreTranslate |
| Resilience | Resilience4j (circuit breaker + retry) |
| Observability | Micrometer, Prometheus, Logstash Logback Encoder |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Maven 3.9 + JaCoCo (80% coverage gate) |
| Testing | JUnit 5, Testcontainers (PostgreSQL, LocalStack) |

### Frontend
| Layer | Technology |
|---|---|
| Framework | React 18.3 + Vite 5 |
| Routing | React Router DOM v6 |
| HTTP Client | Axios (with JWT interceptors + auto-refresh) |
| Icons | Lucide React |
| Styling | Vanilla CSS (custom design system) |

### Infrastructure & DevOps
| Tool | Purpose |
|---|---|
| Docker / Docker Compose | Local containerised dev environment |
| LocalStack | AWS service emulation (SQS, S3, Secrets Manager, Translate) |
| Terraform | IaC for AWS (VPC, ECS Fargate, ALB, RDS, ElastiCache, ECR, SQS, S3) |
| GitHub Actions | CI (test + coverage) + CD (dev + prod blue-green deployment) |
| AWS ECS Fargate | Container orchestration (prod) |
| AWS ECR | Private Docker image registry |
| AWS RDS (PostgreSQL) | Managed relational DB (prod, Multi-AZ) |
| AWS ElastiCache (Redis) | Managed Redis cluster (prod) |

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         React 18 SPA (Vite)                             │
│  Login · Register · Translate · Translation History · Admin Dashboard   │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │  HTTPS / JWT Bearer
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                   Spring Boot 3 REST API  (port 8080)                   │
│                                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  ┌───────────┐ │
│  │ AuthController│  │Translation   │  │HistoryController│  │AdminCtrl │ │
│  │ /auth/**     │  │Controller    │  │ /history/**   │  │/admin/**  │ │
│  └──────┬───────┘  │/translations │  └───────┬───────┘  └─────┬─────┘ │
│         │          └──────┬───────┘          │                │       │
│         │                 │                  │                │       │
│  ┌──────▼─────────────────▼──────────────────▼────────────────▼──────┐ │
│  │              Spring Security (JWT stateless, RBAC)                 │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    TranslationService                             │  │
│  │  L1 Caffeine (@Cacheable, 10 min)                                │  │
│  │     └─ L2 Redis (StringRedisTemplate, 24 h)                      │  │
│  │          └─ Provider chain (priority-ordered)                     │  │
│  │               1. AwsTranslateProvider (priority 1)               │  │
│  │               2. LibreTranslateProvider (priority 2)             │  │
│  │               3. MockTranslationProvider (priority 99, dev)       │  │
│  │  @CircuitBreaker + @Retry (Resilience4j)                         │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌────────────┐  ┌────────────┐  ┌────────────────┐  ┌─────────────┐  │
│  │ PostgreSQL │  │   Redis    │  │  AWS SQS (bulk)│  │AWS S3(export│  │
│  │  (JPA +   │  │  (cache)   │  │  BulkController│  │  bucket)    │  │
│  │  Flyway)  │  │            │  │  → SqsService  │  │             │  │
│  └────────────┘  └────────────┘  └────────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### Translation Provider Failover Chain

```
Request arrives
      │
      ▼
[L1 Caffeine hit?] ──YES──► Return cached result (async persist)
      │NO
      ▼
[L2 Redis hit?]    ──YES──► Return cached result (async persist)
      │NO
      ▼
[Try AWS Translate]─OK──► Store in L2 Redis, async persist, return
      │FAIL
      ▼
[Try LibreTranslate]─OK──► Store in L2 Redis, async persist, return
      │FAIL
      ▼
[Circuit Breaker Open?]──► Throw 503 (fallback)
```

---

## 📁 Project Structure

```
web-based-language-translator/
├── frontend/                         # React 18 + Vite SPA
│   ├── src/
│   │   ├── api/
│   │   │   └── client.js             # Axios instance, JWT interceptors, API helpers
│   │   ├── components/
│   │   │   ├── Navbar.jsx
│   │   │   └── ProtectedRoute.jsx
│   │   ├── pages/
│   │   │   ├── Login.jsx             # Register / login form
│   │   │   ├── Translate.jsx         # Main translation UI (19 languages)
│   │   │   ├── History.jsx           # Paginated history, filters, favourites
│   │   │   └── Admin.jsx             # Admin dashboard (users + system stats)
│   │   ├── App.jsx                   # Route declarations
│   │   ├── main.jsx                  # React entry point
│   │   └── index.css                 # Full CSS design system
│   ├── index.html
│   ├── vite.config.js                # Dev proxy → localhost:8080
│   └── package.json
│
├── src/main/java/com/translator/
│   ├── TranslatorApplication.java    # Spring Boot entry point
│   │
│   ├── infrastructure/
│   │   ├── aws/
│   │   │   ├── AwsConfig.java        # AWS SDK bean config (LocalStack-aware)
│   │   │   ├── AwsTranslateProvider.java  # Provider #1: AWS Translate
│   │   │   ├── S3Service.java        # Export bucket operations
│   │   │   └── SqsService.java       # Bulk queue producer
│   │   ├── cache/
│   │   │   └── CaffeineCacheConfig.java   # L1 cache spec (10 min TTL)
│   │   ├── config/
│   │   │   └── OpenApiConfig.java    # Swagger / OpenAPI 3 config
│   │   ├── external/
│   │   │   ├── TranslationProvider.java   # Interface (translate, name, priority)
│   │   │   ├── LibreTranslateProvider.java # Provider #2: LibreTranslate
│   │   │   └── MockTranslationProvider.java # Provider #99: dev stub
│   │   ├── observability/
│   │   │   ├── TranslatorMetrics.java     # Micrometer counters + timers
│   │   │   ├── TranslationProviderHealthIndicator.java # /actuator/health contrib
│   │   │   └── MdcLoggingFilter.java      # MDC request-id injection
│   │   └── security/
│   │       ├── JwtService.java       # Token generation / validation (RSA)
│   │       ├── JwtAuthFilter.java    # OncePerRequestFilter
│   │       └── SecurityConfig.java   # Filter chain, CORS, password encoder
│   │
│   ├── presentation/rest/
│   │   ├── AuthController.java       # /api/v1/auth/**
│   │   ├── TranslationController.java # POST /api/v1/translations
│   │   ├── BulkTranslationController.java # POST /api/v1/translations/bulk
│   │   ├── HistoryController.java    # /api/v1/history/**
│   │   ├── AdminController.java      # /api/v1/admin/** (ADMIN role)
│   │   ├── RootController.java       # GET / health check
│   │   └── GlobalExceptionHandler.java # @RestControllerAdvice
│   │
│   ├── translation/
│   │   ├── model/Translation.java    # JPA entity (JSONB metadata column)
│   │   ├── repository/TranslationRepository.java
│   │   ├── service/
│   │   │   ├── TranslationService.java     # Core: cache → provider → persist
│   │   │   └── TranslationPersistenceService.java # @Async DB writes
│   │   └── dto/
│   │       ├── TranslationRequestDTO.java
│   │       ├── TranslationResponseDTO.java
│   │       ├── HistoryStatsDTO.java
│   │       └── SystemStatsDTO.java
│   │
│   └── user/
│       ├── model/User.java           # JPA entity with Role enum
│       ├── model/Role.java           # USER, ADMIN
│       ├── repository/UserRepository.java
│       ├── service/UserService.java  # Registration, login, refresh
│       └── dto/
│           ├── LoginRequestDTO.java
│           ├── RegisterRequestDTO.java
│           ├── AuthResponseDTO.java  # accessToken + refreshToken
│           ├── RefreshRequestDTO.java
│           └── UserSummaryDTO.java
│
├── src/main/resources/
│   ├── application.yml               # Base config (datasource, cache, JWT, resilience4j)
│   ├── application-dev.yml           # Dev overrides (LocalStack endpoint)
│   ├── application-prod.yml          # Prod overrides (Secrets Manager for JWT key)
│   ├── db/                           # Flyway migration scripts (V1__, V2__, …)
│   └── logback-spring.xml            # JSON logging (Logstash encoder)
│
├── src/test/                         # JUnit 5 + Testcontainers integration tests
│
├── terraform/
│   ├── modules/
│   │   ├── vpc/     ecr/     ecs/
│   │   ├── alb/     rds/     elasticache/
│   │   ├── sqs/     s3/
│   └── environments/
│       ├── dev/     # Dev environment stack
│       └── prod/    # Production stack (Multi-AZ RDS, 2× ECS tasks)
│
├── .github/workflows/
│   ├── ci.yml                        # PR + push: test, coverage, SonarCloud
│   ├── deploy-dev.yml                # Push to develop branch: deploy to dev ECS
│   └── deploy-prod.yml               # Version tag push: blue-green prod deploy
│
├── docker-compose.yml                # Local stack: postgres, redis, localstack, app
├── Dockerfile                        # Multi-stage build (builder → JRE Alpine, non-root)
├── Makefile                          # Developer shortcuts
├── .env.example                      # Environment variable template
└── CONTRIBUTING.md                   # Contribution guidelines
```

---

## ✨ Features

### 🔐 Authentication & Authorization
- Email + password registration and login
- Stateless JWT with **access token** (short-lived) + **refresh token** (rotation)
- `BCryptPasswordEncoder` (strength 12) for password storage
- Role-based access: `USER` (translation, history) · `ADMIN` (user management, system stats)
- Frontend auto-refresh on 401 via Axios response interceptor

### ✍️ Translation
- **19 languages** in the UI: English, Spanish, French, German, Italian, Portuguese, Dutch, Russian, Japanese, Korean, Chinese (Simplified), Arabic, Hindi, Bengali, Turkish, Vietnamese, Polish, Ukrainian, Swedish
- Auto-detect source language option
- Language swap button (swaps both selector and text)
- Keyboard shortcut: `Ctrl+Enter` to translate
- Character counter (5000 char max)
- Post-translation stats: word count, character count, detected language
- Provider badge shows which backend provider served the result (or `⚡ Cached`)

### ⚡ Multi-Tier Caching
| Level | Storage | TTL | Key |
|---|---|---|---|
| L1 | Caffeine (in-process) | 10 min | `sourceText:sourceLang:targetLang` |
| L2 | Redis | 24 h | `translation:l2:<MD5>:<targetLang>` |

Cache hits trigger async persistence so the response is not slowed by DB writes.

### 📦 Bulk Translation (SQS)
- `POST /api/v1/translations/bulk` accepts a list of strings
- Each entry is serialised via Jackson and enqueued onto the SQS bulk queue
- Returns a `jobId` immediately (async processing)
- Jackson serialisation prevents JSON injection from user-supplied text

### 📜 Translation History
- Paginated list (10 per page) with full-text search and language filter
- Toggle **favourites** (star) per entry
- Delete individual entries or **clear all** history
- Stats panel: total translations, favourites count, translations this week, most-used language pair

### 🔧 Admin Dashboard
- Paginated user list with translation counts and account status
- Deactivate user accounts
- System-wide stats: total users, translations today, provider breakdown, cache hit rates

### 📊 Observability
- `GET /actuator/health` — health details for all components
- `GET /actuator/prometheus` — Prometheus-compatible metrics including:
  - `translation.requests.total` (tagged: `target_language`, `provider`, `cached`)
  - `translation.latency` (tagged: `provider`)
  - `auth.login.attempts` (tagged: `success`)
  - `cache.hit` (tagged: `level`, `result`)
- MDC filter injects `requestId` into every log line
- Structured JSON logging via Logstash Logback Encoder (prod)

### 🛡 Resilience
- **Circuit Breaker** (`translationService`): sliding window 10, failure threshold 50%, 10 s wait in open state, 3 calls in half-open
- **Retry** (`translationService`): 3 attempts, 500 ms base wait, ×2 exponential back-off
- **Fallback**: returns `503 Service Temporarily Unavailable` when circuit is open

---

## 📡 API Reference

All endpoints are documented interactively at **`http://localhost:8080/swagger-ui/index.html`** when the app is running.

### Authentication — `/api/v1/auth`

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/register` | ✗ | Register a new user |
| `POST` | `/auth/login` | ✗ | Login; returns access + refresh tokens |
| `POST` | `/auth/refresh` | ✗ | Refresh access token |
| `POST` | `/auth/logout` | Bearer | Invalidate refresh token |

**Register request body:**
```json
{ "email": "user@example.com", "password": "SecurePass123!" }
```

**Login / refresh response:**
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "email": "user@example.com",
  "role": "USER"
}
```

---

### Translation — `/api/v1/translations`

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/translations` | Bearer | Translate text (single request) |
| `POST` | `/translations/bulk` | Bearer | Submit async bulk job via SQS |

**Translation request body:**
```json
{
  "sourceText": "Hello, world!",
  "sourceLanguage": "en",
  "targetLanguage": "es"
}
```

**Translation response:**
```json
{
  "translationId": "550e8400-e29b-41d4-a716-446655440000",
  "translatedText": "¡Hola, mundo!",
  "sourceLanguageDetected": "en",
  "targetLanguage": "es",
  "providerUsed": "AWS_TRANSLATE",
  "servedFromCache": false,
  "wordCount": 2,
  "characterCount": 13
}
```

**Bulk request body:**
```json
["Hello world", "How are you?", "Thank you"]
```

**Bulk response:**
```json
{ "jobId": "a1b2c3d4-..." }
```

---

### History — `/api/v1/history`

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/history` | Bearer | Paginated history (query params: `page`, `size`, `search`, `targetLanguage`, `favoritesOnly`) |
| `GET` | `/history/stats` | Bearer | User's aggregate stats |
| `PATCH` | `/history/{id}/favorite` | Bearer | Toggle favourite flag |
| `DELETE` | `/history/{id}` | Bearer | Delete single entry |
| `DELETE` | `/history` | Bearer | Delete all history for the current user |

---

### Admin — `/api/v1/admin` *(ADMIN role required)*

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/admin/users` | Bearer (ADMIN) | Paginated user list with stats |
| `GET` | `/admin/users/{userId}/history` | Bearer (ADMIN) | View any user's history |
| `PATCH` | `/admin/users/{userId}/deactivate` | Bearer (ADMIN) | Deactivate a user account |
| `GET` | `/admin/stats` | Bearer (ADMIN) | System-wide aggregate stats |

---

## 🚀 Local Development

### Prerequisites

| Tool | Version |
|---|---|
| Java | 17+ |
| Maven | 3.9+ |
| Docker Desktop | Latest |
| Node.js | 18+ |

### 1. Clone the Repository

```bash
git clone https://github.com/GowthamReddyNagabhusi/web-based-language-translator.git
cd web-based-language-translator
```

### 2. Configure Environment

```bash
cp .env.example .env
# Edit .env if needed — defaults work out-of-the-box with docker-compose
```

### 3. Start All Services

```bash
make up
# Starts: postgres:16, redis:7, localstack:3, and the Spring Boot app
```

> The app will be ready at **`http://localhost:8080`** once the health check passes (~60 s on first run due to image pulls and Flyway migrations).

### 4. Start the Frontend Dev Server

```bash
cd frontend
npm install
npm run dev
# Frontend: http://localhost:5173
# Vite proxies /api/v1 → localhost:8080
```

### 5. Verify

```bash
# App health
curl http://localhost:8080/actuator/health

# OpenAPI spec
curl http://localhost:8080/v3/api-docs

# Swagger UI
open http://localhost:8080/swagger-ui/index.html
```

---

## 🔐 Environment Variables

Copy `.env.example` to `.env`. All defaults work with docker-compose out of the box.

| Variable | Default (local) | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_USER` | `translator_user` | DB username |
| `DB_PASSWORD` | `password` | DB password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(empty)* | Redis password (if any) |
| `AWS_REGION` | `us-east-1` | AWS region |
| `AWS_ACCESS_KEY_ID` | `test` | AWS access key (LocalStack) |
| `AWS_SECRET_ACCESS_KEY` | `test` | AWS secret key (LocalStack) |
| `AWS_ENDPOINT` | `http://localhost:4566` | LocalStack endpoint (dev only) |
| `S3_EXPORT_BUCKET` | `translator-exports-local` | S3 bucket name for exports |
| `SQS_BULK_QUEUE_NAME` | `bulk-translations-queue-local` | SQS queue for bulk jobs |
| `JWT_SECRET_KEY_ID` | `local-dev-jwt-key` | JWT key ID (prod: from Secrets Manager) |

> **Production**: AWS credentials, DB password, and JWT keys are injected via GitHub Actions secrets and AWS Secrets Manager — never stored in source control.

---

## 🧪 Testing

The project enforces an **80% line coverage minimum** via JaCoCo, checked during `mvn verify`.

### Run All Tests

```bash
make test
# Equivalent to: mvn clean verify
# Testcontainers auto-starts real PostgreSQL and LocalStack containers for integration tests
```

### Run Unit Tests Only

```bash
make test-unit
# Equivalent to: mvn test -Dgroups="unit"
```

### Generate Coverage Report

```bash
make coverage
# Report at: target/site/jacoco/index.html
```

### What's Tested

- **Integration tests** (Testcontainers): full DB round-trips with a real PostgreSQL container; AWS service mocks via LocalStack
- **Unit tests**: service layer logic, caching behaviour, JWT generation/validation, provider failover
- **Security tests**: Spring Security test slice to validate endpoint access controls

---

## 🔄 CI/CD Pipeline

### CI — `ci.yml`

Triggers on **all pull requests** to `main` and pushes to `rebuild/production-grade`.

```
Checkout → Java 17 setup → Maven cache → mvn clean verify (Testcontainers + JaCoCo)
  → Upload JaCoCo report artifact
  → SonarCloud analysis (PR only)
  → Post coverage summary comment on PR
```

- **Coverage gate**: Build fails if line coverage drops below 80%
- **Concurrency**: cancels in-progress runs for the same ref

### Deploy Dev — `deploy-dev.yml`

Triggers on push to the `develop` branch. Builds + pushes to ECR, then updates the dev ECS service.

### Deploy Prod — `deploy-prod.yml`

Triggers on **version tags** matching `v*.*.*` (e.g., `v1.2.3`).

```
Manual Approval Gate (GitHub Environment reviewers required)
  → Full test suite
  → Configure AWS credentials
  → Login to ECR
  → Build + push Docker image (tagged with version + :stable)
  → Register new ECS task definition
  → Blue-Green deploy: update ECS service
  → Wait for service stability
  → Production smoke test (curl ALB /actuator/health)
  → Auto-rollback on failure (reverts to previous task definition)
  → Create GitHub Release
```

**Required GitHub Secrets (prod):**
| Secret | Description |
|---|---|
| `AWS_ACCESS_KEY_ID_PROD` | IAM access key for prod deployment |
| `AWS_SECRET_ACCESS_KEY_PROD` | IAM secret key for prod deployment |
| `SONAR_TOKEN` | SonarCloud project token |

---

## 🏗 Infrastructure (Terraform)

Terraform modules under `terraform/modules/` are consumed by environment configs under `terraform/environments/`.

### Modules

| Module | AWS Service | Notes |
|---|---|---|
| `vpc` | VPC, subnets, IGW, NAT | Public + private subnets across 3 AZs |
| `ecr` | Elastic Container Registry | Private image repo |
| `ecs` | ECS Fargate cluster + service | 2 tasks min for HA; 1024 CPU / 2048 MB |
| `alb` | Application Load Balancer | Public-facing; routes to ECS |
| `rds` | RDS PostgreSQL 16 | `db.r6g.large`, Multi-AZ enabled |
| `elasticache` | ElastiCache Redis | `cache.r6g.large`, 2 nodes |
| `sqs` | SQS FIFO queue | Bulk translation async queue |
| `s3` | S3 bucket | Translation export storage |

### Deploying Production Infrastructure

```bash
cd terraform/environments/prod

terraform init
terraform plan -var="db_password=<STRONG_PASSWORD>" -var="image_tag=v1.0.0"
terraform apply -var="db_password=<STRONG_PASSWORD>" -var="image_tag=v1.0.0"
```

**Outputs:**
- `alb_url` — public ALB DNS name
- `ecr_repository` — ECR image URL
- `rds_endpoint` — RDS connection string
- `redis_endpoint` — ElastiCache Redis endpoint

> Terraform state is stored remotely in S3: `translator-tf-state-prod/prod/terraform.tfstate`

---

## 🛠 Makefile Commands

```
make up          # Start all containers (postgres, redis, localstack, app)
make down        # Stop and remove all containers + volumes
make logs        # Tail application logs
make build       # Build Docker image locally (no compose)
make clean       # Remove containers, volumes, and orphans

make test        # Run full test suite (Testcontainers + JaCoCo)
make test-unit   # Run unit tests only
make coverage    # Generate JaCoCo HTML coverage report

make migrate     # Run Flyway migrations against local postgres
make help        # Show all available targets
```

---

## 🤝 Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) for the full guide. Quick summary:

- Follow **DDD layering** — no business logic in controllers
- Use `SLF4J` for logging — no `System.out.println`
- No hardcoded credentials — use environment variables or AWS Secrets Manager
- All new endpoints must have `@Operation` and `@ApiResponse` Swagger annotations
- Maintain ≥ 80% line coverage
- Branch naming: `feat/`, `fix/`, `chore/` prefixes
- Commit format: `type(scope): description`

### PR Checklist

- [ ] All tests pass (`make test`)
- [ ] Coverage remains ≥ 80%
- [ ] No hardcoded credentials
- [ ] New endpoints documented with Swagger annotations
- [ ] Commit messages follow `type(scope): description` format

---

## 📄 License

This project is licensed under the **MIT License**.

---

<div align="center">
  Built with ☕ Spring Boot · ⚛️ React · ☁️ AWS · 🐘 PostgreSQL · 🟥 Redis
</div>
