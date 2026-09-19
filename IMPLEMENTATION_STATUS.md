# Implementation Status

Last updated: 2026-09-19.

Every item says **where** it was verified, because these are different claims:

- **local** — run on the developer machine (Windows, Docker Desktop, real MySQL 8 containers).
- **CI** — observed passing in GitHub Actions on a named commit.
- **real Groq** — exercised against the live Groq API with a real key.
- **public** — exercised on a deployed public instance.

`[x]` done and verified where stated · `[ ]` not done / not verified · `[!]` blocked on an external action.

## Summary of what is and is not verified

| Claim | local | CI | real Groq | public |
| --- | --- | --- | --- | --- |
| Core banking (accounts, ledger, idempotency, concurrency, admin) | [x] | [x] | n/a | [ ] |
| Unusual-activity checks, Insights, forecasts | [x] | [x] | n/a | [ ] |
| Assistant / categorization **with the model unavailable** (labelled fallback) | [x] | [x] | n/a | [ ] |
| Assistant / categorization **against a simulated provider** (429, 401, 500, timeout, quota) | [x] | [x] | n/a | [ ] |
| Assistant / categorization **against the real Groq API** | n/a | n/a | **[ ] NOT DONE — no `GROQ_API_KEY` available** | [ ] |
| Deployment on Render + Aiven | n/a | n/a | n/a | **[!] NOT DEPLOYED — needs your accounts** |

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
- [x] local (research) — Free-tier terms re-read from Render's and Aiven's own pages on 2026-09-19 ([docs/deployment.md](docs/deployment.md)), including what those pages do **not** state (Render's page does not say sign-up never asks for a card; Aiven's does not state TLS enforcement).
- [!] public — **Not deployed.** Creating a Render web service and an Aiven MySQL service requires signing in to those providers with your own accounts, and confirming in their sign-up flows that no card is requested. That cannot be done on your behalf. `render.yaml` + `docs/deployment.md` make it a short manual procedure. No public URL exists and none is claimed.

## 7. Backup & restore
- [x] local — `scripts/backup.sh` / `restore.sh` were executed end to end at schema `V8` (see `docs/backup-restore.md`). Not re-run at `V10`; the procedure is unchanged, but that specific run is not repeated here.

## 8. CI
- [x] CI — GitHub Actions run [`35419718879`](https://github.com/poojaaxx/banking-system/actions/runs/35419718879) on the release commit **`1ee87baf7ef2c2a11498dcf1755feb5e894b657c`** (`feature/initial-build`, push event) finished **success**, observed on 2026-09-19 through GitHub's public API. All three jobs passed: backend tests on real MySQL via Testcontainers, frontend unit tests + build, and Playwright against the packaged Docker image. The repository is public, so Actions minutes are free.
- This documentation-only follow-up commit is a different commit; its own run is not claimed here.

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

## Known blockers / external actions
- **Real Groq verification:** set `GROQ_API_KEY` (free key, no card) and run `node scripts/ai-smoke.mjs --require`; then use the assistant in the app.
- **Public deployment:** your own Render and Aiven sign-ups (see `docs/deployment.md`).

## Notable environment-driven adaptations
- Spring Boot 4.1.1 fine-grained starters; Jackson 3 (`tools.jackson.databind`); Spring Security 7's `DaoAuthenticationProvider` constructor; Hibernate 7.4.5 / Jakarta Persistence 3.2.
- Firefox/WebKit/mobile Playwright projects are defined but commented out (Chromium runs).
- Rate limiting, the AI quota guard, and the failed-login counter are process-local and reset on restart.
