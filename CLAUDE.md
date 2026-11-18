# PulseWatch

API monitoring platform: users register URLs ("monitors"), the app checks them on a schedule, records results, opens an incident after N consecutive failures, and alerts once per outage.

Spring Boot 4.1.x, Java 25 (target), Maven, PostgreSQL 17, Flyway. Root package `com.karaninder.pulseWatch`, split by feature: `monitor`, `common` now; `check`, `incident`, `alert`, `stats`, `auth` later.

## Commands
- Whole stack in Docker (what a stranger runs): `docker compose up --build`, then http://localhost:8088. Smoke test of a running stack: `scripts/smoke-test.sh`.
- Developing: `docker compose up -d postgres httpbin mailpit prometheus grafana` (supporting services only; plain `docker compose up -d` would also start the app container and clash with `./mvnw spring-boot:run` on port 8080). Tests need the Docker daemon running too, but they start their OWN throwaway Postgres through Testcontainers and never touch the dev database.
- Run app: `./mvnw spring-boot:run` (port 8080; Swagger UI at `/swagger-ui/index.html`; runs with the Spring profile `local` = `application-local.yaml`, set in the pom, which allows private monitor targets)
- Tests: `./mvnw test` (Docker must be running) and `cd frontend && npm test`. Run both before saying a feature is done.
- Inspect DB: `docker exec -it pulsewatch-postgres-1 psql -U pulsewatch`

## I am learning Java
- I am new to Java and Spring. I want to understand every line, not just have working code.
- Workflow (changed after Phase 3): build the project phase by phase WITHOUT stopping to teach. After each phase, append that phase's stops (files to read, a request trace, questions, an experiment, "break it on purpose" items) to `STUDY_GUIDE.md`, then continue to the next phase. I will study later and ask for quizzes then.
- Still explain a new concept in a few sentences before writing code for it.
- Teaching format when I do ask for a lesson: trace one request end to end, explain each concept with the real code, give hands-on exercises, then a quiz (about 5 to 6 questions).
- Phase 1 and 2 quiz answers are still owed by me (including: why is timeout < interval enforced in both API and DB?).
- Before writing code for a new concept, explain it in a few sentences.
- When I paste my code, review it: bugs first, then idiomatic Java, then style.
- No Lombok. Prefer records for DTOs, constructor injection, plain Java.
- Every new feature needs a test. Prefer unit tests (Mockito) and `@WebMvcTest`; use Testcontainers (real PostgreSQL) for anything involving SQL.
- I make git commits myself. Do not run `git commit` or `git push`.

## Conventions
- Layers per feature: controller (HTTP only) -> service (rules, `@Transactional`) -> repository -> Postgres. No logic in controllers.
- Entities have no public setters; change them through intention-revealing methods (`pause()`, `applyChanges(...)`).
- DTOs are records (`XxxRequest`, `XxxResponse`); never return entities from controllers.
- Schema changes only through new Flyway migrations (`V2__...`). Never edit a migration that has already run. Hibernate is `ddl-auto: validate`.
- Errors go through `common/GlobalExceptionHandler` and are returned as `ProblemDetail`.
- Rule across fields: `timeoutMs` must be < `intervalSeconds * 1000` (custom `@TimeoutShorterThanInterval` on `MonitorRequest`, plus DB CHECK in V3) so a monitor's checks never overlap.

## Gotchas
- Spring Boot 4 moved test classes: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`; mocks use `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`).
- springdoc must be the 3.x line for Boot 4 (currently 3.2.0).
- The local JDK is 26 while `java.version` is 25; that is fine, but do not use Java 26-only features.
- SSRF guard (Phase 8): `check/TargetGuard` resolves a monitor's host and refuses loopback, private, link-local (169.254.x, cloud metadata), CGNAT and IPv6 unique-local addresses, when the monitor is saved (400 with `errors.url`) AND before every check. `pulsewatch.check.allow-private-targets` defaults to FALSE; it is true only in `application-local.yaml` (the `local` profile of `spring-boot:run`) and in the local `compose.yaml` (so httpbin can be monitored). Never enable it in `deploy/compose.prod.yaml`. Known gap: DNS rebinding between the lookup and the connection (needs an egress firewall).
- Login/registration rate limit (Phase 8): `auth/AttemptLimiter`, sliding window in memory, per account (10 per 5 min), per IP (60) and registrations per IP (20); 429 + `Retry-After`. Per-instance counters. The client IP is `getRemoteAddr()`, correct behind Caddy only because `server.forward-headers-strategy=framework` is set in both compose files.
- The DB credentials in `application.yaml` are local-dev only.
- Checks use the JDK `HttpClient` (not Spring `RestClient`) because each monitor has its own timeout, which `HttpClient` sets per request. Redirects are NOT followed.
- `compose.yaml` also runs go-httpbin on host port 8081 (`/status/500`, `/delay/8`) as a local check target. Port 8080 is the app, 8081 is httpbin.
- The scheduler is `pulsewatch.check.enabled` (default true). `@SpringBootTest` tests must set it to false or they fire real checks against the dev DB.
- Scheduler claims due monitors (advances `next_check_at`) BEFORE running checks. This is single-instance safe only; multi-instance needs ShedLock (Phase 6).

- Retries: `RetryPolicy` (3 attempts, 0.5 s doubling backoff) is capped per monitor so worst-case attempts finish before the interval ends; a 10 s interval with a 5 s timeout gets no retries. `CheckResult.attemptCount` stores how many tries a check took.
- Incidents: opened when the last `failureThreshold` check results are all failures (derived from `check_results`, no counter column), resolved by the first success. The partial unique index `uq_incidents_one_open_per_monitor` is the dedup guarantee. A paused monitor keeps its open incident open. `check` and `incident` packages depend on each other (CheckRunner calls IncidentService; IncidentService reads check results).

- Alerts: `IncidentService` publishes `IncidentOpened`/`IncidentResolved`; `CheckRunner` publishes `SlowResponseDetected`. `alert/AlertListener` handles them `@Async` (`alertExecutor`) and, for incident events, `AFTER_COMMIT`. One down and one recovered alert per outage (dedup comes from the incident state machine). Latency alerts have a cooldown (`pulsewatch.alerts.cooldown`, 15 min), checked against `alert_events`. A failing channel is recorded as `FAILED` and never stops the others.
- Alerts go to the monitor owner's email, plus an optional global `webhook-url`. Local email goes to Mailpit (SMTP 1025, inbox http://localhost:8025); nothing real is ever sent. Mail health check is disabled so `/actuator/health` does not depend on Mailpit.
- Spring Boot 4 uses Jackson 3: `tools.jackson.databind.json.JsonMapper` (not `com.fasterxml.jackson.databind.ObjectMapper`).

- Stats (Phase 5): `GET /api/monitors/{id}/stats` (uptime 24h/7d/30d, latency percentiles 24h/7d, 24h error-rate buckets, open incident), `GET /api/monitors/{id}/error-rate?window=24h|7d|30d`, `GET /api/monitors/{id}/incidents?page&size` (size capped at 100). Aggregates are plain SQL through `JdbcClient` in `stats/StatsRepository`.
- Raw `check_results` are kept 7 days (`pulsewatch.stats.raw-retention`, minimum 7 days). The nightly job (03:00 UTC, `StatsMaintenanceJob`) upserts complete hours into `check_results_hourly`, then deletes raw rows older than the hour-aligned cutoff. Stats read hourly rows BEFORE `boundary = trunc_hour(now - retention)` and raw rows from it on, so nothing is counted twice. Latency percentiles exist only for 24h/7d because percentiles cannot be rebuilt from hourly rollups. If the job never runs, 30d numbers silently shrink to the last 7 days. Percentiles and averages count successful checks only.
- SQL uses `date_trunc('hour', x, 'UTC')` so hour boundaries do not depend on the session time zone (`StatsRepositoryTest` forces Asia/Kolkata to prove it). The `Instant` values are converted to `OffsetDateTime` before binding.
- Tests: `TestcontainersConfig` starts ONE shared `postgres:17` for the whole test run; DB tests import it with `@Import(TestcontainersConfig.class)` and must clean up after themselves (`@Transactional` or `deleteEverything()` in `@BeforeEach`). Testcontainers is 2.x: `org.testcontainers.postgresql.PostgreSQLContainer`.
- Spring Data `Page` is never returned directly; use `common/PageResponse`.

- Auth (Phase 6): `POST /api/auth/register` and `/login` return a JWT (HS256, 12 h, subject = user id). Everything else needs `Authorization: Bearer <token>`. Spring's OAuth2 resource server verifies tokens; `auth/JwtConfig` builds the encoder and decoder from `pulsewatch.security.jwt-secret` (the value in `application.yaml` is local-dev only; use env `PULSEWATCH_SECURITY_JWT_SECRET` in production, min 32 bytes). Passwords are BCrypt. Login errors are deliberately identical for unknown email and wrong password.
- Ownership: every monitor, stats and incident query is filtered by the caller's user id (`AuthenticatedUser.id(jwt)` in controllers, `findByIdAndUserId` etc. in services). Someone else's monitor is **404, never 403**. The scheduler is the one system-level reader and is intentionally unfiltered. Any NEW endpoint that touches a monitor must take the user id and have a cross-user test (see `SecurityIntegrationTest`).
- V7 created `users` and gave pre-existing monitors to a placeholder `legacy@pulsewatch.local` (hash `!`, cannot log in). Alerts go to the monitor owner's email.
- Controller slice tests (`@WebMvcTest`) must `@Import(SecurityConfig.class)`, `@MockitoBean JwtDecoder`, and send `.with(jwt().jwt(t -> t.subject("7")))`.
- Metrics (Micrometer, `/actuator/prometheus`): `pulsewatch_checks_total{result}`, `pulsewatch_check_duration_seconds` (histogram), `pulsewatch_check_retries_total`, `pulsewatch_incidents_open` (gauge), `pulsewatch_alerts_total{type,channel,status}`. NEVER tag by monitor id or URL (unbounded time series). `/actuator/prometheus` is public for local dev: restrict at the network level before any public deployment.
- Prometheus http://localhost:9090 and Grafana http://localhost:3001 (3000 was taken on this machine; anonymous admin, LOCAL ONLY) run from `compose.yaml`; config and the provisioned "PulseWatch" dashboard are in `observability/`. Prometheus scrapes the app on the host at `host.docker.internal:8080` and `:8082`.
- ShedLock: every `@Scheduled` method must also have `@SchedulerLock` (`EveryScheduledJobIsLockedTest` enforces it). `checkScheduler.tick` holds a row in the `shedlock` table around claim+dispatch, which fixes the single-instance claim limit. Lock times use the DB clock (`usingDbTime`). Two local instances: run the second with `--server.port=8082`; `SchedulerLockTest` proves the skip.
- Frontend (Phase 7), in `frontend/`: Vite + React 19 + TypeScript (strict) + TanStack Query + React Router, plain CSS with design tokens, hand-drawn SVG charts (no chart library). Commands: `cd frontend && npm install`, `npm run dev` (http://localhost:5173, proxies `/api` to the app on :8080 so there is no CORS), `npm test` (Vitest + Testing Library, jsdom), `npm run build`. Needs Node 20+ (developed on Node 26).
- The UI uses two endpoints added for it: `GET /api/monitors/overview` (list with status, last latency, 24h uptime, one SQL query) and `GET /api/monitors/{id}/latency?window=24h|7d` (p50/p95 per bucket; 30d is a 400 because raw rows are kept 7 days).
- Design rules (keep new UI consistent): every colour comes from `frontend/src/styles/tokens.css` (warm greys + ONE blue accent; green/amber/red only for status and always next to a word); no gradients, shadows only on popovers, hairline borders, Inter, 14px base; light and dark follow the OS with a manual toggle; charts follow the dataviz rules (2px lines, hairline grid, text in text colours, legend + direct labels, tooltip + keyboard + table view, "no monitor id" style cardinality does not apply). Sort order of the list is trouble first.
- The JWT is kept in localStorage (XSS trade-off documented in `AuthContext.tsx`). `setAuthToken` must run synchronously before any request: setting it in an effect caused a 401 sign-out on page reload.
- Demo data: user `demo@pulsewatch.dev` / `demo-password-1` with 6 monitors and 7 days of SYNTHETIC history was inserted into the dev database for visual testing. Remove with `delete from users where email = 'demo@pulsewatch.dev';` (cascades).
- Packaging (Phase 8): `Dockerfile` (multi-stage: JDK builds, `jarmode=tools extract` layers, Alpine JRE runs as non-root, healthcheck on /actuator/health), `frontend/Dockerfile` (node builds, Caddy serves + proxies `/api` only), `frontend/Caddyfile` (CSP, security headers, SPA fallback, HTTPS when `SITE_ADDRESS` is a domain). `compose.yaml` = the full local stack (web on :8088); `deploy/compose.prod.yaml` = single-server production (only 80/443 published, secrets required via `:?`, SSRF guard on, real SMTP). The theme script lives in `frontend/public/theme.js` because a strict CSP forbids inline scripts. `/actuator/*` and Swagger are NOT reachable through Caddy by design.
- CI (`.github/workflows/ci.yml`): backend `./mvnw verify`, frontend typecheck+test+build, then builds the stack with compose, runs `scripts/smoke-test.sh`, and on pushes to `main` publishes images to GHCR. Not yet run on GitHub (no repository yet): expect to fix small things on the first run. Deploy guide: `docs/DEPLOY.md` (not yet executed on a real server). README has screenshots from the real stack (`docs/screenshots/`).
- Not done yet: token revocation/logout list, shared (multi-instance) rate-limit counters, a measured load test, an animated demo GIF, a real public deployment.

## Roadmap
0 Setup (done) · 1 Monitor CRUD API (done) · 2 Check engine (done: `@Scheduled`, JDK `HttpClient`, virtual threads; awaiting quiz) · 3 Retries, incidents, dedup (done: hand-written retry loop, `incidents` table + partial unique index, `IncidentService`; awaiting quiz) · 4 Alerts (done: events, `Notifier` email + webhook, `alert_events` audit, latency cooldown; awaiting quiz) · 5 Stats API (done: SQL percentiles, hourly rollup + cleanup, incident history, Testcontainers; awaiting quiz) · 6 Security + observability + ShedLock (done: JWT auth, per-user monitors, Micrometer + Prometheus + Grafana, ShedLock; awaiting quiz) · 7 React dashboard (done: login, monitor list, add/edit drawer, detail page with latency chart + status strip + incidents, auto-refresh 30 s, light/dark, mobile; awaiting quiz) · 8 Ship it (done: SSRF guard, login rate limit, Dockerfiles, full-stack compose, prod compose + deploy guide, CI workflow, README; awaiting quiz). ALL PHASES BUILT.
The full plan lives outside the repo: `~/Downloads/PulseWatch — API Monitoring Platform Stack Review & Build Plan.md`.
