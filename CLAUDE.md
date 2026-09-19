# CLAUDE.md — Online Banking Dashboard System

Guidance for any Claude Code session working in this repository.

## What this project is

A portfolio-quality **fictional-money** banking simulator. Two roles: Customer and
Administrator. INR only, all balances are simulated. No real banking networks, UPI,
card issuance, lending, or identity-document collection. Never claim regulatory
compliance. All hosting must stay ₹0 — no paid tiers, no trial credits.

Full spec lived in the original build prompt; the authoritative running record of
what is actually done is [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) — read
that before assuming any feature is complete.

## Architecture (pinned decisions — do not change without updating this file)

- **Backend**: Java 17, Spring Boot 4.1.1 (pulls Spring Security 7.1.1,
  Hibernate 7.4.5 — verified by `mvn dependency:tree`, not assumed; see
  IMPLEMENTATION_STATUS.md "Notable environment-driven adaptations" for the
  API differences this required, e.g. Jackson 3's `tools.jackson.databind`
  package and `DaoAuthenticationProvider`'s constructor signature), Spring Data JPA,
  Maven (with `mvnw` wrapper committed). Modular monolith — packages by feature
  under `com.bankingdemo`, no microservices/Kafka/Redis/Kubernetes.
- **Database**: MySQL 8.0, Flyway forward-only migrations under
  `backend/src/main/resources/db/migration`. `ddl-auto=validate` always — Flyway is
  the only source of schema truth.
- **Auth**: server-side in-memory HTTP sessions (single instance), Spring Security
  form-based session auth, CSRF via cookie (`XSRF-TOKEN`, JS-readable) + header
  (`X-XSRF-TOKEN`), no JWT, no tokens in localStorage. Session cookie is
  HttpOnly + Secure (prod) + SameSite=Lax. Restarting the app signs everyone out
  (documented, not a bug) but never deletes banking data.
- **Money**: `BigDecimal` end-to-end, MySQL `DECIMAL(19,2)`, INR whole currency
  with paise precision. Never `double`/`float` for authoritative amounts.
- **Ledger**: append-only, balanced double-entry. Every completed movement has a
  unique reference; transfers = linked debit+credit; deposits/withdrawals post
  against dedicated system counterpart accounts (`SYSTEM_CASH`, etc.) that are
  excluded from recipient search and customer totals. No UPDATE/DELETE on posted
  ledger rows — corrections are new linked entries. One deliberate, narrow
  exception: `ledger_entries.category_id` is non-financial metadata the
  customer may change (it was already settable at transaction time); every
  change is recorded in the append-only `ledger_entry_category_audit` (V9).
  Amount, direction, balance_after and timestamps are never updated.
- **Idempotency**: dedicated `idempotency_keys` table, unique on
  `(customer_id, operation_type, idempotency_key)`, storing a canonical request
  fingerprint hash + the resulting operation reference + response snapshot.
  Enforced via DB unique constraint + transaction, never an in-memory map.
- **Concurrency**: pessimistic row locks (`SELECT ... FOR UPDATE` via
  `@Lock(PESSIMISTIC_WRITE)`) on accounts, acquired in a consistent order
  (lower account id first) for any operation touching two accounts, to prevent
  deadlocks and lost updates.
- **Real-time**: Server-Sent Events (`/api/events/stream`), authenticated via the
  existing session cookie (no token in URL). Notifications are persisted to
  `notifications` table *before* publish; SSE is a hint to refetch/refresh via
  TanStack Query, never the source of truth for balances.
- **Frontend**: React 19 + TypeScript + Vite, React Router 7, TanStack Query 5.
  Dev proxy (`vite.config.ts`) forwards `/api` to the backend so the browser only
  ever talks to one origin — this is required for cookie/CSRF behavior to match
  prod (same-origin).
- **Packaging**: multi-stage Dockerfile — stage 1 builds the frontend, stage 2
  builds the backend, stage 3 is a slim JRE image containing the Spring Boot jar
  with the built frontend copied into `src/main/resources/static` (or served via
  a static resource handler) so one process serves both API and SPA.
- **Testing**: JUnit5 + Spring Boot Test for unit/service tests; Testcontainers
  (real MySQL in Docker) for repository/concurrency/integration tests — never
  H2 in place of MySQL for anything claiming DB-behavior coverage; Vitest +
  React Testing Library for frontend units; Playwright against the packaged
  Docker image + real MySQL for E2E.
- **Deployment target**: Render free web service (Docker) + Aiven MySQL free
  tier, pending verification of current free-tier terms at deploy time (see
  IMPLEMENTATION_STATUS.md for verification status — do not assume it's still
  free without checking).

## Optional AI, unusual-activity checks and insights

- **AI is optional and never authoritative.** `com.bankingdemo.ai`: Groq (free
  plan, `openai/gpt-oss-20b`) behind an `AiChatClient` interface. Blank
  `GROQ_API_KEY` disables it; every AI path has a labelled deterministic
  fallback ("Calculated answer"). The model has no tools and no authority to
  move money, change balances, freeze accounts or run SQL. Model output is
  verified against backend-computed context (`AssistantAnswerValidator`) and
  financial facts shown to users come from backend fields, not model text.
  Never send passwords, recovery codes, session tokens, full account numbers or
  names/emails to a model; never log prompts, responses or keys. Details and the
  dated free-plan verification: [docs/ai-features.md](docs/ai-features.md).
- **Insights, unusual-activity flags and forecasts are pure backend statistics**
  (`com.bankingdemo.insights`, `alert.UnusualActivityDetector`) with no model.
  They extend the existing `alert_rules`/`account_alerts` tables. They are
  labelled "statistical check"/"rule", never "AI fraud detection", and may only
  notify: never freeze, block or move money. Baselines use only
  pre-transaction data; alert writes are `INSERT IGNORE` and errors are
  swallowed so an alert can never fail a transfer. Method, thresholds and
  synthetic held-out results: [docs/insights.md](docs/insights.md).
- Time-dependent logic takes the injectable `java.time.Clock` bean.
- Do not present synthetic-fixture results as real-user accuracy, and do not
  show confidence percentages that were not computed.
- The UI keeps SecureBank branding and a *discreet* fictional-funds note (login,
  register, footer, About page) — no large demo banners.

## Working conventions

- Every financial-mutating endpoint requires an `Idempotency-Key` header from
  the client; the frontend must persist the in-flight key (customer-scoped,
  non-secret) across refresh until the outcome is known.
- Every service method that touches money must run inside a single
  `@Transactional` boundary that includes: balance mutation, ledger rows,
  idempotency record, and notification record. No partial commits.
- Admins never move money directly. Only freeze/unfreeze with a reason, and
  respond to support tickets.
- New Flyway migrations are always additive/forward-only — never edit a
  migration that has already been committed and could have run.
- When adding a feature, update IMPLEMENTATION_STATUS.md in the same commit.
- Commit at meaningful milestones on `feature/initial-build`; do not force-push
  or rewrite history; do not invent/push to a remote without explicit
  authorization.

## Local commands (PowerShell-first; bash equivalents in README.md)

```powershell
# Full stack via Docker
docker compose up --build

# Backend only (dev)
cd backend; .\mvnw.cmd spring-boot:run

# Frontend only (dev, proxies /api to localhost:8080)
cd frontend; npm install; npm run dev

# Backend tests (spins up MySQL via Testcontainers — needs Docker running).
# Always `clean`: on this OneDrive checkout incremental compiles can lose classes.
cd backend; .\mvnw.cmd clean test

# Frontend unit tests
cd frontend; npm test

# E2E (Playwright, against packaged Docker image)
cd e2e; npm install; npx playwright test
```
