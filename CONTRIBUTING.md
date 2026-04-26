# Contributing to Language Translator

Thank you for your interest in contributing. Please follow the guidelines below.

---

## Ground Rules

- **Test before committing.** Every commit must compile and pass all tests.
- **No `System.out.println`.** Use SLF4J logging at the correct level.
- **No hardcoded credentials.** All secrets via environment variables or AWS Secrets Manager.
- **No TODO comments in merged code.** Open a GitHub Issue instead.
- **Coverage minimum 80%.** JaCoCo enforces this in the Maven build.

---

## Branch & Commit Conventions

- Fork the repo and create a feature branch from `main`.
- Branch naming: `feat/short-description`, `fix/short-description`, `chore/short-description`.
- Commit message format: `type(scope): description`
  - Types: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`
  - Examples:
    - `feat(auth): add JWT refresh token rotation`
    - `fix(cache): handle Redis timeout gracefully`
    - `test(translation): add Testcontainers integration test`

---

## Development Setup

```bash
# 1. Start infrastructure
make up

# 2. Verify the app is healthy
curl localhost:8080/actuator/health

# 3. Run tests
make test

# 4. Generate coverage report
make coverage
# open target/site/jacoco/index.html
```

---

## Package Structure (DDD)

```
com.translator
├── domain/
│   ├── translation/       (model, repository, service, dto)
│   └── user/              (model, repository, service, dto)
├── application/
│   └── usecase/           (orchestration use cases)
├── infrastructure/
│   ├── aws/               (AWS SDK clients)
│   ├── cache/             (Caffeine config)
│   ├── external/          (fallback providers)
│   ├── observability/     (metrics, MDC filter, health indicators)
│   └── security/          (JWT, filters, SecurityConfig)
└── presentation/
    └── rest/              (all @RestController classes)
```

Do not add business logic to controllers. Controllers delegate to services only.

---

## Pull Request Checklist

- [ ] All tests pass (`make test`)
- [ ] Coverage remains ≥ 80%
- [ ] No hardcoded credentials or `System.out.println`
- [ ] New endpoints documented with `@Operation` and `@ApiResponse`
- [ ] Commit messages follow `type(scope): description` format
- [ ] PR description explains the change and links to any relevant issue
