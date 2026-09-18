# Implementation Status

Legend: `[ ]` not started · `[~]` in progress · `[x] local` verified locally ·
`[x] CI` verified in CI · `[x] public` verified on deployed instance · `[!]` blocked

Last updated: 2026-09-18 (session start).

## 0. Foundation
- [x] local — Environment inspected (Java 17, Maven 3.9.16, Node 24.14, npm 11.9, Git 2.53, Docker 29.7 + Compose v5.5.1, MySQL client 8.0.46 present)
- [x] local — Root structure scaffolded (backend/ frontend/ e2e/ scripts/ docs/ .github/workflows/)
- [x] local — Git initialized, working on `feature/initial-build`
- [~] Root docs (.gitignore, .env.example, CLAUDE.md, IMPLEMENTATION_STATUS.md) — this file in progress
- [ ] README.md
- [ ] Dockerfile, docker-compose.yml, .dockerignore

## 1. Database & Money Design
- [ ] Flyway migration V1: core schema (customers, admins, accounts, system accounts)
- [ ] Flyway migration: ledger (financial_transactions, ledger_entries)
- [ ] Flyway migration: idempotency_keys
- [ ] Flyway migration: beneficiaries, notifications, recovery_codes
- [ ] Flyway migration: support_tickets, support_messages
- [ ] Flyway migration: audit_log, account_alerts/alert_rules
- [ ] Flyway migration: request-money, bill payments/billers, budgets/categories, savings goals

## 2. Backend Core
- [ ] Spring Boot project scaffold (pom.xml, mvnw wrapper, application.yml profiles)
- [ ] Security config (session auth, CSRF, password encoder, admin/customer principal separation)
- [ ] Admin bootstrap via env vars
- [ ] Account domain + creation + closure
- [ ] Ledger engine (deposit/withdraw/transfer, locking, idempotency)
- [ ] Recipient lookup, beneficiaries
- [ ] Notifications (persisted) + SSE stream
- [ ] Support tickets
- [ ] Admin endpoints (inspection, freeze/unfreeze, ticket response, alerts, audit)
- [ ] Recovery codes (customer), admin recovery doc
- [ ] Rate limiting / abuse protection
- [ ] Additional features: request money, bill pay, budgets/categories, savings goals, alert rules

## 3. Frontend
- [ ] Vite + React + TS scaffold, router, TanStack Query, design system baseline
- [ ] Auth pages (register/login/logout/recovery/security settings)
- [ ] Customer dashboard, accounts, transfers, statements, notifications, support
- [ ] Admin layout + pages
- [ ] Real-time wiring (SSE -> query invalidation)

## 4. Testing
- [ ] Backend unit tests
- [ ] Backend Testcontainers integration + concurrency tests
- [ ] Frontend unit tests
- [ ] Playwright E2E against packaged image

## 5. Packaging, CI, Deployment
- [ ] Multi-stage Dockerfile + compose
- [ ] GitHub Actions CI
- [ ] Deployment research (Render + Aiven free tier verification)
- [ ] Deployment executed (pending explicit authorization + external account actions)

## 6. Docs, Backup, Final Demo
- [ ] Full README/docs pass
- [ ] Backup/restore procedure + tested
- [ ] Final demonstration script executed and recorded

## Known blockers / external actions required
- None yet identified. Deployment will require the user to create/authenticate
  Render and Aiven accounts (cannot be done by the agent).

## Notes
This is a large build tackled in stages within one continuous session. Each
stage above is committed once its local checks pass. This file is updated in
the same commit as the feature it describes.
