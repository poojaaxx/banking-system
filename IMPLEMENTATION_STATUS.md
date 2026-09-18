# Implementation Status

Legend: `[ ]` not started · `[~]` in progress · `[x] local` verified locally ·
`[x] CI` verified in CI · `[x] public` verified on deployed instance · `[!]` blocked

Last updated: 2026-09-18.

## 0. Foundation
- [x] local — Environment inspected (Java 17, Maven 3.9.16, Node 24.14, npm 11.9, Git 2.53, Docker 29.7 + Compose v5.5.1, MySQL client 8.0.46 present)
- [x] local — Root structure scaffolded (backend/ frontend/ e2e/ scripts/ docs/ .github/workflows/)
- [x] local — Git initialized, working on `feature/initial-build`
- [x] local — Root docs (.gitignore, .env.example, CLAUDE.md, IMPLEMENTATION_STATUS.md, README.md skeleton)
- [ ] Dockerfile, docker-compose.yml, .dockerignore

## 1. Database & Money Design
- [x] local — Flyway V1: customers, admins, accounts (incl. seeded SYSTEM_CASH/SYSTEM_BILLPAY)
- [x] local — Flyway V2: spending_categories (seeded)
- [x] local — Flyway V3: ledger (financial_transactions, ledger_entries; DB-enforced one-debit-one-credit)
- [x] local — Flyway V4: idempotency_keys
- [x] local — Flyway V5: beneficiaries, notifications, recovery_codes
- [x] local — Flyway V6: support_tickets, support_messages
- [x] local — Flyway V7: audit_log, alert_rules (seeded), account_alerts
- [x] local — Flyway V8: billers (seeded), bill_payments, budgets, savings_goals, money_requests
- [x] local — All 8 migrations verified against a real disposable MySQL 8.0 container

## 2. Backend Core
- [x] local — Spring Boot 4.1.1 (Java 17) scaffold, application.yml profiles (local/docker/prod/test)
- [x] local — JPA entities + Spring Data repositories for every table (plain FK ids, no relationship mappings, by design)
- [x] local — Security: CustomerPrincipal/AdminPrincipal (distinct types, SecurityUtils-gated access),
      cookie-based CSRF, session-fixation protection, SessionRegistry-backed session invalidation on
      password change, JSON 401/403, security headers
- [x] local — AdminBootstrapRunner (env-var only, race-safe via unique constraint)
- [x] local — Bounded in-memory RateLimiter + proxy-aware ClientIpResolver
- [x] local — AccountService (creation at zero, customer-only closure, recipient lookup)
- [x] local — Ledger engine: IdempotencyService/IdempotencyKeyStore (DB-constraint-based, not in-memory),
      LedgerEngine (ascending-id pessimistic locking, balanced ledger writes, notification on transfer),
      LedgerService (deposit/withdraw/transfer with ownership + demo-limit checks)
- [x] local — Notification persistence + SSE fan-out infra (SseEventPublisher; bounded, commit-gated)
- [x] local — Customer auth: register/login/logout/session/change-password/recovery-login/regenerate
- [x] local — Admin auth: login
- [ ] Account/transfer/notification/beneficiary/statement REST controllers (DTOs wired to services above)
- [ ] SSE stream endpoint (/api/events/stream) wired to SecurityUtils + reconnect replay
- [ ] Support tickets (customer + admin sides)
- [ ] Admin endpoints (customer/account inspection, freeze/unfreeze, ticket response, alerts, audit)
- [ ] Additional features: request money, bill pay, budgets/categories, savings goals, alert-rule evaluation job

## 3. Frontend
- [ ] Vite + React + TS scaffold, router, TanStack Query, design system baseline
- [ ] Auth pages (register/login/logout/recovery/security settings)
- [ ] Customer dashboard, accounts, transfers, statements, notifications, support
- [ ] Admin layout + pages
- [ ] Real-time wiring (SSE -> query invalidation)

## 4. Testing
- [x] local — LedgerServiceIntegrationTest: 8 tests, real MySQL 8.0 via Testcontainers (not H2) --
      zero-start balances, balanced double-entry, idempotent replay, reused-key-different-payload
      rejection, insufficient-funds and frozen-account rejection (balance unchanged), concurrent
      overdraft race (5 threads, exactly 1 succeeds), concurrent duplicate-idempotency-key race
      (balance moves by exactly one transfer)
- [ ] Additional backend unit/integration tests (auth, ownership, admin restrictions, recovery,
      notifications, support, additional features) as those layers are built
- [ ] Frontend unit tests
- [ ] Playwright E2E against packaged image

## 5. Packaging, CI, Deployment
- [ ] Multi-stage Dockerfile + compose
- [ ] GitHub Actions CI
- [ ] Deployment research (Render + Aiven free tier verification)
- [ ] Deployment executed (pending explicit authorization + external account actions)

## 6. Docs, Backup, Final Demo
- [ ] Full README/docs pass (architecture, env vars, admin bootstrap/recovery, API invariants, test results, deployment, backup/restore, migration rollback limitations, known gaps)
- [ ] Backup/restore procedure + tested
- [ ] Final demonstration script executed and recorded

## Known blockers / external actions required
- None yet identified. Deployment will require the user to create/authenticate
  Render and Aiven accounts (cannot be done by the agent).

## Notable environment-driven adaptations
This session's actual toolchain resolved newer major versions than typically
assumed, and the implementation was adapted accordingly (verified by
successful builds/tests, not assumed):
- Spring Boot 4.1.1 uses fine-grained starters (e.g. `spring-boot-starter-webmvc`
  instead of `-web`, per-starter `-test` artifacts instead of one `spring-boot-starter-test`).
- Jackson 3 (`tools.jackson.databind.ObjectMapper`), not Jackson 2
  (`com.fasterxml.jackson.databind`) -- annotations (`com.fasterxml.jackson.annotation.*`)
  did not move.
- Spring Security 7.1.1: `DaoAuthenticationProvider` takes `UserDetailsService`
  in its constructor (no longer a no-arg constructor + setter).
- Hibernate 7.4.5 / Jakarta Persistence 3.2.

## Notes
This is a large build tackled in stages within one continuous session. Each
stage above is committed once its local checks pass. This file is updated in
the same commit as the feature it describes.
