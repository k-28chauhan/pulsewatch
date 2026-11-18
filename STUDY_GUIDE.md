# PulseWatch study guide: the order to read the code

All 8 phases are built: skeleton, Monitor CRUD API, check engine, retries and incidents, alerts, stats API, security and observability, the React dashboard, and shipping (hardening, Docker, CI, docs). About 170 source files. Each phase appends its own stops at the bottom of the route (Stops 8 and 9 are Phase 3, 10 and 11 are Phase 4, 12 and 13 are Phase 5, 14 to 16 are Phase 6, 17 to 20 are Phase 7, the React dashboard, 21 to 24 are Phase 8, shipping it). Note: Phase 6 changed code you read in earlier stops (every monitor method now takes the caller's user id), so re-read Stop 4 after Stop 14.

**How to use it.** Do the stops in order. At each stop: read the files, answer the question in your own words *before* looking at the answer hints, then run the small experiment. Don't skim: the comments in the code explain the *why*.

**Why this order?** Data first (what is stored), then the request path (how it gets there), then the background engine (what happens without a request). Each stop only depends on stops before it.

---

## Stop 0: Orientation (20 min)

| File | Look for |
|---|---|
| [pom.xml](pom.xml) | The parent (`spring-boot-starter-parent`) pins versions. Each `starter` is a bundle of libraries. Which starter gives you REST? JPA? Flyway? |
| [src/main/resources/application.yaml](src/main/resources/application.yaml) | Datasource URL, `ddl-auto: validate`, `open-in-view: false` |
| [compose.yaml](compose.yaml) | Two containers: Postgres and httpbin |
| [PulseWatchApplication.java](src/main/java/com/karaninder/pulseWatch/PulseWatchApplication.java) | `@SpringBootApplication` = three annotations in one. It scans only *its own package and below* |

**Question:** What breaks if you move a class out of `com.karaninder.pulseWatch`?
**Experiment:** `docker compose up -d`, then `./mvnw spring-boot:run`, and watch the startup log top to bottom (Flyway, Hibernate, Tomcat, "Started").

## Stop 1: The database (30 min)

Read the SQL *before* the Java, because the Java mirrors it.

1. [V1__create_monitors.sql](src/main/resources/db/migration/V1__create_monitors.sql): columns, defaults, `CHECK`s, the partial index `idx_monitors_due`
2. [V2__create_check_results.sql](src/main/resources/db/migration/V2__create_check_results.sql): foreign key with `ON DELETE CASCADE`, the `(monitor_id, checked_at DESC)` index
3. [V3__timeout_shorter_than_interval.sql](src/main/resources/db/migration/V3__timeout_shorter_than_interval.sql): a rule added later *as a new file*

**Question:** Why is V3 a new file and not an edit of V1?
**Experiment:** `docker exec -it pulsewatch-postgres-1 psql -U pulsewatch`, then `\d monitors`, `\d check_results`, `select * from flyway_schema_history;`

## Stop 2: The domain model (45 min)

1. [MonitorMethod.java](src/main/java/com/karaninder/pulseWatch/monitor/MonitorMethod.java): the simplest file, an enum
2. [Monitor.java](src/main/java/com/karaninder/pulseWatch/monitor/Monitor.java): the entity. Read in this order:
   - the annotations on fields ([`@Enumerated` line 36](src/main/java/com/karaninder/pulseWatch/monitor/Monitor.java:36)), and match each field to a column in V1
   - the `protected` no-arg constructor (why?) and the public one
   - `applyChanges`, `pause`, `resume`, `scheduleNextCheck`: the *only* ways to change a Monitor (there are no setters)
   - the lifecycle callbacks [`@PrePersist` line 111](src/main/java/com/karaninder/pulseWatch/monitor/Monitor.java:111) and [`@PreUpdate` line 118](src/main/java/com/karaninder/pulseWatch/monitor/Monitor.java:118)
3. [MonitorRepository.java](src/main/java/com/karaninder/pulseWatch/monitor/MonitorRepository.java): an interface with no code. Read how the *method name* `findByEnabledTrueAndNextCheckAtLessThanEqual` becomes SQL

**Questions:** Why `Integer latencyThresholdMs` but `int timeoutMs`? Why `EnumType.STRING`?
**Experiment:** Rename `@Column(name = "timeout_ms")` to `"timeout_millis"`, start the app, read the Hibernate `validate` error, then undo.

## Stop 3: Data in and out, plus validation (45 min)

1. [MonitorRequest.java](src/main/java/com/karaninder/pulseWatch/monitor/MonitorRequest.java): a **record**. Study the *compact constructor* that fills defaults, and the validation annotations
2. [MonitorResponse.java](src/main/java/com/karaninder/pulseWatch/monitor/MonitorResponse.java): the output record and its static `from()` factory
3. [TimeoutShorterThanInterval.java](src/main/java/com/karaninder/pulseWatch/monitor/TimeoutShorterThanInterval.java), then [TimeoutShorterThanIntervalValidator.java](src/main/java/com/karaninder/pulseWatch/monitor/TimeoutShorterThanIntervalValidator.java): a custom annotation and its logic

**Questions:** In what order do these run: JSON parsing, compact constructor, validation? Why does a missing `intervalSeconds` pass `@Min(10)`?

## Stop 4: One request, end to end (60 min)  <-- the most important stop

Trace `POST /api/monitors` through the code, opening each file as the request reaches it:

```
HTTP JSON
  → MonitorController.create        [@Valid triggers validation]        (HTTP only)
  → MonitorService.create           [@Transactional]                    (rules)
  → MonitorRepository.save          [Spring-generated implementation]   (SQL)
  → Postgres
  ← MonitorResponse.from(...)  ← 201 + Location header
```

1. [MonitorController.java](src/main/java/com/karaninder/pulseWatch/monitor/MonitorController.java): [`@Valid` line 33](src/main/java/com/karaninder/pulseWatch/monitor/MonitorController.java:33), `ResponseEntity.created`
2. [MonitorService.java](src/main/java/com/karaninder/pulseWatch/monitor/MonitorService.java): constructor injection; [`@Transactional` line 13](src/main/java/com/karaninder/pulseWatch/monitor/MonitorService.java:13); how `update()` saves *without* calling `save()` (dirty checking)
3. [MonitorNotFoundException.java](src/main/java/com/karaninder/pulseWatch/monitor/MonitorNotFoundException.java) and [GlobalExceptionHandler.java](src/main/java/com/karaninder/pulseWatch/common/GlobalExceptionHandler.java): where each failure becomes JSON (`@RestControllerAdvice`, `ProblemDetail`)

**Experiment:** In Swagger (`/swagger-ui/index.html`), send a valid create, then break one rule at a time (empty name, `ftp://`, interval 5, `method: "GTE"`, `GET /api/monitors/abc`). For each, name the exception class and the handler method.

## Stop 5: Phase 1 tests (45 min)

Read tests as documentation: each test name is a sentence about behaviour.

1. [MonitorServiceTest.java](src/test/java/com/karaninder/pulseWatch/monitor/MonitorServiceTest.java): pure Mockito, no Spring
2. [MonitorRequestValidationTest.java](src/test/java/com/karaninder/pulseWatch/monitor/MonitorRequestValidationTest.java): validation without Spring
3. [MonitorControllerTest.java](src/test/java/com/karaninder/pulseWatch/monitor/MonitorControllerTest.java): `@WebMvcTest` + `@MockitoBean`; why the service is mocked

**Experiment:** Change one validation rule (e.g. `@Min(10)` to `@Min(30)`), run `./mvnw test`, see which tests fail, then undo.

## Stop 6: The check engine, bottom-up (90 min)

Read from the smallest piece upward. Each file only uses files above it.

| # | File | One-line job |
|---|---|---|
| 1 | [CheckTarget.java](src/main/java/com/karaninder/pulseWatch/check/CheckTarget.java) | immutable snapshot of what a check needs |
| 2 | [CheckOutcome.java](src/main/java/com/karaninder/pulseWatch/check/CheckOutcome.java) | what happened (never throws) |
| 3 | [HttpChecker.java](src/main/java/com/karaninder/pulseWatch/check/HttpChecker.java) | call ONE URL. Study the [`catch` order, line 45](src/main/java/com/karaninder/pulseWatch/check/HttpChecker.java:45) |
| 4 | [CheckResult.java](src/main/java/com/karaninder/pulseWatch/check/CheckResult.java) + [CheckResultRepository.java](src/main/java/com/karaninder/pulseWatch/check/CheckResultRepository.java) | the row for check_results |
| 5 | [CheckRunner.java](src/main/java/com/karaninder/pulseWatch/check/CheckRunner.java) | one check = call + save. Why *not* `@Transactional`? |
| 6 | [CheckScheduleService.java](src/main/java/com/karaninder/pulseWatch/check/CheckScheduleService.java) | "who is due?" and **claim** them (line 27 is the transaction) |
| 7 | [CheckScheduler.java](src/main/java/com/karaninder/pulseWatch/check/CheckScheduler.java) | the heartbeat: [`@Scheduled` line 37](src/main/java/com/karaninder/pulseWatch/check/CheckScheduler.java:37), hands work to virtual threads |
| 8 | [CheckConfig.java](src/main/java/com/karaninder/pulseWatch/check/CheckConfig.java) | `@EnableScheduling`, the `HttpClient` and executor `@Bean`s |

Then trace one tick:

```
@Scheduled tick (every 10 s)
  → CheckScheduleService.claimDueMonitors   [SELECT due; move next_check_at forward; commit]
  → for each target: executor.execute(...)  [one virtual thread per check]
       → CheckRunner.run → HttpChecker.check (network wait) → repository.save(CheckResult)
```

**Questions:** Why claim *before* checking? Why does the tick not run the checks itself? What does `@ConditionalOnProperty` on the scheduler do?
**Experiment:** Create three monitors in Swagger (`http://localhost:8081/status/200`, `/status/500`, `/delay/8` with `timeoutMs: 2000`, all `intervalSeconds: 10`). Watch the log, then run:
```bash
docker exec pulsewatch-postgres-1 psql -U pulsewatch -c "select monitor_id, checked_at, status_code, latency_ms, success, error from check_results order by id desc limit 15"
```

## Stop 7: Phase 2 tests (60 min)

1. [HttpCheckerTest.java](src/test/java/com/karaninder/pulseWatch/check/HttpCheckerTest.java): a real tiny HTTP server inside the test (port 0 = any free port)
2. [CheckScheduleServiceTest.java](src/test/java/com/karaninder/pulseWatch/check/CheckScheduleServiceTest.java): why `now` is a method parameter
3. [CheckSchedulerTest.java](src/test/java/com/karaninder/pulseWatch/check/CheckSchedulerTest.java): `Runnable::run` as a same-thread `Executor`
4. [CheckRunnerTest.java](src/test/java/com/karaninder/pulseWatch/check/CheckRunnerTest.java): `ArgumentCaptor`
5. [PulseWatchApplicationTests.java](src/test/java/com/karaninder/pulseWatch/PulseWatchApplicationTests.java): the full context, with the scheduler switched off. Why?

## Stop 8: Retries and incidents (Phase 3, 90 min)

Read the SQL first, then the retry code, then the incident code.

**8a. The table and its guard**
- [V4__create_incidents.sql](src/main/resources/db/migration/V4__create_incidents.sql): find the `CREATE UNIQUE INDEX ... WHERE resolved_at IS NULL`. It is the whole deduplication rule: at most one *open* row per monitor, while resolved rows are free to repeat.

**8b. Retries (hand-written)**

| # | File | One-line job |
|---|---|---|
| 1 | [RetryPolicy.java](src/main/java/com/karaninder/pulseWatch/check/RetryPolicy.java) | pure rules: backoff doubles (`1L << n`), and `attemptsThatFit` caps attempts so retries finish before the next check is due |
| 2 | [Sleeper.java](src/main/java/com/karaninder/pulseWatch/check/Sleeper.java) | "wait" as a one-method interface so tests do not really sleep |
| 3 | [RetriedOutcome.java](src/main/java/com/karaninder/pulseWatch/check/RetriedOutcome.java) | final outcome + attempt count |
| 4 | [RetryingChecker.java](src/main/java/com/karaninder/pulseWatch/check/RetryingChecker.java) | the `while` loop: try, sleep, try again. Note how `InterruptedException` is handled |
| 5 | [CheckConfig.java](src/main/java/com/karaninder/pulseWatch/check/CheckConfig.java) | the `RetryPolicy` and `Sleeper` beans (`Thread::sleep` is a method reference) |
| 6 | [CheckTarget.java](src/main/java/com/karaninder/pulseWatch/check/CheckTarget.java) | now also carries `intervalSeconds` and `failureThreshold` |

**8c. Incidents**

| # | File | One-line job |
|---|---|---|
| 1 | [Incident.java](src/main/java/com/karaninder/pulseWatch/incident/Incident.java) | entity with a static factory `open(...)` and a `resolve(...)` that works only once |
| 2 | [IncidentRepository.java](src/main/java/com/karaninder/pulseWatch/incident/IncidentRepository.java) | `findByMonitorIdAndResolvedAtIsNull`: the method name becomes `WHERE resolved_at IS NULL` |
| 3 | [CheckResultRepository.java](src/main/java/com/karaninder/pulseWatch/check/CheckResultRepository.java) | `Pageable` used as `LIMIT n` to fetch the newest N results |
| 4 | [IncidentTransition.java](src/main/java/com/karaninder/pulseWatch/incident/IncidentTransition.java) | enum: `NONE`, `OPENED`, `RESOLVED` |
| 5 | [IncidentService.java](src/main/java/com/karaninder/pulseWatch/incident/IncidentService.java) | the state machine. Read `evaluate` first, then the two private methods |
| 6 | [CheckRunner.java](src/main/java/com/karaninder/pulseWatch/check/CheckRunner.java) | now: retrying check, save result, `incidentService.evaluate`, and a `catch` for the unique-index violation |

Trace one failing monitor (`failureThreshold` 2, interval 10 s):

```
tick → claim monitor → virtual thread → CheckRunner.run
  → RetryingChecker: attempt 1 fails, sleep 0.5 s, attempt 2 fails, sleep 1 s, attempt 3 fails  (attempts = 3)
  → save CheckResult (success = false, attempt_count = 3)         [transaction 1, committed]
  → IncidentService.evaluate                                      [transaction 2]
       no open incident, last 2 results: only 1 failure so far → NONE
10 s later, same path, now the last 2 results are both failures → INSERT incident → OPENED
10 s later, same path, an open incident exists → NONE   (this is the deduplication)
URL fixed → success → open incident found → resolve → RESOLVED (dirty checking writes it; no save call)
```

**Questions:**
1. Why is the consecutive-failure count read from `check_results` instead of stored as a counter on `monitors`?
2. Why does `attemptsThatFit` exist? What happens with `intervalSeconds: 10` and `timeoutMs: 5000`?
3. Why does `CheckRunner` save the result *before* calling `IncidentService`, in two separate transactions?
4. Two layers prevent duplicate incidents. What are they, and which one is the real guarantee?
5. Why is `IncidentTransition` returned instead of `void`?

**Experiment:** Create a monitor pointing at `http://localhost:8081/status/500` with `intervalSeconds: 10`, `timeoutMs: 2000`, `failureThreshold: 2`. Wait one minute, then:
```bash
docker exec pulsewatch-postgres-1 psql -U pulsewatch -c "select id, monitor_id, started_at, resolved_at from incidents" -c "select success, attempt_count, checked_at from check_results where monitor_id = (select max(id) from monitors) order by id"
```
Expect several failed checks (each with `attempt_count` 3) and exactly one open incident. Now `PUT` the monitor to `/status/200` and query again: `resolved_at` fills in.

## Stop 9: Phase 3 tests (45 min)

1. [RetryPolicyTest.java](src/test/java/com/karaninder/pulseWatch/check/RetryPolicyTest.java): do the arithmetic of each `attemptsThatFit` case by hand
2. [RetryingCheckerTest.java](src/test/java/com/karaninder/pulseWatch/check/RetryingCheckerTest.java): a fake `Sleeper` that records waits (`sleeps::add`), and the interrupt test
3. [IncidentTest.java](src/test/java/com/karaninder/pulseWatch/incident/IncidentTest.java): entity rules
4. [IncidentServiceTest.java](src/test/java/com/karaninder/pulseWatch/incident/IncidentServiceTest.java): one test per state change; the section comments mark closed→open, open stays open, open→resolved
5. [CheckRunnerTest.java](src/test/java/com/karaninder/pulseWatch/check/CheckRunnerTest.java): the duplicate-incident exception is swallowed

**Experiment:** In `IncidentService.evaluate`, delete the `if (open.isPresent()) return NONE;` check and run the tests. Which test fails, and what would happen in production? (Hint: the unique index would still stop it. That is why it is there.)

## Stop 10: Alerts (Phase 4, 120 min)

The big idea: `IncidentService` does not call any alert code. It *publishes an event*, and the `alert` package *listens*. The two packages stay loosely coupled, and the email can never slow down or break a check.

**10a. Infrastructure first**
- [compose.yaml](compose.yaml): the new `mailpit` service. Open http://localhost:8025 while the app runs: it is a fake inbox that catches every email.
- [application.yaml](src/main/resources/application.yaml): `spring.mail.*` and the `pulsewatch.alerts` block. Why is `management.health.mail.enabled` false?
- [V5__create_alert_events.sql](src/main/resources/db/migration/V5__create_alert_events.sql): the audit table, its `CHECK`s, and the cooldown index.

**10b. The events (the messages)**

| File | Meaning |
|---|---|
| [IncidentOpened.java](src/main/java/com/karaninder/pulseWatch/incident/IncidentOpened.java) | a monitor went down |
| [IncidentResolved.java](src/main/java/com/karaninder/pulseWatch/incident/IncidentResolved.java) | a monitor recovered (carries both timestamps) |
| [SlowResponseDetected.java](src/main/java/com/karaninder/pulseWatch/check/SlowResponseDetected.java) | a successful check was slower than the threshold |

They are records named in the *past tense*: facts, not commands. Then see where they are published: [`IncidentService`](src/main/java/com/karaninder/pulseWatch/incident/IncidentService.java) (`events.publishEvent(...)` in `resolve` and `openIfThresholdReached`) and [`CheckRunner.announceIfSlow`](src/main/java/com/karaninder/pulseWatch/check/CheckRunner.java).

**10c. The alert package, from the outside in**

| # | File | One-line job |
|---|---|---|
| 1 | [AlertListener.java](src/main/java/com/karaninder/pulseWatch/alert/AlertListener.java) | receives events. Study the three annotations: `@Async`, `@TransactionalEventListener(AFTER_COMMIT)`, `@EventListener` |
| 2 | [AlertService.java](src/main/java/com/karaninder/pulseWatch/alert/AlertService.java) | builds the message, enforces the cooldown, loops over notifiers, writes the audit row |
| 3 | [Notifier.java](src/main/java/com/karaninder/pulseWatch/alert/Notifier.java) | the interface: `channel()`, `isEnabled()`, `send()` |
| 4 | [EmailNotifier.java](src/main/java/com/karaninder/pulseWatch/alert/EmailNotifier.java) | `JavaMailSender` + `SimpleMailMessage` |
| 5 | [WebhookNotifier.java](src/main/java/com/karaninder/pulseWatch/alert/WebhookNotifier.java) | JDK `HttpClient` POST, JSON built with Jackson |
| 6 | [AlertMessage.java](src/main/java/com/karaninder/pulseWatch/alert/AlertMessage.java), [AlertType](src/main/java/com/karaninder/pulseWatch/alert/AlertType.java), [AlertChannel](src/main/java/com/karaninder/pulseWatch/alert/AlertChannel.java), [AlertStatus](src/main/java/com/karaninder/pulseWatch/alert/AlertStatus.java) | the vocabulary |
| 7 | [AlertEvent.java](src/main/java/com/karaninder/pulseWatch/alert/AlertEvent.java), [AlertEventRepository.java](src/main/java/com/karaninder/pulseWatch/alert/AlertEventRepository.java) | the audit row; one long derived-query name for the cooldown |
| 8 | [AlertProperties.java](src/main/java/com/karaninder/pulseWatch/alert/AlertProperties.java) | YAML bound into a typed record (`@ConfigurationProperties`, `@DefaultValue`) |
| 9 | [AlertConfig.java](src/main/java/com/karaninder/pulseWatch/alert/AlertConfig.java) | `@EnableAsync`, the `Clock` bean, the `alertExecutor` |

Trace one outage:

```
(check thread, inside IncidentService's transaction)
  IncidentService.openIfThresholdReached → INSERT incident → events.publishEvent(IncidentOpened)
  transaction COMMITS
(after commit, handed to a new virtual thread by @Async)
  AlertListener.incidentOpened → AlertService.onIncidentOpened
     → MonitorRepository.findById (to get name and URL for the text)
     → for each Notifier in List<Notifier>: if enabled → send → save AlertEvent(SENT or FAILED)
Later: first success → IncidentResolved → same path → "RECOVERED ... Outage lasted: 1m 2s"
```

Why *once per outage*? `IncidentOpened` is published only when the state really changes (closed to open). While the incident stays open, `evaluate` returns `NONE` and publishes nothing. So the deduplication from Phase 3 *is* the alert deduplication.

Latency alerts are different: a slow monitor is slow on every check, so they use the **cooldown**. `AlertService.onSlowResponse` asks the audit table "was a SENT latency alert sent for this monitor in the last 15 minutes?" and stays silent if yes.

**Questions:**
1. Why `AFTER_COMMIT`? What would a user receive if the email went out *before* the incident transaction rolled back?
2. Why is `AlertService` not `@Transactional`?
3. `AlertService` receives a `List<Notifier>`. Who fills the list? What must you do to add a Slack channel?
4. Why does one failing notifier not stop the others, and where can you see the failure afterwards?
5. Why is the cooldown applied to latency alerts but not to down and recovered alerts?
6. What is injected instead of calling `Instant.now()` in `AlertService`, and why?
7. `SlowResponseDetected` uses a plain `@EventListener`, but the incident events use `@TransactionalEventListener`. Why?

**Experiments:**
1. Run the app (`./mvnw spring-boot:run`), create a monitor on `http://localhost:8081/status/500` with `intervalSeconds: 10`, `timeoutMs: 2000`, `failureThreshold: 2`. After about 30 s, open http://localhost:8025: one `DOWN` email. Then `PUT` it to `/status/200`: one `RECOVERED` email with the outage length.
2. Create a second monitor on `http://localhost:8081/delay/1` with `timeoutMs: 3000` and `latencyThresholdMs: 500`. Wait two minutes: many slow checks, one `SLOW` email.
3. Look at the audit trail:
```bash
docker exec pulsewatch-postgres-1 psql -U pulsewatch -c "select monitor_id, type, channel, status, error, created_at from alert_events order by id"
```
4. Stop Mailpit (`docker compose stop mailpit`), cause an incident, and read the `FAILED` row and its error. Then `docker compose start mailpit`.
5. Webhook: in `application.yaml` set `webhook-url: http://localhost:8081/post` (the local go-httpbin accepts POSTs there). Restart, cause an incident, and see two `alert_events` rows (EMAIL and WEBHOOK) for the one alert. Undo the change afterwards.

## Stop 11: Phase 4 tests (60 min)

1. [AlertServiceTest.java](src/test/java/com/karaninder/pulseWatch/alert/AlertServiceTest.java): a hand-written **fake** (`FakeNotifier`) instead of a Mockito mock, and `Clock.fixed(...)` to freeze time. When is a fake better than a mock?
2. [EmailNotifierTest.java](src/test/java/com/karaninder/pulseWatch/alert/EmailNotifierTest.java): capturing the `SimpleMailMessage`
3. [WebhookNotifierTest.java](src/test/java/com/karaninder/pulseWatch/alert/WebhookNotifierTest.java): a real in-test HTTP server again; the exact JSON string asserted
4. [AlertListenerTest.java](src/test/java/com/karaninder/pulseWatch/alert/AlertListenerTest.java): a `@SpringBootTest` that proves the *wiring*: delivered after commit, dropped on rollback, dropped with no transaction, handled on another thread. No unit test with mocks can prove any of that.
5. [IncidentServiceTest.java](src/test/java/com/karaninder/pulseWatch/incident/IncidentServiceTest.java) and [CheckRunnerTest.java](src/test/java/com/karaninder/pulseWatch/check/CheckRunnerTest.java): the new `verify(events).publishEvent(...)` and `verifyNoInteractions(events)` lines. Find the test that proves "no second alert for the same outage".

**Experiment:** In `AlertListener`, change `AFTER_COMMIT` to `BEFORE_COMMIT` and run `AlertListenerTest`. Which test fails? Then remove `@EnableAsync` from `AlertConfig`: which one fails now?

## Stop 12: The stats API (Phase 5, 150 min)

The big idea: a detail page needs *numbers computed over thousands of rows*. The database is the right place to compute them (SQL), and a nightly job keeps the table from growing forever.

**12a. Data first**
- [V6__stats_rollup_and_incident_history_index.sql](src/main/resources/db/migration/V6__stats_rollup_and_incident_history_index.sql): `check_results_hourly` (one row per monitor per UTC hour) and the incident-history index. Why are percentiles *not* stored here?

**12b. The SQL (the heart of the phase)**
- [StatsRepository.java](src/main/java/com/karaninder/pulseWatch/stats/StatsRepository.java), in this order:
  1. `HOURLY_UNION`: a CTE that gives the same shape from two sources, raw rows grouped on the fly and stored rollups. Read the two `WHERE` clauses and the `boundary` parameter until you can explain why nothing is counted twice.
  2. `counts`: sums over the CTE (uptime numbers).
  3. `errorRateBuckets`: `to_timestamp(floor(extract(epoch ...) / :bucketSeconds) * :bucketSeconds)` puts hours into buckets of any size.
  4. `latency`: `percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms)`. Why only successful checks? Why raw rows only?
  5. `rollUp`: `INSERT ... SELECT ... ON CONFLICT (monitor_id, hour_start) DO UPDATE` (an *upsert*) and why it is safe to run twice.
  6. `deleteRawBefore`.
- Why `date_trunc('hour', checked_at, 'UTC')` with the third argument? (`StatsRepositoryTest` runs with a session time zone of Asia/Kolkata to prove it.)

**12c. The layers above it**

| # | File | One-line job |
|---|---|---|
| 1 | [StatsWindow.java](src/main/java/com/karaninder/pulseWatch/stats/StatsWindow.java) | enum with data: label, length and chart bucket size (24h to 1 h, 7d to 6 h, 30d to 1 d) |
| 2 | [WindowCounts](src/main/java/com/karaninder/pulseWatch/stats/WindowCounts.java), [UptimeStats](src/main/java/com/karaninder/pulseWatch/stats/UptimeStats.java), [LatencyStats](src/main/java/com/karaninder/pulseWatch/stats/LatencyStats.java), [ErrorRateBucket](src/main/java/com/karaninder/pulseWatch/stats/ErrorRateBucket.java), [StatsResponse](src/main/java/com/karaninder/pulseWatch/stats/StatsResponse.java) | the records; note which fields are nullable and why |
| 3 | [StatsProperties.java](src/main/java/com/karaninder/pulseWatch/stats/StatsProperties.java) | `raw-retention` with validation in the compact constructor |
| 4 | [StatsService.java](src/main/java/com/karaninder/pulseWatch/stats/StatsService.java) | `@Transactional(readOnly = true)`; computes the `boundary`; `toUptime` does the percentage maths and "no data" |
| 5 | [StatsController.java](src/main/java/com/karaninder/pulseWatch/stats/StatsController.java) | two GET endpoints |
| 6 | [IncidentQueryService](src/main/java/com/karaninder/pulseWatch/incident/IncidentQueryService.java), [IncidentController](src/main/java/com/karaninder/pulseWatch/incident/IncidentController.java), [IncidentResponse](src/main/java/com/karaninder/pulseWatch/incident/IncidentResponse.java), [PageResponse](src/main/java/com/karaninder/pulseWatch/common/PageResponse.java) | pagination: `PageRequest`, `Sort`, clamping, our own page JSON |
| 7 | [InvalidStatsWindowException](src/main/java/com/karaninder/pulseWatch/stats/InvalidStatsWindowException.java) + [GlobalExceptionHandler](src/main/java/com/karaninder/pulseWatch/common/GlobalExceptionHandler.java) | a specific exception becomes a 400 |

**12d. The nightly job**
- [StatsMaintenanceJob.java](src/main/java/com/karaninder/pulseWatch/stats/StatsMaintenanceJob.java): `@Scheduled(cron = ...)`. Learn to read `0 0 3 * * *`.
- [StatsMaintenanceService.java](src/main/java/com/karaninder/pulseWatch/stats/StatsMaintenanceService.java): *rollup first, delete second, one transaction*. Why that order? Why is the cutoff cut at a whole hour?

Trace the detail page:

```
GET /api/monitors/5/stats
  → StatsController.stats → StatsService.stats  [read-only transaction]
       boundary = trunc_hour(now - 7 days)
       3 x counts (24h, 7d, 30d), 2 x latency (24h, 7d), 1 x buckets (24h), 1 x open incident
       each SQL: rollups BEFORE the boundary  +  raw rows FROM the boundary on
  ← one JSON document
03:00 UTC every night
  → rollUp (all complete hours still in raw)  → deleteRawBefore(cutoff)
```

**Questions:**
1. Why does 30-day uptime stay exact after raw rows are deleted, while 30-day percentiles are not offered?
2. What goes wrong if the stats `boundary` and the maintenance `cutoff` are computed differently?
3. Why is "no checks in the window" returned as `null` and not `100`?
4. Why are failed checks excluded from latency percentiles and averages?
5. Why does the incident endpoint clamp `size` to 100 instead of returning an error?
6. Why does `ON CONFLICT DO UPDATE` make the rollup safe to run twice, and what would a plain `INSERT` do?
7. Why does `PageResponse` exist when Spring already has `Page`?

**Experiments:**
1. Start the app and open `http://localhost:8080/api/monitors/<id>/stats` (use an id from `GET /api/monitors`). Compare `uptime24h.totalChecks` with `select count(*) from check_results where monitor_id = <id> and checked_at > now() - interval '24 hours'`.
2. Run the percentile SQL by hand: `select percentile_cont(0.5) within group (order by latency_ms) from check_results where success;` and compare with `latency7d.p50`.
3. Try `error-rate?window=1y` (a 400 ProblemDetail), an unknown id (404), and `incidents?size=100000` (clamped to 100).
4. See the rollup work (careful: this deletes raw rows older than 7 days from your dev database, which is exactly its job): in `psql`, `select * from check_results_hourly;` should be empty today. After the app has run past 03:00 UTC with data older than a few hours, it fills up.

## Stop 13: Phase 5 tests (90 min)

This phase introduces **Testcontainers**: tests that start a real PostgreSQL 17 in Docker.

1. [TestcontainersConfig.java](src/test/java/com/karaninder/pulseWatch/TestcontainersConfig.java): `@ServiceConnection` replaces the datasource with the container; a *static* container is shared by every test class. Flyway runs all migrations on it, so these tests prove the migrations work from scratch.
2. [StatsRepositoryTest.java](src/test/java/com/karaninder/pulseWatch/stats/StatsRepositoryTest.java): every SQL query, with hand-computed expectations (find the test where p95 is 480 and work out why). Note `hikari.connection-init-sql` setting the session to Asia/Kolkata.
3. [StatsTestData.java](src/test/java/com/karaninder/pulseWatch/stats/StatsTestData.java): a helper that inserts rows with exact timestamps
4. [IncidentRepositoryTest.java](src/test/java/com/karaninder/pulseWatch/incident/IncidentRepositoryTest.java): the Phase 3 promise kept: the **partial unique index rejects a second open incident on a real database**. `@Transactional` on the class rolls back after each test.
5. [StatsServiceTest.java](src/test/java/com/karaninder/pulseWatch/stats/StatsServiceTest.java) and [StatsMaintenanceServiceTest.java](src/test/java/com/karaninder/pulseWatch/stats/StatsMaintenanceServiceTest.java): fast Mockito tests for the arithmetic and the order (`InOrder`)
6. [StatsControllerTest.java](src/test/java/com/karaninder/pulseWatch/stats/StatsControllerTest.java), [IncidentControllerTest.java](src/test/java/com/karaninder/pulseWatch/incident/IncidentControllerTest.java), [IncidentQueryServiceTest.java](src/test/java/com/karaninder/pulseWatch/incident/IncidentQueryServiceTest.java): web layer and clamping
7. [StatsApiTest.java](src/test/java/com/karaninder/pulseWatch/stats/StatsApiTest.java): nothing mocked. Read `thirtyDayUptimeSurvivesTheNightlyCleanupOfRawRows`: it is the whole rollup story as one test.

**Questions:** Why is `StatsRepository` tested against Postgres and not with a mock? Why does `IncidentRepositoryTest` need `incidents.flush()` between resolving one incident and saving the next? (Hint: Hibernate orders its SQL.)

**Experiment:** In `StatsRepository`, change `date_trunc('hour', checked_at, 'UTC')` to `date_trunc('hour', checked_at)` and run `./mvnw test -Dtest=StatsRepositoryTest`. Which tests fail, and why only because of the Kolkata setting? Then undo.

## Stop 14: Authentication and ownership (Phase 6, 150 min)

The big idea: the server never remembers who you are. At login it hands you a **signed token**; you send it with every request; the server only checks the signature. Then every query is limited to *your* rows.

**14a. The database**
- [V7__create_users_and_monitor_ownership.sql](src/main/resources/db/migration/V7__create_users_and_monitor_ownership.sql): `users`, a unique index on `lower(email)`, and how pre-existing monitors were handed to a placeholder `legacy@pulsewatch.local` account (look at the `WHERE EXISTS` and the `'!'` hash) before `user_id` became `NOT NULL`. Why add the column nullable first?

**14b. Getting a token** (`auth` package)

| # | File | One-line job |
|---|---|---|
| 1 | [User.java](src/main/java/com/karaninder/pulseWatch/auth/User.java), [UserRepository.java](src/main/java/com/karaninder/pulseWatch/auth/UserRepository.java) | entity holding only a password *hash* |
| 2 | [RegisterRequest.java](src/main/java/com/karaninder/pulseWatch/auth/RegisterRequest.java), [LoginRequest.java](src/main/java/com/karaninder/pulseWatch/auth/LoginRequest.java) | validation: why is the password limited to 72 characters? |
| 3 | [AuthService.java](src/main/java/com/karaninder/pulseWatch/auth/AuthService.java) | `register` (normalise email, BCrypt, handle the race) and `login` (see the `dummyHash` trick) |
| 4 | [SecurityProperties.java](src/main/java/com/karaninder/pulseWatch/auth/SecurityProperties.java), [JwtConfig.java](src/main/java/com/karaninder/pulseWatch/auth/JwtConfig.java) | the secret, and the encoder (signs) and decoder (verifies) built from it |
| 5 | [JwtService.java](src/main/java/com/karaninder/pulseWatch/auth/JwtService.java) | builds the claims (`sub` = user id, `exp`) and signs |
| 6 | [AuthController.java](src/main/java/com/karaninder/pulseWatch/auth/AuthController.java) | the two public endpoints |
| 7 | [EmailAlreadyRegisteredException](src/main/java/com/karaninder/pulseWatch/auth/EmailAlreadyRegisteredException.java), [InvalidCredentialsException](src/main/java/com/karaninder/pulseWatch/auth/InvalidCredentialsException.java) + [GlobalExceptionHandler](src/main/java/com/karaninder/pulseWatch/common/GlobalExceptionHandler.java) | 409 and 401 as `ProblemDetail` |

**14c. Checking a token on every request**
- [SecurityConfig.java](src/main/java/com/karaninder/pulseWatch/auth/SecurityConfig.java): the *filter chain*. Read it line by line: why `csrf.disable()`, why `STATELESS`, which paths are public, what `oauth2ResourceServer(jwt)` adds, and the `PasswordEncoder` bean.
- [AuthenticatedUser.java](src/main/java/com/karaninder/pulseWatch/auth/AuthenticatedUser.java): how a controller learns the caller's id (`@AuthenticationPrincipal Jwt`).
- [OpenApiConfig.java](src/main/java/com/karaninder/pulseWatch/auth/OpenApiConfig.java): the "Authorize" button in Swagger.

**14d. Ownership (the other half)**
- [MonitorRepository.java](src/main/java/com/karaninder/pulseWatch/monitor/MonitorRepository.java): `findByIdAndUserId`, `existsByIdAndUserId`, and the one deliberately *unfiltered* method (the scheduler).
- [MonitorService.java](src/main/java/com/karaninder/pulseWatch/monitor/MonitorService.java) and [MonitorController.java](src/main/java/com/karaninder/pulseWatch/monitor/MonitorController.java): the `userId` parameter threaded through every call.
- [StatsService.requireOwnedMonitor](src/main/java/com/karaninder/pulseWatch/stats/StatsService.java) and [IncidentQueryService](src/main/java/com/karaninder/pulseWatch/incident/IncidentQueryService.java): the same rule on the read side.
- [AlertService.java](src/main/java/com/karaninder/pulseWatch/alert/AlertService.java): alerts now go to the monitor *owner's* email (`Target`, `recipientEmail`).

Trace a request:

```
POST /api/auth/login  {email, password}
  → AuthController → AuthService.login: findByEmail → BCrypt.matches → JwtService.issueFor → signed token
GET /api/monitors/5   Authorization: Bearer <token>
  → security filter chain: BearerTokenAuthenticationFilter → JwtDecoder (signature + expiry) → Jwt becomes the principal
  → MonitorController.get(@AuthenticationPrincipal Jwt) → AuthenticatedUser.id(jwt) = 42
  → MonitorService.findById(42, 5) → SELECT ... WHERE id = 5 AND user_id = 42
  → found: 200   |   not found (missing OR someone else's): MonitorNotFoundException → 404
```

**Questions:**
1. Why is a JWT safe to trust even though the client holds it? What can a client NOT do to it?
2. Why 404 and not 403 for another user's monitor?
3. Why does `login` hash the password against a dummy hash when the email is unknown?
4. What does BCrypt give you that `SHA-256(password)` does not?
5. Why is CSRF protection switched off here, and when would that be wrong?
6. What happens to a stolen token? What would you add to limit the damage (Phase 8 list)?
7. Why does the scheduler's `findByEnabledTrue...` method not take a user id, and what rule keeps that safe?

**Experiments:**
1. In Swagger: `POST /api/auth/register`, copy the token, press **Authorize**, paste it, then create and list monitors. Register a second user and confirm their list is empty.
2. Decode your token's payload (it is just Base64): `echo '<middle part>' | base64 -d`. Find `sub`, `exp`, `email`. Notice there is *no password* in it, and that anyone can read it but nobody can change it.
3. `curl -i localhost:8080/api/monitors` (401, look at the `WWW-Authenticate` header), then with a garbage token.
4. Look at your stored hash: `select email, password_hash from users;` and register the same password for two users: the hashes differ (the salt).
5. Expiry: set `pulsewatch.security.token-ttl: PT10S`, log in, wait 11 seconds, call an endpoint (401). Undo it.

## Stop 15: Metrics, Prometheus and Grafana (Phase 6, 90 min)

**15a. What the app records**

| File | Job |
|---|---|
| [CheckMetrics.java](src/main/java/com/karaninder/pulseWatch/check/CheckMetrics.java) | a counter (`pulsewatch.checks`, tag `result`), a timer with a histogram (`pulsewatch.check.duration`), a retries counter. Read the comment on tags: why never a monitor id? |
| [IncidentMetrics.java](src/main/java/com/karaninder/pulseWatch/incident/IncidentMetrics.java) | a **gauge**: a number read at scrape time (monitors down right now) |
| [AlertService.record](src/main/java/com/karaninder/pulseWatch/alert/AlertService.java) | counts every alert delivery by type, channel and status |
| [application.yaml](src/main/resources/application.yaml) | `prometheus` exposed; a common `application` tag |

Counter vs gauge vs timer: a counter only goes up (use `rate()` in queries), a gauge goes up and down, a timer records durations.

**15b. Prometheus and Grafana**
- [compose.yaml](compose.yaml): the two new services; [observability/prometheus.yml](observability/prometheus.yml): scrape both `:8080` and `:8082` every 5 s.
- [observability/grafana/](observability/grafana/): *provisioning* means Grafana configures itself from files (datasource, dashboard provider, and [pulsewatch.json](observability/grafana/dashboards/pulsewatch.json)). Read the PromQL of each panel: `rate(...[1m])`, `sum by (result)`, `histogram_quantile(0.95, ...)`.

**Questions:** What would happen to Prometheus if you tagged `pulsewatch.checks` with the monitor id and had 10 000 monitors? Why does `histogram_quantile` need `publishPercentileHistogram()`? Why is the open-incidents gauge `max(...)` and not `sum(...)` in the dashboard when two instances run?

**Experiments:**
1. Run the app, open `http://localhost:8080/actuator/prometheus` and find the `pulsewatch_*` lines.
2. Open `http://localhost:9090/targets` (are both targets up? a missing second instance shows as down), then run the query `sum by (result) (rate(pulsewatch_checks_total[1m]))` in the Prometheus UI.
3. Open Grafana at `http://localhost:3001`. Create a monitor on `http://localhost:8081/status/500` and watch "Monitors down right now" turn red a minute later.

## Stop 16: ShedLock and the Phase 6 tests (90 min)

**16a. Why you need it.** The scheduler *claims* due monitors, but two instances can read the same due rows before either commits, and both would check. ShedLock makes the whole tick a critical section across instances.

- [SchedulerLockConfig.java](src/main/java/com/karaninder/pulseWatch/common/SchedulerLockConfig.java): the `LockProvider` over the database; `usingDbTime()` and why.
- [V8__create_shedlock.sql](src/main/resources/db/migration/V8__create_shedlock.sql): the lock table.
- [CheckScheduler.tick](src/main/java/com/karaninder/pulseWatch/check/CheckScheduler.java) and [StatsMaintenanceJob.nightly](src/main/java/com/karaninder/pulseWatch/stats/StatsMaintenanceJob.java): `@SchedulerLock` with `lockAtMostFor` (the safety net if a winner dies) and `lockAtLeastFor`.

**Experiment (the proof): two instances.** In two terminals run `./mvnw spring-boot:run` and `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8082`. Create a monitor with `intervalSeconds: 10`. After a minute:
```bash
docker exec pulsewatch-postgres-1 psql -U pulsewatch -c "select monitor_id, count(*), min(gap) from (select monitor_id, checked_at - lag(checked_at) over (partition by monitor_id order by checked_at) gap from check_results) g group by 1"
```
`min(gap)` stays near 10 s: no double checks. Then watch the Grafana panel "Checks per minute by instance": the work is split between the two instances, never doubled. Stop one instance: the other takes over everything.

**16b. The tests**
1. [SecurityIntegrationTest.java](src/test/java/com/karaninder/pulseWatch/auth/SecurityIntegrationTest.java): the "done when" of the phase. Two real users, real tokens, real database: read `twoUsersCannotSeeOrTouchEachOthersMonitors` slowly.
2. [AuthServiceTest](src/test/java/com/karaninder/pulseWatch/auth/AuthServiceTest.java), [JwtServiceTest](src/test/java/com/karaninder/pulseWatch/auth/JwtServiceTest.java) (expired, forged and tampered tokens are all rejected), [AuthControllerTest](src/test/java/com/karaninder/pulseWatch/auth/AuthControllerTest.java)
3. How the older controller tests changed: [MonitorControllerTest](src/test/java/com/karaninder/pulseWatch/monitor/MonitorControllerTest.java) now imports `SecurityConfig`, mocks `JwtDecoder` and uses `jwt()`; find `everyEndpointRequiresAToken`.
4. [CheckMetricsTest](src/test/java/com/karaninder/pulseWatch/check/CheckMetricsTest.java) (see the "no monitor id tag" test) and [IncidentMetricsTest](src/test/java/com/karaninder/pulseWatch/incident/IncidentMetricsTest.java): `SimpleMeterRegistry` is a metrics registry for tests.
5. [SchedulerLockTest.java](src/test/java/com/karaninder/pulseWatch/common/SchedulerLockTest.java): two `JdbcTemplateLockProvider`s play two app instances; and `tick()` is skipped while "another instance" holds the lock.
6. [EveryScheduledJobIsLockedTest.java](src/test/java/com/karaninder/pulseWatch/common/EveryScheduledJobIsLockedTest.java): a *guard* test that scans the code with reflection so a future `@Scheduled` job without `@SchedulerLock` fails the build.

**Questions:** Why does `SchedulerLockTest` wait in a loop to take the lock first? What does `lockAtMostFor` protect against that `lockAtLeastFor` does not? Why is a database row an acceptable lock here but not for high-frequency locking?

## Stop 17: The frontend project and its design system (Phase 7, 60 min)

The dashboard lives in `frontend/` and is a separate program: it only talks to the Java app through the REST API. If you know Java, think of it as a second client of the same contract.

**17a. Project setup**
- [package.json](frontend/package.json): `dependencies` (shipped to users: React, router, TanStack Query, the font) versus `devDependencies` (only for building and testing: Vite, TypeScript, Vitest). Compare with `pom.xml`.
- [vite.config.ts](frontend/vite.config.ts): the **proxy**. The browser asks Vite for `/api/...`, Vite forwards to Spring Boot on 8080. Same origin from the browser's view, so no CORS setup. (In Phase 8 something else, a reverse proxy, does this job in production.)
- [tsconfig.json](frontend/tsconfig.json): `strict` and `noUncheckedIndexedAccess` make `array[0]` possibly `undefined`, which is why you see `!` and `as` in a few places.
- [index.html](frontend/index.html): the one HTML page; note the tiny script that applies the saved theme before first paint (no light flash in dark mode).

**17b. The design system (the "why does it look like this" stop)**
- [tokens.css](frontend/src/styles/tokens.css): the palette as CSS variables. Rules written in its header: warm greys, **one** accent (blue), status colours (green, amber, red) only for state and always beside a word. Light values on `:root`, dark values under `prefers-color-scheme` and under `[data-theme='dark']` (the toggle).
- [base.css](frontend/src/styles/base.css): reset, typography (Inter, 14px, tabular numbers where numbers line up), focus ring, `prefers-reduced-motion`.
- [ui.css](frontend/src/styles/ui.css): every component's style, in the order the app uses them. Find the media query at the bottom: that is the whole mobile layout.

**Questions:** Why are colours variables and never hex values inside components? Why is the dark theme declared twice? Why does the status dot come with a word (look at [StatusBadge.tsx](frontend/src/components/StatusBadge.tsx))?
**Experiment:** In `tokens.css` change `--accent` and watch every button, link, focus ring and the chart line update. Then flip your OS to dark mode.

## Stop 18: Talking to the API (Phase 7, 90 min)

Read bottom-up, like the check engine.

| # | File | One-line job |
|---|---|---|
| 1 | [api/types.ts](frontend/src/api/types.ts) | TypeScript shapes of the Java records (`MonitorOverview`, `StatsResponse`...). Compare each with its Java record |
| 2 | [api/client.ts](frontend/src/api/client.ts) | the only place that calls `fetch`: adds the token, turns errors into `ApiError`, reacts to 401 |
| 3 | [lib/jwt.ts](frontend/src/lib/jwt.ts) | reads the email out of the token (display only, never trusted) |
| 4 | [auth/AuthContext.tsx](frontend/src/auth/AuthContext.tsx) | React **context**: who is signed in, `signIn`, `signOut`, session in `localStorage` |
| 5 | [api/hooks.ts](frontend/src/api/hooks.ts) | TanStack Query hooks: `useOverview`, `useStats`, `useLatency`, mutations. Notice the `keys` object and `refetchInterval` |
| 6 | [App.tsx](frontend/src/App.tsx) | providers, routes, `RequireAuth` |
| 7 | [main.tsx](frontend/src/main.tsx) | the entry point: imports the CSS and renders `<App />` |

Trace "open the monitor list after a page reload":

```
main.tsx → <App/> → <AuthProvider>: useState initialiser reads localStorage and calls setAuthToken(token)   (synchronously!)
  → route "/" → RequireAuth sees a session → <AppShell/> → <MonitorsPage/>
  → useOverview(): GET /api/monitors/overview with "Authorization: Bearer ..."   (client.ts)
  → Spring Security checks the token, MonitorService/StatsService filter by user id
  ← JSON → React Query caches it under ['overview'] → the page renders rows
  every 30 s: refetchInterval fetches again; the screen updates only if the data changed
a 401 at any point → onUnauthorized → signOut() → router sends you to /login
```

**Questions:**
1. Why is `setAuthToken` called inside the `useState` initialiser instead of in a `useEffect`? (This was a real bug during development: a reload signed you out. Work out why an effect was too late.)
2. What does React Query do for you that a plain `useEffect` + `fetch` would not (caching, dedup, refetch interval, `placeholderData`)?
3. Why does `client.ts` not treat a 401 from `/api/auth/login` as "session expired"?
4. Why does signing out call `queryClient.clear()`?
5. The token is in `localStorage`. What is the risk and what is the alternative?

**Experiments:** Open DevTools, Network tab, and watch `/api/monitors/overview` repeat every 30 seconds. Application tab, Local Storage: find `pulsewatch.session`; delete it and reload (you are sent to login). Then in `AuthContext.tsx` move `setAuthToken(...)` into a `useEffect`, reload while signed in, and see the sign-out bug come back. Undo it.

## Stop 19: Pages, components and charts (Phase 7, 120 min)

**19a. React in five ideas (before the files)**
1. A **component** is a function that returns UI. Its **props** are its inputs.
2. **State** (`useState`) is memory inside a component; changing it re-renders.
3. **Effects** (`useEffect`) run code after rendering (subscribe, timers). Clean up in the returned function.
4. UI is a pure function of state and props: you never edit the DOM by hand.
5. **Lists need keys**, so React can tell which row is which.

**19b. Reading order**

| # | File | What to notice |
|---|---|---|
| 1 | [components/StatusBadge.tsx](frontend/src/components/StatusBadge.tsx), [SegmentedControl.tsx](frontend/src/components/SegmentedControl.tsx), [States.tsx](frontend/src/components/States.tsx) | tiny reusable components; generics in `SegmentedControl<T>` |
| 2 | [pages/LoginPage.tsx](frontend/src/pages/LoginPage.tsx) | a controlled form, error display, switching between sign-in and register |
| 3 | [pages/MonitorsPage.tsx](frontend/src/pages/MonitorsPage.tsx) | `sortMonitors` (trouble first), `useMemo`, loading/empty/error states, the row as a `<Link>` |
| 4 | [components/monitorValidation.ts](frontend/src/components/monitorValidation.ts) | the server's validation rules repeated for a faster answer; why numbers stay strings in the form |
| 5 | [components/Drawer.tsx](frontend/src/components/Drawer.tsx) + [MonitorDrawer.tsx](frontend/src/components/MonitorDrawer.tsx) | focus handling, Escape key, mapping server field errors onto fields |
| 6 | [charts/scales.ts](frontend/src/charts/scales.ts) | the maths of a chart as pure functions: `niceCeil`, `yAxis`, `buildStripCells` (fills empty buckets), `splitRuns` (breaks a line at gaps) |
| 7 | [charts/LatencyChart.tsx](frontend/src/charts/LatencyChart.tsx) | an SVG drawn by hand: scales, paths, crosshair, tooltip, arrow keys, "View as table" |
| 8 | [charts/StatusStrip.tsx](frontend/src/charts/StatusStrip.tsx) | one cell per bucket; each cell is a focusable button with a spoken description |
| 9 | [pages/MonitorDetailPage.tsx](frontend/src/pages/MonitorDetailPage.tsx) | the biggest file: six queries, the window switch, the incident banner, delete confirmation |

**19c. Design details worth noticing**
- Response time is **two lines on one axis**: the median in the accent blue, the p95 in neutral grey. The skill rule: one series is the point, the other is context.
- The check-results strip answers "when did it fail?" at a glance; the numbers are in the tooltip and in the table.
- While a new time range loads, the old chart stays at reduced opacity (`placeholderData`, class `refetching`): no flicker, no jumping layout.
- Every chart has three ways in: hover, keyboard (Tab then arrows), and a table. Colour is never the only signal.
- Sizes: the hero figure (uptime) is the only large number; everything else is 24px.

**Questions:** Why is the chart drawn at the container's measured width ([useElementWidth.ts](frontend/src/charts/useElementWidth.ts)) rather than scaled by CSS? Why does `buildStripCells` create cells for buckets that have no data? Why is there no 30-day latency chart (follow the reason back to Phase 5)? What does `useNow` do and why is it not a refetch?

**Experiments:**
1. Run the whole stack (below) and open `http://localhost:5173`. Create a monitor on `http://localhost:8081/status/500`, wait a minute: the row turns red, the detail page shows the banner and the strip.
2. In `LatencyChart.tsx` change the p95 colour to `var(--critical)` and see why a status colour on a data series is misleading. Undo.
3. Resize the browser window below 760px and compare with the media query at the bottom of `ui.css`.

**Running everything:**
```bash
docker compose up -d                 # Postgres, httpbin, Mailpit, Prometheus, Grafana
./mvnw spring-boot:run               # the API on :8080
cd frontend && npm install && npm run dev   # the UI on :5173
```
A demo account with synthetic history exists in your dev database: `demo@pulsewatch.dev` / `demo-password-1` (remove it with `delete from users where email = 'demo@pulsewatch.dev';`).

## Stop 20: Frontend tests (Phase 7, 60 min)

Run them with `cd frontend && npm test`.

1. [lib/format.test.ts](frontend/src/lib/format.test.ts), [jwt.test.ts](frontend/src/lib/jwt.test.ts), [charts/scales.test.ts](frontend/src/charts/scales.test.ts), [components/monitorValidation.test.ts](frontend/src/components/monitorValidation.test.ts): plain functions, the easiest tests there are
2. [api/client.test.ts](frontend/src/api/client.test.ts): `fetch` replaced by a fake with `vi.stubGlobal`
3. [test/utils.tsx](frontend/src/test/utils.tsx): the **test harness**: `mockApi` (a fake server keyed by `"GET /path"`), `signIn`, `renderApp` (wraps a component in the same providers the app uses)
4. [pages/LoginPage.test.tsx](frontend/src/pages/LoginPage.test.tsx), [MonitorsPage.test.tsx](frontend/src/pages/MonitorsPage.test.tsx), [MonitorDetailPage.test.tsx](frontend/src/pages/MonitorDetailPage.test.tsx), [components/MonitorDrawer.test.tsx](frontend/src/components/MonitorDrawer.test.tsx): tests written the way a user acts (type, click) and asserting what a user sees (text, roles), never internal state
5. [charts/charts.test.tsx](frontend/src/charts/charts.test.tsx): SVG and keyboard behaviour in jsdom

Compare with the Java tests: `mockApi` plays the role of `@MockitoBean`, `renderApp` plays the role of `@WebMvcTest`'s slice, and `userEvent` plays the role of `MockMvc.perform`.

**Questions:** Why do the tests find elements by role and label (`getByRole('button', { name: 'Create monitor' })`) instead of CSS classes? Why does `renderApp` create a fresh `QueryClient` per test? What does `vi.unstubAllGlobals()` protect against?
**Experiment:** In `MonitorsPage.tsx` change the `DOWN: 0` severity to `DOWN: 9` and run `npm test`: two tests fail. That is what the tests are for.

## Stop 21: Hardening for the internet (Phase 8, 90 min)

Two attacks every public service must survive: "make the server call something it should not" and "guess passwords all day".

**21a. SSRF (server-side request forgery)**
- [TargetGuard.java](src/main/java/com/karaninder/pulseWatch/check/TargetGuard.java): resolves a hostname and refuses loopback, private, link-local (`169.254.169.254` is the cloud credentials endpoint), carrier-grade NAT and IPv6 unique-local addresses. Read `isInternal` and the Javadoc about the DNS-rebinding gap.
- [CheckProperties.java](src/main/java/com/karaninder/pulseWatch/check/CheckProperties.java) and [application-local.yaml](src/main/resources/application-local.yaml): the switch `allow-private-targets`, **off by default**, on only for local development. Find where the Maven plugin selects the `local` profile in [pom.xml](pom.xml).
- Where it is applied: [MonitorService](src/main/java/com/karaninder/pulseWatch/monitor/MonitorService.java) (`requirePublicTarget` on create and update, so the user gets an error on the URL field) and [HttpChecker](src/main/java/com/karaninder/pulseWatch/check/HttpChecker.java) (again before every check, because DNS answers change). Redirects are never followed ([CheckConfig](src/main/java/com/karaninder/pulseWatch/check/CheckConfig.java)).
- [ForbiddenTargetException](src/main/java/com/karaninder/pulseWatch/monitor/ForbiddenTargetException.java) and [GlobalExceptionHandler](src/main/java/com/karaninder/pulseWatch/common/GlobalExceptionHandler.java): a 400 with `errors.url`, so the UI shows it next to the field.

**21b. Rate limiting**
- [AttemptLimiter.java](src/main/java/com/karaninder/pulseWatch/auth/AttemptLimiter.java): a sliding window in memory (`ConcurrentHashMap` of `Deque<Instant>`, `synchronized` per key). Notice that refused attempts are not counted.
- [AuthService.java](src/main/java/com/karaninder/pulseWatch/auth/AuthService.java): three limits (per account, per IP, registrations per IP) and the reset after a successful login. [AuthController](src/main/java/com/karaninder/pulseWatch/auth/AuthController.java) passes `request.getRemoteAddr()`.
- [TooManyAttemptsException](src/main/java/com/karaninder/pulseWatch/auth/TooManyAttemptsException.java) becomes a **429 with a `Retry-After` header** in the exception handler.

**Questions:**
1. Why is the SSRF check done twice (when saving and before each check)?
2. A user registers `http://evil.example/` whose DNS says `8.8.8.8` when you save and `127.0.0.1` a minute later. Which of your two checks stops it? What is the one gap left, and what closes it?
3. Why is the default `false` and the relaxation done through a profile instead of the other way round?
4. Why are refused attempts not recorded by the limiter?
5. Behind a reverse proxy every request seems to come from the proxy's IP. How does the app learn the real client IP, and why is trusting `X-Forwarded-For` dangerous if the app is directly reachable?
6. Why does a rate limit per account and a rate limit per IP both exist?

**Experiments:**
1. Run the app with `./mvnw spring-boot:run`, then in Swagger (or curl) create a monitor on `http://localhost:8081/status/200` (allowed locally). Now start it from the jar without the profile (`java -jar target/*.jar`) and try the same: refused.
2. Try `http://169.254.169.254/`, `http://10.0.0.5/`, `http://[::1]/` and `http://2130706433/` (the number form of 127.0.0.1: what does the guard say, and why?).
3. Fire 12 wrong logins in a row with curl and read the 429 and its `Retry-After`.

**Tests to read:** [TargetGuardTest](src/test/java/com/karaninder/pulseWatch/check/TargetGuardTest.java) (the boundaries of every private range), [AttemptLimiterTest](src/test/java/com/karaninder/pulseWatch/auth/AttemptLimiterTest.java) (a hand-made `Clock` that you advance), the SSRF cases in [MonitorServiceTest](src/test/java/com/karaninder/pulseWatch/monitor/MonitorServiceTest.java) and [SecurityIntegrationTest](src/test/java/com/karaninder/pulseWatch/auth/SecurityIntegrationTest.java) (`guessingOneAccountsPasswordIsStoppedAfterTenTries`).

## Stop 22: Docker images (Phase 8, 75 min)

An image is a frozen filesystem plus a start command. **Multi-stage** builds use a big image to build and copy only the result into a small one.

| File | What to notice |
|---|---|
| [Dockerfile](Dockerfile) | stage 1 (JDK + Maven) builds the jar; stage 2 (JRE on Alpine) runs it. `COPY pom.xml` and `dependency:go-offline` come BEFORE `COPY src`: a code change reuses the cached dependency layer. `jarmode=tools extract` splits libraries from your code into separate layers. `USER app` (not root). `JAVA_TOOL_OPTIONS` (heap as a percentage of the container limit). `HEALTHCHECK` |
| [.dockerignore](.dockerignore) | what is NOT sent to the builder: keeps builds fast and caches stable |
| [frontend/Dockerfile](frontend/Dockerfile) | stage 1 (Node) builds the static files; stage 2 is just Caddy plus those files. No Node in production |
| [frontend/Caddyfile](frontend/Caddyfile) | one site block: `/api/*` is proxied to the app, everything else is a static file with `index.html` as fallback (so `/monitors/5` survives a reload). Only `/api` is forwarded: `/actuator` and Swagger are unreachable from outside. Headers: a strict Content-Security-Policy, no sniffing, no framing, HSTS. `{$SITE_ADDRESS::80}` means "the environment variable, default :80": a real domain makes Caddy fetch and renew an HTTPS certificate by itself |
| [frontend/public/theme.js](frontend/public/theme.js) | the theme script was inline in `index.html`; a strict CSP forbids inline scripts, so it became a file |

**Questions:** Why does a JRE image have no `javac`, and why is that a security gain as well as a size gain? What happens to the layer cache if you move `COPY src` above `RUN ... dependency:go-offline`? Why must the Caddyfile use `app:8080` and not `localhost:8080`?
**Experiments:**
```bash
docker build -t pw-app .                       # watch the stages
docker history pw-app                          # the layers and their sizes
docker run --rm --entrypoint id pw-app -un     # prints "app", not root
docker run --rm --entrypoint ls pw-app /app    # no source code, no pom.xml
```
Then touch a Java file and rebuild: only the last layers rebuild.

## Stop 23: Compose, configuration and the smoke test (Phase 8, 90 min)

- [compose.yaml](compose.yaml): the full local stack. Read each service's `depends_on` with `condition: service_healthy` (the app waits for a healthy database, the web server waits for a healthy app) and the `healthcheck` blocks. Environment variables become Spring properties by **relaxed binding**: `SPRING_DATASOURCE_URL` is `spring.datasource.url`, `PULSEWATCH_CHECK_ALLOW_PRIVATE_TARGETS` is `pulsewatch.check.allow-private-targets`. That is how one image runs everywhere with different settings (the "12-factor" idea).
- [deploy/compose.prod.yaml](deploy/compose.prod.yaml) versus the local file: list the differences (no published ports except 80/443, `${VAR:?message}` makes missing secrets a startup error, SSRF guard on, real SMTP, `restart: unless-stopped`, volumes for certificates).
- [deploy/.env.example](deploy/.env.example): the one file an operator edits.
- [scripts/smoke-test.sh](scripts/smoke-test.sh): a shell script that checks a RUNNING stack the way a user would (web page, hidden endpoints, register, create a monitor, wait for the first check). Used by you and by CI.
- [docs/DEPLOY.md](docs/DEPLOY.md): the runbook.

**Questions:** What is the difference between `depends_on` without and with `condition: service_healthy`? Why does production not publish port 5432? Why does `deploy/.env` never get committed? What is the point of `restart: unless-stopped`?
**Experiments:**
1. `docker compose up --build`, open http://localhost:8088, run `scripts/smoke-test.sh`.
2. `docker compose stop postgres` and watch `docker compose ps` and the app logs; `docker compose start postgres`: does the app recover?
3. In `deploy/`, run `docker compose -f compose.prod.yaml config` without a `.env`: read the refusal message.
4. `docker compose exec app sh` then `id`, `env | grep PULSEWATCH`, `ls /app`.

## Stop 24: CI and the README (Phase 8, 45 min)

- [.github/workflows/ci.yml](.github/workflows/ci.yml): three jobs. `backend` (build and all tests, Testcontainers works because the runner has Docker), `frontend` (typecheck, tests, build), `images` (builds the whole stack with compose, runs the smoke test, and only on pushes to `main` logs in to GitHub's container registry and publishes the images). `needs:` orders jobs; `concurrency:` cancels outdated runs; `permissions:` gives the job only what it needs. It has not run on GitHub yet: expect small fixes on the first run.
- [README.md](README.md): written for a stranger. Re-read it as one: could you run the project in 3 commands? Find the two diagrams (Mermaid renders on GitHub) and the "Design decisions" section: every decision in it has a place in this guide.

**Questions:** Why do pull requests build the images but not publish them? Why is the smoke test in CI, when the unit tests already pass? Which README section would an interviewer read first?
**Experiment:** Break something on purpose (for example rename the `app` service in `compose.yaml` but not in the Caddyfile), run `docker compose up --build` and `scripts/smoke-test.sh`, and read which checks fail. This is exactly what the CI job protects against.

---

## Java concepts: where to see each one

| Concept | Look at |
|---|---|
| records | `MonitorRequest`, `MonitorResponse`, `CheckTarget`, `CheckOutcome` |
| compact constructor | `MonitorRequest` |
| enums | `MonitorMethod` |
| encapsulation (no setters) | `Monitor`, `CheckResult` |
| `Optional` | `MonitorService.findById` (`orElseThrow`) |
| exceptions (checked vs unchecked, `catch` order) | `MonitorNotFoundException`, `HttpChecker` |
| streams and lambdas | `MonitorController.list`, `CheckScheduleService`, `GlobalExceptionHandler` |
| `Instant` / `Duration` | `Monitor`, `HttpChecker`, `CheckScheduleService` |
| interfaces and substitution | `Executor` in `CheckScheduler`, `MonitorRepository` |
| virtual threads | `CheckConfig.checkExecutor` |
| custom annotations | `TimeoutShorterThanInterval` |
| static factory methods | `MonitorResponse.from`, `CheckOutcome.responded/failed`, `Incident.open` |
| `@FunctionalInterface`, lambdas, method references | `Sleeper`, `CheckConfig.sleeper` (`Thread::sleep`), `RetryingCheckerTest` (`sleeps::add`) |
| bit shifting (`1L << n`) | `RetryPolicy.backoffBefore` |
| loops and restoring the interrupt flag | `RetryingChecker.check` |
| `IllegalStateException` vs `IllegalArgumentException` | `Incident.resolve` vs `RetryPolicy` constructor |
| state as an enum | `IncidentTransition`, `AlertType`, `AlertStatus` |
| interfaces and polymorphism (many implementations, one type) | `Notifier`, `EmailNotifier`, `WebhookNotifier`, `List<Notifier>` in `AlertService` |
| immutable event records | `IncidentOpened`, `IncidentResolved`, `SlowResponseDetected` |
| enums that carry data | `StatsWindow` (label, length, bucket) |
| generics (`PageResponse<T>`, `Function<S, T>`) | `PageResponse.of` |
| nested records | `StatsMaintenanceService.Result` |
| text blocks for SQL | `StatsRepository` |
| `Math.round` for rounding, `double` pitfalls | `StatsService.toUptime`, `StatsRepository.nullableDouble` |
| `Instant.truncatedTo(ChronoUnit.HOURS)` | `StatsService.boundary`, `StatsMaintenanceService` |
| static `final` shared resources, static initialiser | `TestcontainersConfig` |
| `Locale.ROOT` for case folding | `AuthService.normalise` |
| `byte[]`, `StandardCharsets`, `SecretKeySpec` | `JwtConfig`, `SecurityProperties` |
| reflection (`getDeclaredMethods`, annotations) | `EveryScheduledJobIsLockedTest` |
| `Optional.flatMap` chains | `AlertService.target` |
| private nested records as tuples | `AlertService.Target` |
| `Clock` instead of `Instant.now()` | `AlertService`, `AlertConfig.clock`, `AlertServiceTest` |
| `Duration` arithmetic | `AlertService.describe`, `onSlowResponse` |
| text blocks vs string concatenation, `LinkedHashMap` | `AlertService` messages, `WebhookNotifier` payload |
| fakes vs mocks | `AlertServiceTest.FakeNotifier` |
| `AtomicReference`, `CountDownLatch` (thread hand-off in tests) | `AlertListenerTest`, `WebhookNotifierTest` |

## Frontend concepts: where to see each one

| Concept | Look at |
|---|---|
| components and props | `StatusBadge`, `SegmentedControl` |
| state (`useState`) and effects (`useEffect`) | `MonitorDrawer`, `ThemeToggle`, `useNow` |
| derived data (`useMemo`) | `MonitorsPage` |
| context (global state) | `AuthContext` |
| routing, protected routes | `App.tsx` (`RequireAuth`), `MonitorDetailPage` (`useParams`) |
| server state and caching (TanStack Query) | `api/hooks.ts` |
| controlled forms | `LoginPage`, `MonitorDrawer` |
| generics in TypeScript | `SegmentedControl<T>`, `request<T>`, `Page<T>` |
| union types as a replacement for enums | `MonitorStatus`, `Window` in `api/types.ts` |
| CSS variables and theming | `tokens.css` |
| SVG drawing | `LatencyChart`, `StatusStrip` |
| accessibility (roles, labels, focus, keyboard) | `Drawer`, `StatusStrip`, `LatencyChart` |
| component and user-flow tests | `*.test.tsx`, `test/utils.tsx` |

## DevOps concepts: where to see each one

| Concept | Look at |
|---|---|
| multi-stage image builds, layer caching | `Dockerfile`, `frontend/Dockerfile` |
| running as a non-root user | `Dockerfile` (`USER app`) |
| health checks and start ordering | `Dockerfile` `HEALTHCHECK`, `compose.yaml` `depends_on` |
| configuration through environment variables (relaxed binding) | `compose.yaml` `environment:` |
| secrets that must exist (`${VAR:?}`) | `deploy/compose.prod.yaml` |
| reverse proxy, TLS termination, security headers | `frontend/Caddyfile` |
| least exposure (only 80/443 published) | `deploy/compose.prod.yaml` |
| continuous integration, job dependencies, publishing images | `.github/workflows/ci.yml` |
| end-to-end smoke testing | `scripts/smoke-test.sh` |
| runbooks (backup, restore, update) | `docs/DEPLOY.md` |

## Spring concepts: where to see each one

| Concept | Look at |
|---|---|
| auto-configuration and starters | `pom.xml`, `application.yaml` |
| component scan, beans | `@Service`, `@Component`, `@RestController`, `@Bean` in `CheckConfig` |
| dependency injection (constructor) | every `Service` and `Controller` constructor |
| `@RestController`, mappings, `@PathVariable` | `MonitorController` |
| Bean Validation | `MonitorRequest`, `@Valid` |
| `@RestControllerAdvice`, `ProblemDetail` | `GlobalExceptionHandler` |
| JPA entity, repository, derived queries | `Monitor`, `MonitorRepository` |
| `@Transactional`, dirty checking | `MonitorService.update`, `CheckScheduleService` |
| Flyway vs `ddl-auto: validate` | `db/migration/`, `application.yaml` |
| `@Scheduled`, `@EnableScheduling` | `CheckScheduler`, `CheckConfig` |
| `@ConditionalOnProperty` | `CheckScheduler` |
| test slices (`@WebMvcTest`, `@SpringBootTest`) | the `monitor` and root test classes |
| `Pageable` as a limit | `CheckResultRepository.findByMonitorIdOrderByCheckedAtDesc` |
| exceptions crossing a transaction boundary | `CheckRunner` catching `DataIntegrityViolationException` from `IncidentService` |
| `@Bean` methods for plain objects | `CheckConfig.retryPolicy`, `CheckConfig.sleeper` |
| database constraints as the final guard | `V4` partial unique index |
| application events (`ApplicationEventPublisher`) | `IncidentService`, `CheckRunner` |
| `@TransactionalEventListener(AFTER_COMMIT)` | `AlertListener` |
| `@Async`, `@EnableAsync`, a named executor | `AlertListener`, `AlertConfig` |
| `@ConfigurationProperties` on a record | `AlertProperties`, `application.yaml` |
| injecting all beans of one interface | `AlertService(List<Notifier> ...)` |
| JavaMail via `spring-boot-starter-mail` | `EmailNotifier`, `spring.mail.*` |
| Jackson 3 (`tools.jackson`) | `WebhookNotifier` |
| slice vs full-context tests for wiring | `AlertListenerTest` |
| `InetAddress`, IP ranges, bit masking | `TargetGuard.isInternal` |
| a hand-written `Clock` for tests | `AttemptLimiterTest.ManualClock` |
| `synchronized` and `ConcurrentHashMap` | `AttemptLimiter` |
| HTTP 429 and response headers (`ResponseEntity`) | `GlobalExceptionHandler.handleRateLimited` |
| Spring profiles (`application-local.yaml`) | `pom.xml` plugin config |
| `JdbcClient` and plain SQL (CTE, `UNION ALL`, `percentile_cont`, `ON CONFLICT`) | `StatsRepository` |
| `@Transactional(readOnly = true)` | `StatsService`, `IncidentQueryService` |
| cron scheduling | `StatsMaintenanceJob` |
| pagination (`Pageable`, `PageRequest`, `Sort`, `Page`) | `IncidentRepository.findByMonitorId`, `IncidentQueryService` |
| Testcontainers, `@ServiceConnection`, `@Import` of a test configuration | `TestcontainersConfig`, `StatsRepositoryTest` |
| `@AutoConfigureMockMvc` with a full context | `StatsApiTest` |
| `@Transactional` test rollback | `IncidentRepositoryTest` |
| Spring Security filter chain, stateless sessions, CSRF | `SecurityConfig` |
| OAuth2 resource server and JWT (Nimbus) | `JwtConfig`, `SecurityConfig` |
| `@AuthenticationPrincipal` | every controller |
| BCrypt / `PasswordEncoder` | `SecurityConfig`, `AuthService` |
| testing with security (`jwt()` post-processor, `@Import(SecurityConfig)`) | the controller tests |
| Micrometer: counter, timer, gauge, `MeterRegistry` | `CheckMetrics`, `IncidentMetrics` |
| ShedLock, `@SchedulerLock` | `SchedulerLockConfig`, `CheckScheduler` |
| unique/`lower()` function index for case-insensitive uniqueness | `V7` |

## Break it on purpose (the fastest way to learn)

Do each, read the error, and undo it:
1. Remove `@Service` from `MonitorService`: what does Spring say at startup?
2. Remove `@Valid` from `create`: which tests fail?
3. Edit a comment inside `V1__create_monitors.sql`: what does Flyway say?
4. Remove `@EnableScheduling`: what happens to the checks (nothing, silently)? How would you notice?
5. In `CheckScheduleService`, delete the `scheduleNextCheck` line: watch checks pile up for a slow monitor.
6. Swap the two `catch` blocks in `HttpChecker` (put `IOException` first): does it compile? Read the compiler's message and explain it.
7. In `RetryingChecker`, change `attempts < allowedAttempts` to `true`: which test hangs or fails, and why would production loop forever on a dead URL?
8. Insert two open incidents for the same monitor by hand in `psql` (see exercise 13 for the exact statements). Read the error and name the index.
9. Edit `V4__create_incidents.sql` after it has run and restart: read Flyway's checksum error. Then undo.
10. Remove `@Async("alertExecutor")` from `AlertListener.slowResponse`: which test fails? What would a slow SMTP server do to your checks in production?
11. In `AlertService.dispatch`, change `catch (RuntimeException e)` to `catch (NotificationException e)` and make `EmailNotifier` throw an `IllegalStateException`: what happens to the other channels?
12. Set `pulsewatch.alerts.cooldown: banana` in `application.yaml` and start the app: read how Spring reports a property it cannot bind. Then undo.
13. Insert two open incidents by hand to see the index work (use the id of a monitor that exists, from `select id from monitors;`, here 2; delete the row afterwards):
    `insert into incidents (monitor_id, started_at) values (2, now());` twice, then `delete from incidents where monitor_id = 2;`
14. In `StatsService.boundary`, change `truncatedTo(ChronoUnit.HOURS)` to no truncation and run `StatsApiTest`: does anything fail? Think about what would happen between 03:00 and the next night in production.
15. In `StatsMaintenanceService.run`, swap the two calls (delete first, then roll up) and run `StatsApiTest`: which test fails, and what real-world data loss does it model?
16. Remove `@SchedulerLock` from `StatsMaintenanceJob.nightly` and run `./mvnw test -Dtest=EveryScheduledJobIsLockedTest`: read the failure message it prints.
17. In `MonitorService.findById`, change `findByIdAndUserId(id, userId)` back to `findById(id)` and run `SecurityIntegrationTest`: which assertion fails first? This is exactly the bug these tests exist to catch.
18. In `SecurityConfig`, add `/api/monitors/**` to the `permitAll()` list and run `MonitorControllerTest`: which test fails, and why would the app still not leak data in production? (Think about `AuthenticatedUser.id(jwt)`.)
19. Change one character of the token's signature part and call the API: 401. Change one character of the payload part: also 401. Explain why in one sentence each.
20. Change `Route path="/monitors/:id"` in `App.tsx` to `/m/:id` and click a monitor: the list still renders, but clicking a monitor falls into the catch-all route and bounces you back to the list. Why do the `Link`s and the route have to agree, and what test would catch it?
21. In `api/hooks.ts` change `REFRESH_MS` to 3000 and watch the Network tab. What would 3000 ms mean for the database with 1000 users, and why is 30 s a better default?
22. Remove `key={...}` from the rows in `MonitorsPage.tsx` and read the console warning React prints.
23. Remove the `if (allowPrivateTargets)` shortcut from `TargetGuard` (so it always checks) and run `./mvnw test -Dtest=HttpCheckerTest`: which tests fail and why? Undo it.
24. In `HttpChecker`, delete the `targetGuard.violation(...)` block (keeping the one in `MonitorService`). Which test notices? What attack does that leave open?
25. In `AuthService.login`, move `limiter.record(...)` AFTER the password check and run `AuthServiceTest`. What attack does that allow even though no test of "wrong password" fails? (Think about what you are counting.)
26. In `frontend/Caddyfile` change `handle /api/*` to `handle` for everything and rebuild: run `scripts/smoke-test.sh`. Which check catches the leak?
27. Remove `USER app` from the Dockerfile, rebuild, and run `docker run --rm --entrypoint id <image> -un`.
28. Delete `condition: service_healthy` under `web` in `compose.yaml` and start from scratch (`docker compose down -v && docker compose up --build`): do you see an error in the web logs while the app is starting? What does the browser show during that time?
