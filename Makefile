.PHONY: up down logs test migrate build clean

## ─── Local Dev ──────────────────────────────────────────────────────────────

up:          ## Start all services (postgres, redis, localstack, app)
	docker compose up -d --build

down:        ## Stop and remove all containers
	docker compose down -v

logs:        ## Tail application logs
	docker compose logs -f app

build:       ## Build the Docker image locally (no compose)
	docker build -t translator-app:local .

clean:       ## Remove Docker volumes (WARNING: destroys local data)
	docker compose down -v --remove-orphans

## ─── Testing ────────────────────────────────────────────────────────────────

test:        ## Run full test suite (Testcontainers spins up its own infra)
	mvn clean verify

test-unit:   ## Run unit tests only
	mvn test -Dgroups="unit"

coverage:    ## Generate JaCoCo coverage report
	mvn clean verify
	@echo "Report: target/site/jacoco/index.html"

## ─── Database ───────────────────────────────────────────────────────────────

migrate:     ## Run Flyway migrations against the local postgres
	mvn flyway:migrate \
		-Dflyway.url=jdbc:postgresql://localhost:5432/translator_db \
		-Dflyway.user=translator_user \
		-Dflyway.password=password

## ─── Help ───────────────────────────────────────────────────────────────────

help:        ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'
