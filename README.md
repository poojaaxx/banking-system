# SecureBank — Online Banking Dashboard System

> **Fictional money.** This is a portfolio-quality banking
> simulator. No real money, real bank accounts, UPI, cards, loans, or
> identity documents are involved anywhere in this system, and it makes no
> claim of regulatory compliance.

Two roles: **Customer** (accounts, transfers, statements, bill pay, savings
goals, budgets, support, **Insights** with unusual-activity notes and
forecasts, and an optional read-only **assistant**) and **Administrator** (customer/account inspection,
freeze/unfreeze, alerts, support responses, audit log). Full architecture
and rationale: [CLAUDE.md](CLAUDE.md). Honest, continuously-updated record
of what's actually done vs. pending: [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md).

## Live instance

**https://securebank-app-k1a4.onrender.com** — Render free web service + Aiven
free MySQL, deployed from commit `2f3294c` (`feature/initial-build`).

- It runs on free tiers: after ~15 minutes idle it sleeps and the first request
  takes about a minute; every sleep or restart signs users out (banking data is
  kept in MySQL). Registration takes a few seconds on the 0.1-CPU instance.
- Verified on 2026-09-19 with fictional data: HTTPS, registration/login/logout,
  recovery codes, transfers with database-backed duplicate protection,
  ownership and admin restrictions, freeze/unfreeze, live SSE updates in two
  browsers, persistence across restarts, database TLS with certificate and
  hostname verification, and real Groq responses with a labelled fallback.
  Evidence and the few things not re-run publicly: [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) §6–6b.
- Use fictional details only. Please don't load-test it; it is someone's free tier.

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
| `RATE_LIMIT_*`, `TRUST_PROXY_HEADERS` | Bounded in-process abuse protection (defaults: 20 registrations/hour and 10 logins/minute per IP); only trust `X-Forwarded-For` behind a real reverse proxy. |
| `GROQ_API_KEY`, `GROQ_MODEL`, `AI_*` | **Optional** AI. Blank key = no AI, everything still works with labelled "Calculated answer" fallbacks. Free-plan limits, data controls and verification status: [docs/ai-features.md](docs/ai-features.md). |
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
# The suite registers dozens of customers from one IP, so lift the register limit for this throwaway stack
# (CI does the same); a shell variable overrides .env without editing it.
$env:RATE_LIMIT_REGISTER_PER_HOUR = "1000"; $env:RATE_LIMIT_LOGIN_PER_MINUTE = "200"
docker compose up --build -d
cd e2e; npm install; npx playwright install chromium
$env:PLAYWRIGHT_BASE_URL = "http://localhost:8080"; $env:E2E_TARGET = "packaged"
npx playwright test
```

**Results observed on 2026-09-19** (exact suites and what each proves are in
[IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)):
- Backend: 110/110 (`.\mvnw.cmd clean test`, real MySQL 8 via Testcontainers).
- Frontend: 38/38 unit tests; `npm run build` is clean.
- E2E: 23/23 Playwright tests against the packaged Docker image with real MySQL.
- CI (GitHub Actions): all three jobs passed on the deployed commit `2f3294c`
  and on the earlier `1ee87ba`
  ([run 35419718879](https://github.com/poojaaxx/banking-system/actions/runs/35419718879));
  details in `IMPLEMENTATION_STATUS.md` §8.
- Public deployment: 9/9 of the browser specs above plus a 40-check API run
  passed against the live URL (see below).
- Live Groq: verified locally and on the public app (genuine assistant and
  categorization responses, unsupported model figures rejected, labelled
  fallback when the provider is unavailable); see
  [docs/ai-features.md](docs/ai-features.md). A real Groq 429/5xx has not been
  observed.

To re-run browser specs against a deployed instance (fictional data, low
volume; the free instance needs the longer timeouts in the public config):

```powershell
cd e2e
$env:PLAYWRIGHT_BASE_URL = "https://<your-service>.onrender.com"; $env:E2E_TARGET = "packaged"
npx playwright test -c playwright.public.config.ts customer-transfer duplicate-submission ownership-denial frontend-routes
```

To check a Groq key without printing it: `node scripts/ai-smoke.mjs --require`
(reads `GROQ_API_KEY` from the environment; exit 0 = pass, 1 = provider
failure, 2 = no key).

## Repository layout

```
backend/    Spring Boot (Java 17) API, Flyway migrations, backend tests
frontend/   React 19 + TypeScript + Vite SPA
e2e/        Playwright end-to-end tests against the packaged app
scripts/    Docker entrypoint, backup/restore helper scripts
docs/       Architecture, deployment, admin-bootstrap, backup/restore, ai-features, insights
render.yaml Optional Render Blueprint (free plan; the live service was created by hand with the same settings)
.github/workflows/  CI (backend tests, frontend build/tests, packaged E2E)
```

## Known gaps and unverified behavior

- **Free-tier hosting is a demo, not production.** Render sleeps when idle and
  Aiven may power off an idle free database; neither is covered by an SLA.
- Not re-run on the public instance: unusual-activity alert delivery over SSE
  and Insights with populated history (both covered locally and in CI), and a
  real Groq 429/5xx (covered only against a simulated provider).
- The insights/forecast accuracy figures are from **synthetic** fixtures, not
  real users; the model never has authority over money, and its output is
  checked against backend-computed figures.
- Admin has no self-service password reset; see
  `docs/admin-bootstrap.md` for the intentional manual procedure.
- In-memory sessions mean any restart (including a Render free-tier
  spin-down) signs everyone out — documented behavior, not a bug; banking
  data itself is unaffected since it lives entirely in MySQL.
- Rate limiting, the failed-login counter and the AI quota guard are process-local and in-memory —
  they reset on restart and aren't shared across instances. Fine for this
  single-instance demo; not a durable security guarantee.
- Firefox/WebKit/mobile Playwright projects are defined but commented out
  in `e2e/playwright.config.ts` (only Chromium runs by default) — enable
  them if you want to reproduce those checks locally.

## Backup/restore

See [docs/backup-restore.md](docs/backup-restore.md) — includes a real,
already-executed verification run (not just the procedure).

## Deployment

Render (Docker, Free) + Aiven MySQL (Free), database TLS verified with
`sslMode=VERIFY_IDENTITY`. Step-by-step settings, the exact environment
variables, the pitfalls hit on the real services and how the deployed instance
was verified: [docs/deployment.md](docs/deployment.md). Never commit `.env`,
`.env.deploy` or any `.pem`; all are Git-ignored and excluded from the Docker
build context.

## Architecture

See [docs/architecture.md](docs/architecture.md) and [CLAUDE.md](CLAUDE.md).
