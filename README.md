# Online Banking Dashboard System (Demo)

> **Demo banking — fictional money.** This is a portfolio-quality banking
> simulator. No real money, real bank accounts, UPI, cards, loans, or
> identity documents are involved anywhere in this system, and it makes no
> claim of regulatory compliance.

Two roles: **Customer** (accounts, transfers, statements, bill pay, savings
goals, budgets, support) and **Administrator** (customer/account inspection,
freeze/unfreeze, alerts, support responses, audit log). Full architecture
and rationale: [CLAUDE.md](CLAUDE.md). Honest, continuously-updated record
of what's actually done vs. pending: [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md).

## Quick start

```powershell
# 1. Copy the environment template and fill in local secrets
Copy-Item .env.example .env
# edit .env: set MYSQL_PASSWORD, MYSQL_ROOT_PASSWORD, DB_PASSWORD (same value),
# and ADMIN_BOOTSTRAP_PASSWORD (>=12 characters)

# 2. Build and start everything
docker compose up --build
```

Then open **http://localhost:8080** — one container serves both the API and
the React app on the same origin. First startup runs all Flyway migrations
and bootstraps the one admin account from `ADMIN_BOOTSTRAP_*` (see
[docs/admin-bootstrap.md](docs/admin-bootstrap.md)).

Bash/macOS/Linux equivalent: `cp .env.example .env` then the same
`docker compose up --build`.

## Local development (frontend + backend separately, with hot reload)

```powershell
# Terminal 1: MySQL only
docker compose up mysql

# Terminal 2: backend (reads the "local" Spring profile -> localhost:3306... but
# docker-compose's mysql binds to 127.0.0.1:3307 by default -- override the port:
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Dspring.datasource.url=jdbc:mysql://localhost:3307/banking_demo?serverTimezone=UTC&sslMode=DISABLED&allowPublicKeyRetrieval=true"

# Terminal 3: frontend (Vite dev server, proxies /api and /actuator to :8080)
cd frontend
npm install
npm run dev
```

Open the URL Vite prints (typically http://localhost:5173). The dev proxy in
`frontend/vite.config.ts` keeps the browser talking to a single origin, so
cookies/CSRF behave exactly as they do in production.

> **Note on this environment's local dev quirk:** during development we hit
> a Maven incremental-compilation issue on this OneDrive-synced Windows
> checkout where `spring-boot:run` occasionally picked up a stale/incomplete
> `target/classes` (missing a nested class, or even the main class). Running
> `.\mvnw.cmd clean compile` (or `clean package`) immediately before starting
> the dev server reliably avoids it. This never affects the Docker build,
> which always compiles from a clean checkout inside the image.

## Environment variables

Full reference with defaults and explanations: [.env.example](.env.example).
Highlights:

| Variable | Purpose |
|---|---|
| `ADMIN_BOOTSTRAP_USERNAME/EMAIL/PASSWORD` | Creates the **one** admin account on first startup only. No default admin exists. |
| `DB_HOST/PORT/NAME/USER/PASSWORD`, `DB_SSL_MODE` | Datasource connection; `DB_SSL_MODE=VERIFY_IDENTITY` + `DB_SSL_CA_PEM` for a hosted MySQL requiring TLS (see [docs/deployment.md](docs/deployment.md)). |
| `COOKIE_SECURE`, `COOKIE_SAME_SITE` | Session cookie flags — `COOKIE_SECURE=true` requires HTTPS (always true in any real deployment). |
| `DEMO_MAX_FUNDING_AMOUNT`, `DEMO_MAX_TRANSFER_AMOUNT`, `DEMO_DAILY_TRANSFER_LIMIT` | Configurable demo money limits. |
| `RATE_LIMIT_*`, `TRUST_PROXY_HEADERS` | Bounded in-process abuse protection; only trust `X-Forwarded-For` behind a real reverse proxy. |
| `BACKUP_GPG_PASSPHRASE` | Only used interactively by `scripts/backup.sh`/`restore.sh`, never read by the app itself. |

## Testing

```powershell
# Backend: JUnit + real MySQL via Testcontainers (needs Docker running)
cd backend; .\mvnw.cmd test

# Frontend: Vitest + React Testing Library
cd frontend; npm test

# Frontend build/type-check
cd frontend; npm run build

# E2E: Playwright against the packaged Docker image (not the dev server)
docker compose up --build -d
cd e2e; npm install; npx playwright install chromium
$env:PLAYWRIGHT_BASE_URL = "http://localhost:8080"; $env:E2E_TARGET = "packaged"
npx playwright test
```

**Actual results observed locally** (exact suite names/counts are tracked in
`IMPLEMENTATION_STATUS.md`, since they grow as the code does):
- Backend: `LedgerServiceIntegrationTest` (11 tests) and
  `RecoveryServiceIntegrationTest` (4 tests) all pass against real MySQL 8.0
  via Testcontainers.
- Frontend: 24/24 unit tests pass; `npm run build` is clean.
- E2E: 9/9 Playwright tests pass against the packaged Docker image with a
  real MySQL 8 container (two real browser contexts proving a live
  cross-account SSE balance update, concurrent-duplicate-idempotency-key
  safety with real CSRF, ownership denial, and SPA/static-asset routing).
- CI (GitHub Actions) is configured (`.github/workflows/ci.yml`) but **not
  yet observed running** — this repository has no remote and hasn't been
  pushed. Do not treat anything as "verified in CI" until a real run has
  been watched on the exact commit.

## Repository layout

```
backend/    Spring Boot (Java 17) API, Flyway migrations, backend tests
frontend/   React 19 + TypeScript + Vite SPA
e2e/        Playwright end-to-end tests against the packaged app
scripts/    Docker entrypoint, backup/restore helper scripts
docs/       Architecture, deployment, admin-bootstrap, backup/restore docs
.github/workflows/  CI (backend tests, frontend build/tests, packaged E2E)
```

## Known gaps and unverified behavior

- **Deployment has not been performed.** `docs/deployment.md` documents a
  verified-against-current-docs plan for Render (free web service) + Aiven
  (free MySQL), and exactly which external account actions only a human can
  take. No live URL exists yet.
- **CI has not been observed running** (no remote/push yet — see above).
- Admin has no self-service password reset; see
  `docs/admin-bootstrap.md` for the intentional manual procedure.
- In-memory sessions mean any restart (including a Render free-tier
  spin-down) signs everyone out — documented behavior, not a bug; banking
  data itself is unaffected since it lives entirely in MySQL.
- Rate limiting and the three alert rules are process-local and in-memory —
  they reset on restart and aren't shared across instances. Fine for this
  single-instance demo; not a durable security guarantee.
- Firefox/WebKit/mobile Playwright projects are defined but commented out
  in `e2e/playwright.config.ts` (only Chromium runs by default) — enable
  them if you want to reproduce those checks locally.

## Backup/restore

See [docs/backup-restore.md](docs/backup-restore.md) — includes a real,
already-executed verification run (not just the procedure).

## Deployment

See [docs/deployment.md](docs/deployment.md).

## Architecture

See [docs/architecture.md](docs/architecture.md) and [CLAUDE.md](CLAUDE.md).
