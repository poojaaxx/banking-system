# Implementation Status

Last updated: 2026-09-19 (public deployment verified).

Every item says **where** it was verified, because these are different claims:

- **local** — run on the developer machine (Windows, Docker Desktop, real MySQL 8 containers).
- **CI** — observed passing in GitHub Actions on a named commit.
- **real Groq** — exercised against the live Groq API with a real key.
- **public** — exercised on a deployed public instance.

`[x]` done and verified where stated · `[ ]` not done / not verified · `[!]` blocked on an external action.

## Summary of what is and is not verified

| Claim | local | CI | real Groq | public |
| --- | --- | --- | --- | --- |
| Core banking (accounts, ledger, idempotency, concurrency, admin) | [x] | [x] | n/a | [x] |
| Unusual-activity checks, Insights, forecasts | [x] | [x] | n/a | [~] Insights endpoint 200 only; alert delivery not re-run publicly |
| Assistant / categorization **with the model unavailable** (labelled fallback) | [x] | [x] | n/a | [x] (no key, and rejected key) |
| Assistant / categorization **against a simulated provider** (429, 401, 500, timeout, quota) | [x] | [x] | n/a | n/a |
| Assistant / categorization **against the real Groq API** | n/a | n/a | **[x] 2026-09-19** (new key; smoke exit 0) | **[x] 2026-09-19** (12/12 on the public URL) |
| Deployment on Render + Aiven | n/a | n/a | n/a | **[x] https://securebank-app-k1a4.onrender.com** at `2f3294c` |

## 0. Foundation
- [x] local — Root structure, `.gitignore`, `.gitattributes`, `.env.example`, `CLAUDE.md`, `README.md`, this file.
- [x] Git: remote `https://github.com/poojaaxx/banking-system`; work is on `feature/initial-build`. **Correction to record:** `main` was fast-forwarded once, at the owner's request, to commit `f27aa4c`; it has not been touched since, and release work is pushed to `feature/initial-build` only.
- [x] local — `Dockerfile` (multi-stage), `docker-compose.yml`, `.dockerignore`, optional `render.yaml` (unexecuted).

## 1. Database
- [x] local — Flyway `V1`–`V10`, all applied to real MySQL 8 (Testcontainers, docker-compose, and `V10` on top of a populated database). `V9`: category-change audit. `V10`: `dedupe_key` + two unusual-activity rules.
- Ledger invariants unchanged: append-only, balanced double-entry, DB-backed idempotency, pessimistic ascending-id locking. One documented exception: `ledger_entries.category_id` is non-financial metadata, changeable by its owner and audited (see `CLAUDE.md`).

## 2. Backend (Spring Boot 4.1.1 / Java 17 / Security 7.1.1 / Hibernate 7.4.5)
- [x] local — Security, admin bootstrap, ledger engine, full customer and admin APIs (see earlier sections of the README/architecture doc).
- [x] local — **API error contract** (this release): unknown routes → `404` JSON; existing route with the wrong method → `405` with `Allow`; malformed JSON / missing header / bad parameter → `400`; unsupported content type → `415`; authentication, authorization and CSRF are enforced *before* routing; no API response is SPA HTML or an accidental `500`. Verified by `api-errors.spec.ts` (8 tests) against the packaged image.
- [x] local — **Unusual-activity checks** extending the existing alerts module (`UNUSUAL_LARGE_SPEND`, `REPEATED_PAYMENT`): customer-specific, baselines from earlier data only, deduplicated (`INSERT IGNORE`), delivered as a notification through the existing SSE path to the owning customer only, labelled as statistical checks/rules (never "AI fraud detection"), never freeze/block/move money, and unable to fail a transfer. Method and thresholds: [docs/insights.md](docs/insights.md).
- [x] local — **Insights**: observed month-to-date spending, month-end projection, budget-overrun estimates, savings-goal progress/estimates, all computed in backend code (`SpendingForecaster`, `GoalProjector`, `SpendingStatistics`), own-account transfers and simulated deposits excluded, refunds netted, "Insufficient history" gates, no confidence percentages.
- [x] local — **Groq integration hardened** ([docs/ai-features.md](docs/ai-features.md)): optional; model-output verification (amounts, dates, totals, references, links); trusted facts rendered from backend fields; bounded (timeouts, token caps, local calls/tokens quota under the free plan's limits, per-customer rate limit); `429` honors `Retry-After`; auth failure cooldown; privacy-safe logging (asserted by a test); default model corrected to `openai/gpt-oss-20b` because Groq deprecated the Llama models for free tiers on 2026-08-16.

## 3. Frontend (React 19 + TypeScript + Vite + React Router 7 + TanStack Query 5)
- [x] local — All customer/admin pages; SecureBank branding; discreet fictional-funds note (login, register, admin login, footer, `/about`) with no large banners.
- [x] local — **Insights page**: observed vs projected in separate labelled sections, assumptions and basis, "Insufficient history" states, budget estimates, goal progress with estimates only when supported; unusual-activity list refreshes live over SSE.
- [x] local — Assistant page: "AI answer · checked against your records" vs "Calculated answer" with the reason for any fallback; figures and transactions shown from backend fields.

## 4. Testing (all counts observed, not assumed)
- [x] local — **Backend: 110/110** (`mvnw clean test`, real MySQL 8 via Testcontainers), run from a clean build in a copy outside OneDrive. Includes: ledger concurrency/idempotency (11), recovery codes (4), assistant (9), categorization (5 + 7), answer validator (10), Groq client failure modes (11), unusual activity (11), insights (12), statistics (7), forecaster (13), goal projector (8), synthetic held-out evaluation (1), context load (1).
- [x] local — **Frontend: 38/38** Vitest + RTL; `npm run build` clean.
- [x] local — **Packaged-browser E2E: 23/23** Playwright (Chromium) against the packaged Docker image with real MySQL: core transfer with live SSE update across two browser contexts, duplicate-submission safety, ownership denial, SPA/static routing, the API error contract, cold start ("Insufficient history"), unusual-activity delivery live over SSE to only the owner with nothing blocked, duplicate-alert suppression, labelled AI fallback with no key, discreet disclosure.
- [x] local — 10-step final demonstration script (`e2e/final-demo.mjs`, steps 1–9) and step 10 (restart: identical database state, admin re-login works, stale session rejected) re-run against this release's image on 2026-09-19.
- [x] local — Synthetic held-out forecast evaluation: [docs/insights.md](docs/insights.md#evaluation-on-chronological-held-out-fixtures-synthetic). **Synthetic results only — not real-user accuracy; there are no real users.**

## 5. Packaging & local running
- [x] local — `docker compose up --build` (verified repeatedly, including a `V10` upgrade of an existing database). Compose now passes `RATE_LIMIT_*` through (previously documented but silently ignored) and the AI variables.

## 6. Deployment
- [x] local (research) - Free-tier terms re-read from Render's and Aiven's pages on 2026-09-19 ([docs/deployment.md](docs/deployment.md)).
- [x] **public - DEPLOYED 2026-09-19: https://securebank-app-k1a4.onrender.com**
  - Render web service `securebank-app`: Docker, **Free** instance, Singapore, branch `feature/initial-build`, **deployed commit `2f3294c`** (green CI), auto-deploy **Off**, health check `/actuator/health`. Workspace billing page: plan *Hobby*, **"No card on file"**. No disk, worker, cron or custom domain.
  - Aiven service `mysql-20edaaf5`: **MySQL 8.4.8**, plan **Free-1-1gb** (1 CPU / 1 GB), DigitalOcean Bangalore. The account also shows a platform-trial banner for *non-free* plans; the billing report shows **$0.00 costs and $0.00 applied credits**, so this service is not trial-funded. Nothing else was created there.
  - **Database TLS verified (not just configured):** `openssl` verification of the Aiven CA chain and hostname (correct host accepted, wrong host rejected with "hostname mismatch"); `mysql --ssl-mode=VERIFY_IDENTITY` succeeds, and fails without the CA; and, from the server side (`sys.session_ssl_status`), every connection opened by the deployed app is **TLSv1.3 / TLS_AES_256_GCM_SHA384**. The truststore is built from `DB_SSL_CA_PEM` at container start and Hikari uses `sslMode=VERIFY_IDENTITY`.
  - Flyway V1-V10 applied to the empty Aiven database on MySQL 8.4.8 (my earlier tests used 8.0) with the production settings, from a local container, before the Render deploy. No destructive statement was ever run against Aiven. Read-only check on the public database: ledger credits minus debits = 0.00.
  - Secrets live only in Render's environment, Git-ignored local files (`.env`, `.env.deploy`, `.env.deploy.ca.pem`, `.env.local-admin`; all excluded from the Docker context) and Aiven. `ADMIN_BOOTSTRAP_PASSWORD` was removed from Render after the admin existed.
  - **Known limits (free tier):** sleeps after 15 min idle (about 1 min cold start; all sessions lost, data kept); 0.1 CPU, so registration (bcrypt of the password plus 10 recovery codes) takes several seconds; Aiven may power off an idle free database.

## 6b. Public verification (2026-09-19, https://securebank-app-k1a4.onrender.com, fictional data only)
- [x] HTTPS with HSTS, HTTP to HTTPS 301, `Secure` cookies; `/actuator/health` UP; `/actuator/env` 404; JS/CSS served with correct content types; missing asset returns 404 (not HTML); SPA routes serve the app; unknown/unsupported API paths return JSON errors.
- [x] API run (40 checks, after correcting my own script's wrong expectations - see below): registration (+10 recovery codes), login/logout, wrong password 401, recovery-code login and single use, accounts, deposit, withdrawal, overdraft rejected, concurrent identical requests give exactly one applied result (the other got 409 in-flight), sequential retry replays the same reference, same key with a different body gives 409, exact balances, own-account transfer rejected, ownership denial (403 on read, spend and history), customer denied on the admin API, statement CSV, support ticket plus admin reply, persisted notification, admin login with the **new** credentials, admin cannot use customer money endpoints, **freeze, transfer rejected, unfreeze, transfer works**.
- [x] Browser (Playwright, the existing specs via `e2e/playwright.public.config.ts`): 9/9, including **two browser contexts where a transfer updates the recipient's balance and unread badge live over SSE**, duplicate-submission safety, ownership denial, admin routes reject a customer session, hard-refresh session persistence, and the missing-asset 404 (`E2E_TARGET=packaged`).
- [x] **Persistence:** after a Render restart (and after redeploys) customers, accounts, balances, transactions, notifications and admin login persisted; a session cookie captured *before* a redeploy returned **401 afterwards**; fresh logins worked.
- [x] **Real Groq, public:** assistant `aiGenerated=true / NONE`; on-demand categorization `source=AI`; a prompt-injection request was refused; a request for another customer's account was answered only from the caller's own data and the recipient's balance was unchanged; an over-reaching question produced a model answer with a figure the backend never supplied and it was **rejected** (`AI_ANSWER_REJECTED`), with the backend's own figure shown; AI categorization of another customer's entry returned 403. **Provider failure on the public app:** with a deliberately invalid key the assistant returned the labelled "Calculated answer" and `aiAvailable=false`, with banking unaffected; the real key was then restored and genuine answers resumed.
- [ ] Not re-run publicly: unusual-activity alert delivery over SSE (covered locally and in CI), Insights with populated history, a real Groq 429/5xx.
- Test-script mistakes corrected during the run (not application defects): expecting an anonymous session right after registration (registration signs the user in), expecting both concurrent duplicates to return 200 (one correctly returns 409), miscounting posted rows, omitting the required `reason` on unfreeze, and treating 204 as failure. Playwright's default 5-second assertion timeout is too short for the free instance's bcrypt registration, hence the public config.

## 7. Backup & restore
- [x] local — `scripts/backup.sh` / `restore.sh` were executed end to end at schema `V8` (see `docs/backup-restore.md`). Not re-run at `V10`; the procedure is unchanged, but that specific run is not repeated here.

## 8. CI
- [x] CI - the deployed commit **`2f3294c`** and its predecessors `1ee87ba` (run `35419718879`), `ad2c9c6` and `5cda9e7` (run `35425791697`) all finished **success** on GitHub Actions (backend on real MySQL via Testcontainers, frontend, packaged Playwright), observed via GitHub's public API on 2026-09-19. Later documentation/script-only commits get their own runs; they are not what is deployed.

## What went wrong along the way (kept for honesty)

Earlier build (unchanged): admin login broken by `@Primary` bean resolution (caught by the demonstration, fixed with explicit `@Qualifier`); OneDrive/incremental-compile quirk losing classes in `target/`; missing static asset returned `500`.

This release:
- **Stale default model.** Phase 1 shipped `llama-3.3-70b-versatile`, which Groq deprecated for free tiers on 2026-08-16. Found while re-verifying the free plan; default changed, and the same stale value was also pinned in `docker-compose.yml`, `.env.example` and a local `.env`, which would have silently overridden the code default.
- **Invented numbers in Phase 1.** Rule-based categorization returned a hard-coded `confidence: 0.7`; removed (no confidence is exposed).
- **Forecast gate too lenient.** The first version (≥ 5 payments) still forecast a sparse profile with 52–80% error in held-out testing; gates tightened to 10 payments and 25% active days. A second finding: the outlier cap flattened a regular heavy fortnight; the cap now applies only when at most 10% of payments exceed the fence.
- **Unsupported methods returned `500`.** `GET /api/customer/deposits` failed with a `500` and no `Allow` header; fixed and covered.
- **Documented but ignored settings.** `RATE_LIMIT_*` in `.env.example` were never passed by `docker-compose.yml`; the e2e suite tripped the (working) registration limiter. Fixed; CI raises the limit for its throwaway stack only.
- **A faulty scripted edit corrupted `docker-compose.yml`** (my search matched the wrong service). The file was clean in git, so it was restored and re-edited precisely.
- **The OneDrive build directory was clobbered mid-run** (51 errors of `ClassNotFoundException`, including pure unit tests). The same tests had passed class-by-class; the trustworthy 110/110 comes from a clean build outside OneDrive.
- **A hard-coded local demo admin password** was committed in `e2e/final-demo.mjs` in `94f8418` and is therefore in the public history. The script now reads it from the environment. History was not rewritten. On 2026-09-19 that password was rotated in the local database (documented `UPDATE admins` procedure), sessions were invalidated by restart, and the old value was confirmed rejected (HTTP 401). It was never deployed anywhere.

- **Groq key exposed in the session.** A file-sync notice printed `.env`, including the Groq key. It was recognised as the key still configured (the console listed a key with the same last characters), a replacement was created and written straight into `.env`, and the exposed key was **revoked** in the Groq console. Nothing else secret was in that file (local placeholder DB passwords; blank admin bootstrap value).
- **Windows carriage returns in a generated env file.** `.env.deploy` had CRLF endings on its first four lines, so my first copy into Render put a trailing `\r` on `ADMIN_BOOTSTRAP_USERNAME/EMAIL/PASSWORD` and `DB_SSL_TRUSTSTORE_PASSWORD`. The app still worked (bootstrap skipped; truststore password used consistently), but it was wrong: the values were corrected, the file normalised to LF, and the bootstrap password variable removed. A first attempt to delete that row also removed `TRUST_PROXY_HEADERS`; that was noticed on re-listing and re-added before any verification relied on it.
- **`ai-smoke.mjs` exited 127 on Windows** (libuv assertion from `process.exit()` while sockets were closing). Fixed by setting `process.exitCode`; verified exit 0 with a valid key, 1 with a rejected key, 2 with no key and `--require`.
- **A flaky E2E assertion failed CI on a docs-only commit** (`fbc0173`, run 11). `duplicate-submission` asserted that exactly one of three simultaneous identical requests returns 200, but a duplicate that arrives after the first commits legitimately gets a 200 replay of the stored result, so the count depends on timing (it passed on the same code in runs 8 and 10). Application behaviour was correct; the test was over-specified. It now asserts the real invariants: every response is 200 or 409, at least one succeeds, all successes carry the same transfer reference, and the balances show one debit and one credit. Passed 3/3 locally before pushing.
- **Pre-existing minor quirk, not changed:** `HEAD /` returns 401 (GET works). Render's health check uses `GET /actuator/health`; only HEAD-based uptime monitors would notice.

## Known blockers / external actions
- **Real Groq 429/5xx** behaviour is covered only by simulated-provider tests; the live provider was exercised for success and for a rejected key.
- **Ongoing:** the free tiers can sleep (Render) or power off (Aiven) when idle and are not for production; Groq's free plan limits are organisation-wide. No blockers for the deployed demo.

## Notable environment-driven adaptations
- Spring Boot 4.1.1 fine-grained starters; Jackson 3 (`tools.jackson.databind`); Spring Security 7's `DaoAuthenticationProvider` constructor; Hibernate 7.4.5 / Jakarta Persistence 3.2.
- Firefox/WebKit/mobile Playwright projects are defined but commented out (Chromium runs).
- Rate limiting, the AI quota guard, and the failed-login counter are process-local and reset on restart.
