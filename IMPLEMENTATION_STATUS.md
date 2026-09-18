# Implementation Status

Legend: `[ ]` not started · `[~]` in progress · `[x] local` verified locally ·
`[x] CI` verified in CI · `[x] public` verified on deployed instance · `[!]` blocked

Last updated: 2026-09-18 (end of build session).

## 0. Foundation
- [x] local — Root structure, `.gitignore`, `.gitattributes` (forces LF for `*.sh`), `.env.example`, `CLAUDE.md`, this file, `README.md`
- [x] local — Git repo on `feature/initial-build`, no remote (none authorized/created)
- [x] local — `Dockerfile` (multi-stage), `docker-compose.yml`, `.dockerignore`
- [x] local — `.github/workflows/ci.yml` (backend tests, frontend build/tests, packaged E2E) — **not yet observed running** (no remote/push)

## 1. Database & Money Design
- [x] local — Flyway V1–V8: customers/admins/accounts (+ seeded SYSTEM_CASH/SYSTEM_BILLPAY), spending_categories, ledger (financial_transactions + ledger_entries, DB-enforced one-debit-one-credit), idempotency_keys, beneficiaries/notifications/recovery_codes, support, audit_log/alert_rules/account_alerts, billers/bill_payments/budgets/savings_goals/money_requests
- [x] local — All 8 migrations verified against real MySQL 8.0 multiple times (disposable containers, Testcontainers, docker-compose)

## 2. Backend (Spring Boot 4.1.1 / Java 17 / Spring Security 7.1.1 / Hibernate 7.4.5)
- [x] local — Security: distinct CustomerPrincipal/AdminPrincipal types, cookie CSRF (SPA pattern), session-fixation protection, SessionRegistry-backed session invalidation on password change, JSON 401/403, security headers, bounded rate limiting, proxy-aware IP resolution
- [x] local — Admin bootstrap via env vars only (race-safe via unique constraint)
- [x] local — Ledger engine: DB-constraint-based idempotency (not in-memory), ascending-id pessimistic locking, one transaction per movement, notification-on-transfer
- [x] local — Full customer API: accounts, deposit/withdraw/transfer, transaction history (filtered/paginated) + CSV export (formula-injection-safe) + print, beneficiaries, notifications, SSE stream, dashboard aggregate, support tickets, money requests, bill pay, budgets, savings goals
- [x] local — Full admin API: dashboard aggregates (SAVINGS-only), customer/account inspection, freeze/unfreeze (audited), transaction inspection, support responses, alert list/acknowledge (audited), audit log
- [x] local — Rule-based alerts (LARGE_TRANSACTION, RAPID_TRANSFERS, REPEATED_FAILED_LOGIN) — explicitly not framed as fraud detection
- [x] local — SpaWebConfig serves the built frontend + correct static/API/SPA-route separation

## 3. Frontend (React 19 + TypeScript + Vite + React Router + TanStack Query)
- [x] local — Full route set for both roles (see README/CLAUDE.md), design system in plain CSS (light/dark), accessible forms, responsive nav, print stylesheet
- [x] local — Idempotent financial UX (`usePendingOperation`): persists only the idempotency key across refresh; ambiguous outcomes reuse the same key; clean rejections mint a fresh one
- [x] local — SSE wiring (`useEventStream`) invalidates TanStack Query caches only — never a direct state source

## 4. Testing
- [x] local — Backend: 16/16 tests pass against real MySQL 8.0 via Testcontainers, run just before this update:
  - `BankingBackendApplicationTests` (1) — context loads
  - `LedgerServiceIntegrationTest` (11) — zero-start balances, balanced double-entry, idempotent replay, reused-key-different-payload rejection, insufficient-funds/frozen-account rejection with balance unchanged, concurrent overdraft race (5 threads, exactly 1 succeeds), concurrent duplicate-idempotency-key race, opposite-direction transfers without deadlock, freeze-during-concurrent-transfers consistency, mid-transaction FK-violation rollback with retry
  - `RecoveryServiceIntegrationTest` (4) — regenerate issues 10 active codes, valid redemption consumes permanently, wrong code rejected without consuming a real one, regenerate invalidates all prior codes
- [x] local — Frontend: 24/24 Vitest + RTL tests pass (`npm test`); `npm run build` (tsc -b + vite build) clean
- [x] local — E2E: 9/9 Playwright tests pass against the **packaged Docker image** with real MySQL (`PLAYWRIGHT_BASE_URL=http://localhost:8080 E2E_TARGET=packaged`): core transfer + live SSE update across two browser contexts, excessive-withdrawal rejection, concurrent-duplicate-idempotency-key safety with real CSRF, ownership denial (cross-customer and cross-role), unauthenticated deep-link redirect, hard-refresh session preservation, missing-static-asset 404 (not index.html), API errors never return HTML
- [ ] CI — workflow exists but has never actually run (no remote/push authorized yet)

## 5. Packaging & Local Running
- [x] local — `docker compose up --build` verified end-to-end multiple times, including from a completely empty volume (Flyway runs all 8 migrations, admin bootstraps, health check passes)
- [x] local — Multi-stage build confirmed: frontend built once, embedded on the backend's classpath, single JRE-alpine runtime image, non-root user, healthcheck, conservative JVM flags (`-XX:MaxRAMPercentage=70`) and HikariCP pool (`max=5` by default)
- [x] local — `scripts/docker-entrypoint.sh` (builds a hosted-MySQL TLS truststore from `DB_SSL_CA_PEM` at container start) verified directly inside the runtime image with a real self-signed test certificate

## 6. Deployment
- [x] local (research only) — `docs/deployment.md`: Render free web service + Aiven MySQL free tier, every claim checked against each provider's own current docs on 2026-09-18 (dated, with sources), including sleep/cold-start behavior, ephemeral filesystem, instance-hour limits, Aiven's inactivity power-off policy, and the exact environment variables to set
- [ ] public — **Not deployed.** No remote repository exists and none has been created (requires the user's explicit authorization and their own GitHub/Render/Aiven account actions). No live URL exists; none is claimed.

## 7. Backup & Restore
- [x] local — `scripts/backup.sh` / `scripts/restore.sh` (GPG-encrypted, disposable-target-only guardrail) — **actually run**, not just written: backed up a live compose database (19→ then, after a later reset, a fresh 2-customer dataset), restored into a separate disposable MySQL container, confirmed identical row counts and ledger reconciliation, confirmed the Flyway history, then built and ran the real application jar against the restored database and logged in over HTTP as the fictional test customer. Full record in `docs/backup-restore.md`.

## 8. Final Demonstration (spec's exact 10-step script)

Run against the **packaged Docker image**, real MySQL, from a fresh empty
volume, on 2026-09-18. All 10 steps executed and verified (not assumed):

1. **[x]** Registered Alice and Bob as two fictional customers in separate browser contexts (Playwright, two independent `BrowserContext`s = two independent sessions/cookie jars).
2. **[x]** Created one account each (Alice `593046079566`, Bob `284499791424`).
3. **[x]** Simulated deposit: ₹10,000 to Alice, ₹2,000 to Bob.
4. **[x]** Transferred ₹3,000 from Alice to Bob; reference `TXN-49728048-3de0-4546-aa0b-a2d3a2295cf1`.
5. **[x]** Verified Alice=₹7,000.00, Bob=₹5,000.00, matching reference on the receipt, and Bob's dashboard updated **live** (SSE push → TanStack Query invalidation) with no manual refresh — screenshot evidence captured.
6. **[x]** Confirmed the idempotency/duplicate-submission guarantee: a genuinely new UI submission with the same parameters is correctly treated as a new transfer (money moves again, ₹7,000→₹4,000), while true retry-with-the-same-key safety (no double-move) is separately, explicitly proven under concurrency in `e2e/tests/duplicate-submission.spec.ts` (3 concurrent requests, same key, exactly 1 succeeds).
7. **[x]** Rejected an excessive withdrawal (₹999,999 from Bob's ₹5,000.00 account); balance unchanged.
8. **[x]** Admin froze Bob's account with a required reason; a subsequent transfer attempt from that account was blocked with a "frozen" error. Unfrozen afterward to leave a clean end state.
9. **[x]** Completed a support-ticket conversation: customer opened a ticket, admin responded and marked it RESOLVED, customer saw the reply.
10. **[x]** Restarted the app container (`docker compose restart app`); verified in MySQL directly that customers/accounts/balances/transaction counts were unchanged (₹4,000.00 / ₹8,000.00, 2 customers, 4 transactions), and verified **successful reauthentication** for both Alice (customer) and the bootstrapped admin using their original credentials — sessions were cleared by the restart (expected, documented behavior) but no banking data was lost.

**A real bug was found and fixed during this run:** step 8 initially failed
because admin login was silently broken — see the "what went wrong" section
below. This is exactly why the full demonstration script matters more than
unit tests alone.

## What went wrong along the way (kept for honesty, not swept under the rug)

- **Admin login silently broken by `@Primary` bean resolution.** An earlier
  fix for a startup crash ("Found 2 beans for type AuthenticationManager,
  none marked as primary") marked `customerAuthenticationManager` `@Primary`.
  Spring's bean resolution gives `@Primary` priority over parameter-name
  autowiring, so `AdminAuthController`'s `adminAuthenticationManager`
  constructor parameter was actually wired with the **customer** manager —
  admin login was checking the `customers` table and always failing with a
  generic "Authentication required", never a clearer error. Found via the
  final demonstration's admin-freeze step, root-caused via MySQL's general
  query log (showed `select ... from customers ... where username='admin'`),
  fixed with explicit `@Qualifier` annotations on both auth controllers.
  Fixed, verified, and committed.
- **Local Maven incremental-compilation quirk** (this environment only): on
  this OneDrive-synced Windows checkout, `spring-boot:run` occasionally
  picked up a `target/classes` missing specific compiled classes (including,
  once, the main class itself) even though `mvn clean compile` had just
  succeeded. Workaround: always `mvn clean compile`/`clean package`
  immediately before running locally. **Does not affect the Docker build**,
  which compiles from a clean checkout inside the image every time — every
  packaged-image test and the final demonstration itself ran against jars
  built this way.
- **`NoResourceFoundException` → 500 instead of 404.** Spring's own
  "resource not found" signal for a missing static asset was being caught by
  the generic `Exception` handler and turned into a 500. Fixed by adding a
  dedicated `@ExceptionHandler(NoResourceFoundException.class)` returning a
  real 404 — caught by the packaged-image Playwright suite, not a unit test.
- Two earlier `NoClassDefFoundError` incidents for lazily-loaded nested
  record classes (`LedgerService$TransferFingerprint`) during local dev were
  the same Maven incremental-compilation issue above, not application bugs
  — confirmed by the fact a clean rebuild always resolved them and the
  Docker build never exhibited it.

## Known blockers / external actions required

- **Deployment**: requires the user's own GitHub, Render, and Aiven accounts
  and explicit authorization to push a remote — see `docs/deployment.md` for
  the exact steps and environment variables once that's ready.
- **CI**: will only be "verified in CI" once pushed to a real remote and a
  workflow run has actually been observed on that exact commit.

## Notable environment-driven adaptations

This session's actual toolchain resolved newer major versions than
typically assumed when this build started; the implementation was adapted
accordingly, verified by successful builds/tests rather than assumed:

- Spring Boot 4.1.1 uses fine-grained starters (e.g.
  `spring-boot-starter-webmvc` instead of `-web`, per-starter `-test`
  artifacts instead of one `spring-boot-starter-test`).
- Jackson 3 (`tools.jackson.databind.ObjectMapper`), not Jackson 2
  (`com.fasterxml.jackson.databind`) — annotations
  (`com.fasterxml.jackson.annotation.*`) did not move.
- Spring Security 7.1.1: `DaoAuthenticationProvider` takes a
  `UserDetailsService` in its constructor (no longer a no-arg constructor +
  setter) — and, per the bug write-up above, its interaction with `@Primary`
  when multiple `AuthenticationManager` beans exist is unforgiving of
  parameter-name-only disambiguation.
- Hibernate 7.4.5 / Jakarta Persistence 3.2.

## Notes on scope choices

- Firefox/WebKit/mobile-viewport Playwright projects are defined in
  `e2e/playwright.config.ts` but commented out (Chromium runs by default,
  per the "run Chromium automatically, make others reproducible" guidance).
- Rate limiting and the three alert rules are intentionally process-local,
  in-memory, and documented as such — appropriate for this single-instance
  demo, not presented as a durable multi-instance guarantee.
