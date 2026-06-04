# Implementation Plan — Global Credit Scoring Engine Refactor

This document is the working plan for refactoring the Global Credit Scoring Engine. It maps every
requirement in the brief to a concrete change, explains *why*, and tracks progress in phases.
The target architecture itself is described in [README.md](README.md).

---

## 0. Requirements → solution map

| # | Requirement (from brief) | Solution | Phase |
|---|--------------------------|----------|-------|
| 1 | Spring Boot 3.5 (upgradeable with Spring App Advisor) | Bump parent BOM to 3.5.x, keep Java 17, no hard-pinned versions | P1 |
| 2 | Several Postgres tables (criminal records, credit history, …) | Normalize into `customers`, `credit_history`, `criminal_records`, `credit_scores` | P2 |
| 3 | Populate sample data | Rewrite `DataLoader` to seed all tables with coherent, joinable data | P2 |
| 4 | Cache calculated scores in Valkey with eviction | `CreditScoreCacheStore` with TTL from admin settings | P3, P6 |
| 5 | RabbitMQ queues inbound requests | Keep, behind a `ScoreRequestPublisher` interface (swappable for local) | P5 |
| 6 | GenAI `credit-chat` natural-language querying (currently broken) | Spring AI `ChatClient` + `@Tool` function-calling, bound to `credit-chat` | P4 |
| 7 | Admin portal for eviction rules & config | `admin.html` + `AdminController` + `SettingsService` | P6 |
| 8 | Distributed-transaction Tanzu Hub alert | Scope `@Transactional` to Postgres only; cache-aside after commit | P3 |
| 9 | CPU spikes | Remove blocking sleep, kill `KEYS` scans, bound top-N, tune refresh/concurrency | P3 |
| 10 | Digital assistant showing changes live in a local app | `local` profile (no external services) + Claude live preview of the UI | P5 |
| 11 | Header perf metrics (Valkey vs Postgres) + top-10 table + refresh | Keep & harden existing UI against the new bounded endpoints | P5 |

---

## 1. Current-state assessment

Read of the existing code (Spring Boot **3.2.2**, Java 17) surfaced these problems:

1. **Distributed transaction.** `CreditScoreCalculator.processAndCacheScore` is annotated
   `@Transactional` and writes to **PostgreSQL** (`userFinancialsRepository.save`) *and* **Valkey**
   (`creditScoreCacheRepository.save`) inside the same method. Two resource managers under one
   logical transaction is exactly the pattern Tanzu Hub flagged. Valkey is not transactional, so on
   a post-cache failure the DB rolls back but the cache does not — inconsistent state, and it blocks
   independent scaling.

2. **CPU spikes — several contributors:**
   - `Thread.sleep(200)` inside the `@RabbitListener` path blocks a listener thread on every message
     (artificial latency that burns threads under load).
   - `CrudRepository.findAll()` over `@RedisHash` objects issues a Redis **`KEYS`** / full-keyspace
     `SCAN` plus per-entity hash reads. The UI calls `/api/scores` (→ `findAll`) **every 5 seconds**,
     so cache growth turns into repeated O(N) keyspace scans — a textbook Redis/Valkey CPU spike.
   - `@RedisHash` secondary indexes (`findBySsn`, `findByRiskLevel`, `findByCalculatedScoreGreaterThan`)
     maintain extra index sets on every write.
   - `ddl-auto: update` + `show-sql: true` add overhead and log churn in production.

3. **Single denormalized table.** `user_financials` holds everything; there is no real join, no
   criminal-records or credit-history tables.

4. **Fake GenAI.** `AiController` is a regex parser over cached rows — not the `credit-chat` GenAI
   service. The brief explicitly wants Spring AI integration.

5. **No admin portal, no eviction policy.** Cache entries never expire; nothing is configurable.

6. **No local-only run mode.** Everything assumes real Postgres/Valkey/RabbitMQ, so the frontend
   cannot be inspected without the full backing stack.

---

## 2. Design principles for the refactor

- **One resource manager per transaction.** DB owns the transaction; the cache is updated *after*
  commit (cache-aside / write-through), never inside the DB transaction.
- **Bounded cache access.** No unbounded `KEYS`/`findAll`. "Top-N recent scores" is a single bounded
  structure (Redis sorted set / capped list), and individual scores are plain keyed lookups with TTL.
- **Swap by profile, not by `if`.** Externalize Valkey, RabbitMQ and GenAI behind interfaces with
  `@Profile("cloud")` real implementations and `@Profile("local")` in-memory ones, so the UI runs
  standalone for live preview.
- **Stay BOM-aligned.** Use Spring-Boot-managed and Spring-AI-BOM-managed versions so Spring
  Application Advisor can drive future upgrades cleanly.

---

## 3. Phase plan

### Phase 1 — Build upgrade (Spring Boot 3.5 + Spring AI)
**Goal:** modern, advisor-upgradeable baseline.
- Bump `spring-boot-starter-parent` → latest **3.5.x**; keep `<java.version>17</java.version>`.
- Add **Spring AI BOM** (`spring-ai-bom`) + `spring-ai-starter-model-openai` (Tanzu GenAI exposes an
  OpenAI-compatible endpoint), and `spring-cloud-bindings` for VCAP→Spring property mapping.
- Add `spring-boot-starter-validation`, and `com.h2database:h2` (runtime, `local` profile only).
- Keep `manifest.yml` JRE at 17 (matches Java 17 target).
- **Acceptance:** `./mvnw clean package` builds on Java 17; app boots with `local` profile.

### Phase 2 — Normalize data model + seed data
**Goal:** real multi-table source of truth.
- New JPA entities: `Customer` (`customers`), `CreditHistory` (`credit_history`),
  `CriminalRecord` (`criminal_records`), `CreditScore` (`credit_scores`).
- Repositories + a real join query (customer ⨝ latest credit_history ⨝ criminal_records) used by the
  calculator. Replace the artificial `Thread.sleep`-based "complex join" with the genuine query.
- Rewrite `DataLoader` to seed ~200 customers with coherent history + a subset with criminal records,
  so scores are reproducible and joinable.
- **Acceptance:** seeded tables visible; join returns aggregated risk inputs per SSN.

### Phase 3 — Distributed-transaction fix + CPU remediation
**Goal:** clear the Tanzu Hub alert and the CPU spikes together (they touch the same code).
- Introduce `CreditScoreCacheStore` interface; move all Valkey writes/reads behind it.
- In `CreditScoreCalculator`: `@Transactional` now wraps **only** Postgres reads/writes. The cache
  write happens **after** the transaction commits — either by calling the cache store *after* the
  transactional method returns, or via `TransactionSynchronizationManager` / an
  `@TransactionalEventListener(phase = AFTER_COMMIT)`. Rationale and pattern follow the Tanzu
  Postgres app-dev guide (single transaction scope owned by the database service).
- Remove `Thread.sleep(200)` from the listener path.
- Replace `findAll()`-based "all scores" with a bounded **top-10** structure in Valkey
  (sorted set keyed by `calculatedAt`, capped) read by `/api/scores/top`.
- Drop the heavy `@RedisHash` secondary-index repository in favor of explicit keyed access + the
  top-N structure. Apply **TTL** (from admin settings) on every cache write.
- Set `ddl-auto: validate` (or `none` with schema init) and `show-sql: false` for `cloud`; tune
  `spring.rabbitmq.listener.simple.concurrency` sensibly.
- **Acceptance:** no code path opens a non-DB resource inside a DB transaction; `/api/scores/top`
  issues O(log N) cache ops, not `KEYS`; listener no longer sleeps.

### Phase 4 — GenAI assistant (Spring AI tool-calling)
**Goal:** working natural-language querying via `credit-chat`.
- `AiConfig` builds a `ChatClient` from the OpenAI-compatible `credit-chat` binding.
- `CreditTools` exposes typed `@Tool` methods: `topScores(limit)`, `scoresInLastMinutes(minutes, limit)`,
  `filterByRiskLevel(level)`, `scoreForSsn(ssn)`. The LLM selects tools/params — **no raw SQL from the
  model**, so no injection surface.
- `CreditAiService` wires the prompt + tools; `AiController.POST /api/ai/query` returns structured
  rows for the existing table UI.
- `local` profile uses a deterministic stub responder so the panel works without a GenAI binding.
- **Acceptance:** "top 10 credit scores from the past 15 minutes" returns the right rows on `cloud`;
  stub answers on `local`.

### Phase 5 — Messaging abstraction, frontend, live local preview
**Goal:** standalone-runnable UI + the live "digital assistant" preview.
- `ScoreRequestPublisher` interface: `RabbitPublisher` (`cloud`) / `DirectPublisher` (`local`,
  processes synchronously in-process).
- `local` profile config: H2 datasource, in-memory cache store, direct publisher, stub AI — zero
  external services.
- Update `index.html` to the bounded `/api/scores/top` endpoint and keep header metrics + manual
  refresh; fix risk-level label mismatches.
- Launch the app under `local` and use the **Claude live preview** to show the UI updating as changes
  land (this is the "digital assistant that shows changes live in a local version").
- **Acceptance:** `mvnw spring-boot:run -Dspring-boot.run.profiles=local` serves a fully working UI
  with no Docker/services; preview renders it.

### Phase 6 — Admin portal + eviction rules
**Goal:** operator-configurable eviction and runtime settings.
- `SettingsService` holds runtime config (cache TTL seconds, top-N size, auto-refresh interval,
  optional AI on/off). Backed by Postgres so settings survive restarts; defaults seeded.
- `AdminController` (`/admin`, `GET/POST /admin/settings`) + `admin.html` form.
- Cache store reads TTL from `SettingsService` on every write so changes take effect immediately.
- **Acceptance:** changing TTL in the portal changes the effective Valkey expiry for new entries.

---

## 4. Files added / changed (running list)

_Updated as work proceeds._

- `pom.xml` — Spring Boot 3.5, Spring AI BOM, validation, H2.
- `src/main/resources/application.yml` — `default` / `cloud` / `local` profiles.
- `entity/` — `Customer`, `CreditHistory`, `CriminalRecord`, `CreditScore`.
- `repository/` — per-entity repos + join query.
- `cache/` — `CreditScoreCacheStore` (+ Valkey / in-memory impls).
- `messaging/` — `ScoreRequestPublisher` (+ Rabbit / direct impls), updated listener.
- `service/` — refactored `CreditScoreCalculator`, `MetricsService`, new `SettingsService`, `CreditAiService`.
- `ai/CreditTools.java`, `config/AiConfig.java`.
- `controller/` — updated `CreditApplicationController`, new `AdminController`, real `AiController`.
- `bootstrap/DataLoader.java` — multi-table seeding.
- `static/index.html`, `static/admin.html`.

---

## 5. Risks & mitigations

| Risk | Mitigation |
|------|------------|
| Tanzu GenAI binding shape differs from plain OpenAI | Use `spring-cloud-bindings` + property overrides; `local` stub keeps UI working regardless |
| H2 vs Postgres SQL dialect drift in `local` | Keep queries JPQL/derived where possible; Postgres-specific SQL guarded to `cloud` |
| Cache/DB divergence after the cache-aside split | Reads fall back to Postgres on cache miss; TTL bounds staleness |
| Spring App Advisor friction | No hard-pinned dependency versions outside the managed BOMs |

---

## 6. Validation strategy

- Build on Java 17 (`./mvnw clean package`).
- Boot `local`; exercise submit → top-10 → metrics → AI panel → admin TTL change via the live preview.
- Confirm no DB transaction encloses a Valkey/Rabbit call (code review + grep for cache calls inside
  `@Transactional` methods).
- Smoke-test `cloud`-profile wiring assumptions against `manifest.yml` service names.
