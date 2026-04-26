# Language Translator — Production Grade

[![CI](https://github.com/GowthamReddyNagabhusi/web-based-language-translator/actions/workflows/ci.yml/badge.svg)](https://github.com/GowthamReddyNagabhusi/web-based-language-translator/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/coverage-80%25%2B-brightgreen)](https://github.com/GowthamReddyNagabhusi/web-based-language-translator)
[![Java](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A production-ready language translation REST API supporting **75+ languages** via AWS Translate, with Redis/Caffeine caching, JWT authentication, paginated history, bulk SQS queue processing, and S3 pre-signed export.

---

## Architecture

```
                  ┌─────────────────────────────────────────────────────────┐
                  │                   Internet / Client                     │
                  └──────────────────────────┬──────────────────────────────┘
                                             │
                                    ┌────────▼────────┐
                                    │  AWS ALB (HTTP) │
                                    └────────┬────────┘
                                             │
                         ┌───────────────────▼───────────────────┐
                         │         ECS Fargate (Spring Boot)      │
                         │  ┌──────────────────────────────────┐  │
                         │  │  JwtAuthFilter → SecurityConfig  │  │
                         │  │  TranslationService              │  │
                         │  │    L1: Caffeine (10min TTL)      │  │
                         │  │    L2: Redis (24hr TTL)          │  │
                         │  │    AWS Translate (primary)       │  │
                         │  │    LibreTranslate (fallback)     │  │
                         │  │  HistoryController (paginated)   │  │
                         │  │  BulkTranslation → SQS Queue    │  │
                         │  └──────────────────────────────────┘  │
                         └───┬───────────────────────────┬─────────┘
                             │                           │
              ┌──────────────▼──────────┐   ┌───────────▼───────────┐
              │  RDS PostgreSQL 16      │   │  ElastiCache Redis 7   │
              │  (Multi-AZ in prod)     │   │  (token blacklist +    │
              └─────────────────────────┘   │   L2 translation cache)│
                                            └────────────────────────┘
                             │
              ┌──────────────▼──────────┐
              │  AWS Services           │
              │  • Translate (primary)  │
              │  • S3 (export bucket)   │
              │  • SQS (bulk jobs)      │
              │  • Secrets Manager      │
              └─────────────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Security | Spring Security 6 + JWT RS256 (jjwt) |
| Database | PostgreSQL 16 via Spring Data JPA + Flyway |
| Cache L1 | Caffeine (in-memory, 10 min TTL, 1000 entries) |
| Cache L2 | Redis (24 hr TTL, token blacklisting) |
| Translation | AWS Translate → LibreTranslate (fallback) |
| Async/Queue | AWS SQS (bulk translation jobs) |
| Storage | AWS S3 (pre-signed export URLs) |
| Resilience | Resilience4j (Circuit Breaker + Retry w/ exponential backoff) |
| Observability | Spring Actuator + Micrometer + Prometheus + JSON MDC logging |
| Docs | SpringDoc OpenAPI 3 / Swagger UI |
| Infra | Docker Compose (local) / Terraform ECS Fargate (AWS) |
| CI/CD | GitHub Actions (CI + rolling dev deploy + blue-green prod deploy) |
| Testing | JUnit 5 + Mockito + Testcontainers + JaCoCo (80% minimum) |

---

## Local Setup

### Prerequisites

- Docker Desktop (running)
- Java 17+
- Maven 3.9+
- Make (optional, for convenience targets)

### 1. Start infrastructure

```bash
make up
# OR
docker compose up -d
```

This starts:
- PostgreSQL 16 on `localhost:5432`
- Redis 7 on `localhost:6379`
- LocalStack (S3, SQS, Secrets Manager, Translate) on `localhost:4566`
- Spring Boot app on `localhost:8080`

### 2. Verify health

```bash
curl localhost:8080/actuator/health
# Expected: {"status":"UP", ...}
```

### 3. API Documentation

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## API Quick Reference

### Authentication

```bash
# Register
curl -X POST localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'

# Login → get access + refresh tokens
curl -X POST localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"password123"}'
```

### Translation

```bash
curl -X POST localhost:8080/api/v1/translations \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"sourceText":"Hello, how are you?","targetLanguage":"hi","sourceLanguage":"auto"}'
```

### History

```bash
# Paginated history
curl "localhost:8080/api/v1/history?page=0&size=20" \
  -H "Authorization: Bearer <access_token>"

# Filtered history
curl "localhost:8080/api/v1/history?targetLanguage=hi&favoritesOnly=true" \
  -H "Authorization: Bearer <access_token>"

# Stats
curl localhost:8080/api/v1/history/stats \
  -H "Authorization: Bearer <access_token>"
```

### Bulk Translation

```bash
curl -X POST localhost:8080/api/v1/translations/bulk \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '["Hello","World","How are you?"]'
# Returns: {"jobId": "<uuid>"}
```

---

## Make Targets

```bash
make up        # Start all Docker services
make down      # Stop and remove containers
make logs      # Tail app logs
make test      # Run full test suite (Testcontainers)
make coverage  # Generate JaCoCo HTML report
make migrate   # Run Flyway migrations against local Postgres
make build     # Build Docker image locally
make clean     # Destroy Docker volumes (caution: data loss)
```

---

## Design Decisions

| Decision | Rationale |
|---|---|
| **PostgreSQL** | ACID compliance, JSONB support for translation metadata, excellent Spring Data JPA integration |
| **Redis** | Fast token blacklisting for JWT logout, L2 cache with 24hr TTL for translation results |
| **ECS Fargate** | No infrastructure management, pay-per-use, native ECR integration, easy blue-green deploy |
| **RS256 JWT** | Asymmetric signing — public key can be distributed for verification without exposing the private key |
| **Caffeine L1** | Sub-millisecond in-memory cache for hot translations within a single pod |
| **Resilience4j** | Circuit breaker prevents cascade failure when AWS Translate degrades; retry with backoff handles transient errors |
| **SQS for bulk** | Decouples bulk submission from processing; provides durability via DLQ; scales independently |
| **S3 pre-signed URLs** | Export files served directly from S3, not through the application — avoids server-side streaming overhead |

---

## AWS Architecture (Production)

- **VPC**: Multi-AZ (3 AZs), public subnets for ALB, private subnets for ECS/RDS/Redis
- **ALB**: Application Load Balancer with health check on `/actuator/health`
- **ECS Fargate**: 2+ tasks in prod, `desired_count` autoscaling
- **RDS PostgreSQL**: Multi-AZ, encrypted at rest, deletion protection enabled
- **ElastiCache**: Multi-node Redis cluster in private subnets
- **Secrets**: RSA JWT private key + DB password stored in AWS Secrets Manager

---

## CI/CD Overview

| Trigger | Workflow | Action |
|---|---|---|
| Pull Request | `ci.yml` | Test + Coverage + SonarCloud |
| Merge to `main` | `deploy-dev.yml` | CI + ECR push + rolling ECS deploy + smoke test |
| Push tag `v*.*.*` | `deploy-prod.yml` | Manual approval + ECR push + blue-green + auto-rollback |
