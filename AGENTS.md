# AGENTS.md — working instructions for this repository

Conventions any agent (or human) must follow when changing this project. Read this
before making edits, and keep it up to date when the rules change.

Project: **Credit Engine v2** — Spring Boot 3.5 app on Tanzu Platform for
Cloud Foundry. See [README.md](README.md) for architecture and [IMPLEMENTATION.md](IMPLEMENTATION.md)
for the refactor plan/rationale.

---

## 1. Versioning — REQUIRED on every update

Every meaningful change must be committed, pushed, and (for milestones) tagged so we can
always roll back. Nothing is "done" until it is on GitHub.

**After each change:**
```bash
git add -A
git commit -m "Clear description of what changed"
git push
```

**At each milestone (feature, fix set, or release), create a restore point:**
```bash
git tag -a v2.1.0 -m "What this version is"   # annotated tag
git push origin v2.1.0
```

Use **semantic versioning**: `vMAJOR.MINOR.PATCH`
- PATCH (`v2.0.1`) — small fixes, no behavior change
- MINOR (`v2.1.0`) — new features, backward compatible
- MAJOR (`v3.0.0`) — breaking changes

Current baseline: **v2.0.0** (the refactored engine). List versions: `git tag -n1`.

**Rolling back:**
- Inspect an old version (safe): `git checkout v2.0.0` … `git checkout main`
- Undo but keep history (preferred): `git revert <commit> && git push`
- Hard reset (rewrites history, use with care): `git reset --hard v2.0.0 && git push --force`

---

## 2. Build & run (local toolchain)

- The app requires **JDK 17** (Spring Boot 3.5). The default `java` on this machine is
  **JDK 11** and cannot build it. On this machine JDK 17 lives at
  `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` — set `JAVA_HOME` to a JDK 17
  before building.
- Use **system Maven** (`mvn`), not `mvnw` — the Maven wrapper is missing
  `.mvn/wrapper/maven-wrapper.properties` and does not work.
- Build: `mvn -DskipTests package` (with `JAVA_HOME` = JDK 17). Produces `target/credit-engine-v2.jar`.
- Run locally for frontend inspection (no external services needed):
  `java -jar target/credit-engine-v2.jar --spring.profiles.active=local`
- `server.port` honors the `PORT` env var (also correct for Cloud Foundry).
- Deploy to Tanzu: `cf push` (uses [manifest.yml](manifest.yml)).

---

## 3. Architecture invariants — DO NOT regress these

These were the point of the refactor. Preserve them in every change.

1. **No distributed transactions.** `@Transactional` methods touch **PostgreSQL only**.
   Never call Valkey/Redis or RabbitMQ inside a DB transaction. Cache writes are
   cache-aside, performed **after** the DB transaction commits
   (see `CreditScoreCalculator` → `ScorePersistenceService`).
2. **No unbounded cache access.** Never use Redis `KEYS`/`findAll()`/`@RedisHash` repository
   scans. Score lookups are single keyed GETs with a TTL; "recent scores" is the bounded
   top-N sorted set in `ValkeyCacheStore`.
3. **No blocking on listener/hot-path threads** (no `Thread.sleep`, no busy work) — it
   caused the CPU spikes.
4. **Profile split via interfaces.** Backing services are abstracted behind interfaces with
   `@Profile("cloud")` real impls and `@Profile("local")` in-memory impls
   (`CreditScoreCacheStore`, `ScoreRequestPublisher`, `AiQueryService`). The `local` profile
   must keep running with **no external services** (H2 + in-memory + stub AI).
5. **GenAI = Spring AI tool-calling only.** The model selects typed `@Tool` methods
   (`CreditTools`); it must **never** generate or execute raw SQL.
6. **Spring App Advisor friendly.** Stay on Spring Boot **3.5.x** managed BOM versions and
   the Spring AI BOM. Do not hard-pin dependency versions that the BOMs manage.
7. **Cloud service binding.** Do **not** bundle `spring-cloud-bindings` in `pom.xml` — the
   Tanzu Java buildpack injects it; a second copy crashes the app at startup. Let the
   buildpack binding auto-configure `spring.datasource` / `spring.data.redis` /
   `spring.rabbitmq` from `VCAP_SERVICES` (the Postgres binding field is `user`, not
   `username`, and `hosts` is an array — another reason not to hand-map it). Only the GenAI
   (`credit-chat`) binding is mapped explicitly in the `cloud` profile.

---

## 4. When you change things, also…

- **Frontend:** static files are served from the packaged jar — **rebuild** to see changes.
  Keep the JS↔HTML contract: preserve element IDs (`totalApps`, `avgValkey`, `scoresTableBody`,
  `aiQuery`, `applicationForm`, etc.) and the `risk-*` / `score-*` class names.
- **Docs:** if you change architecture, data model, endpoints, or services, update
  [README.md](README.md) (incl. the diagrams) and [IMPLEMENTATION.md](IMPLEMENTATION.md).
- **Secrets:** never commit credentials. GenAI/DB/cache/broker credentials come from the
  Cloud Foundry service bindings (`VCAP_SERVICES`) in the `cloud` profile.
- **.gitignore:** keep `target/`, `.claude/`, and `desktop.ini` ignored. Do not commit the
  77MB build jar or local editor config.
- **Verify before declaring done:** build with JDK 17, run the `local` profile, smoke-test
  the endpoints, and check the UI in the preview.

---

## 5. Keep this file current

If any of the above changes (new invariant, new build step, new convention), update this
file in the same commit so future updates respect it.
