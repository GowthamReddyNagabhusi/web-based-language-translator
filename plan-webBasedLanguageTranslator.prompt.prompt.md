## Plan: Production-Ready Translator Launch

Harden security/reliability first, then add auth + deployment architecture compatible with Vercel by splitting static frontend hosting from Java API hosting (or migrating API to Vercel-compatible runtime). Recommended path for 1-2 weeks: keep Java Jetty backend, deploy frontend on Vercel, deploy API on Render/Railway/Fly.io, and connect with strict CORS + env-based config.

**Steps**
1. Phase 1 - Deployment Architecture Decision and Baseline (Day 1)  
   Decide runtime topology: Option A (recommended): Vercel for static frontend + separate Java container host for API; Option B: migrate backend to Vercel serverless functions (Node/Java function rewrite). This plan assumes Option A because it is fastest and lowest risk for 1-2 weeks.  
   Create environment strategy with dev/staging/prod values and separate frontend API base URL.
2. Phase 2 - Security Hardening (Days 1-3, blocks Phases 4-6)  
   Replace wildcard CORS with allowlist from env/config in TranslateServlet; include preflight and methods whitelist.  
   Add response security headers in a servlet filter (CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy).  
   Add request size/body limits and stricter validation for lang/text.  
   Add basic abuse protection: IP-based rate limiting per endpoint.  
   Define secret handling (no secrets in repository; env only).
3. Phase 3 - Reliability and Observability (Days 2-5, parallel with Phase 4 after step 2.1)  
   Add retries with exponential backoff and bounded timeout strategy for upstream translation providers.  
   Add circuit-breaker behavior and provider health state to avoid repeated failing calls.  
   Add request correlation IDs and structured logs in backend; include request latency and status.  
   Expand health endpoint to include shallow readiness and optional dependency status.
4. Phase 4 - Authentication for User Login (Days 3-7, depends on Phase 2 CORS/security baseline)  
   Add auth architecture: choose managed auth (Clerk/Auth0/Firebase) for frontend login and backend JWT verification.  
   Add login-protected flows for translation history sync (if backend persistence is introduced).  
   If keeping local-only history in MVP, still protect API with JWT or API key tied to authenticated session.
5. Phase 5 - Frontend Production Improvements (Days 4-8, parallel with Phase 3/4)  
   Introduce configurable API endpoint in frontend (no hardcoded /translate for multi-host deploy).  
   Improve API failure UX (network vs timeout vs server errors), loading states, and request deduplication via AbortController cancellation.  
   Improve accessibility: labels, ARIA progressbar, toast alert roles, focus states, and RTL readiness for supported languages where needed.  
   Harden history import with schema validation and field sanitization.
6. Phase 6 - CI/CD, Quality Gates, and Release Process (Days 6-10, depends on Phase 2 baseline)  
   Add test scaffolding (backend JUnit + servlet/API integration tests; minimal frontend unit tests for API client/utils).  
   Add CI workflow: build, test, dependency vulnerability scan, Docker image build, and deployment jobs.  
   Stop skipping tests in production build path.  
   Add semantic versioning and changelog workflow.
7. Phase 7 - Go-Live and Post-Launch Features (Days 10-14, depends on Phases 2-6)  
   Perform staging soak test and load test for low-traffic target.  
   Configure production monitoring/alerts (error-rate, latency, upstream failure ratio).  
   Launch with rollback runbook and incident checklist.

**Relevant files**
- c:/Prjoects/web-based-language-translator/src/main/java/com/example/translator/TranslateServlet.java - Replace wildcard CORS, validate body/headers, and enforce request constraints.
- c:/Prjoects/web-based-language-translator/src/main/java/com/example/translator/WebServer.java - Register security and request-id filters; centralize config loading and startup validation.
- c:/Prjoects/web-based-language-translator/src/main/java/com/example/translator/Translator.java - Add resilient upstream calling strategy (retry/backoff/circuit-breaker) and richer error mapping.
- c:/Prjoects/web-based-language-translator/src/main/java/com/example/translator/HealthServlet.java - Add readiness/dependency metadata.
- c:/Prjoects/web-based-language-translator/src/main/resources/config.properties - Expand configurable properties (origins, timeouts, limits, API base URL defaults).
- c:/Prjoects/web-based-language-translator/src/main/resources/logback.xml - Add structured logging fields and log level strategy.
- c:/Prjoects/web-based-language-translator/src/main/resources/webapp/api.js - Add configurable API base URL, richer error classification, retry hint handling.
- c:/Prjoects/web-based-language-translator/src/main/resources/webapp/translator.js - Add request deduplication, a11y improvements, and production UX states.
- c:/Prjoects/web-based-language-translator/src/main/resources/webapp/history.js - Add strict JSON import schema validation.
- c:/Prjoects/web-based-language-translator/src/main/resources/webapp/index.html - Add missing accessibility/semantic hooks for controls and feedback.
- c:/Prjoects/web-based-language-translator/Dockerfile - Enable production JVM settings and non-test-skipping release path.
- c:/Prjoects/web-based-language-translator/pom.xml - Add testing/security scan plugins and CI-friendly build profiles.
- c:/Prjoects/web-based-language-translator/README.md - Document production deployment topology and environment variables.

**Verification**
1. Run backend test suite and static checks on every PR; block merges on failure.
2. Run integration tests against /translate and /health with valid/invalid payloads, CORS preflights, and auth-required scenarios.
3. Run container image scan and dependency vulnerability scan in CI.
4. Run staging smoke tests: login, translation, fallback behavior, timeout handling, and history flows.
5. Run a small load test (low-traffic target profile) and verify p95 latency/error budget.
6. Confirm observability dashboards and alerts trigger correctly in staging before production cutover.

**Decisions**
- Included scope: production hardening for current Java stack, deployment plan, auth path, CI/CD, and feature roadmap.
- Excluded scope: full microservices split, enterprise SSO, and multi-region active-active architecture.
- Chosen assumptions from user answers: hosting preference is Vercel, expected traffic is low (<5k/day), auth requires user login, timeline is 1-2 weeks.
- Architecture recommendation: for fastest launch, host static frontend on Vercel and Java API on a Java-friendly container platform; connect via configured API URL and strict CORS.

**Further Considerations**
1. Auth provider choice recommendation: Clerk (fast UI integration) vs Auth0 (enterprise features) vs Firebase Auth (cost-effective for low traffic).
2. Data persistence choice recommendation: keep localStorage-only history for MVP speed vs add backend DB sync for multi-device user value.
3. API hosting choice recommendation: Render/Railway/Fly.io for fastest Java deployment with Docker and health checks.
