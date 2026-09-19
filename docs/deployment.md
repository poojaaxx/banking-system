# Deployment (₹0 only): Render free web service + Aiven MySQL free tier

This app deploys as one Docker web service (Render) talking to one managed
MySQL instance (Aiven). Both are evaluated below against their **current**
free-tier terms, checked via their own docs on 2026-09-18 — re-verify at
`https://render.com/docs/free` and `https://aiven.io/docs/products/mysql/concepts/mysql-free-tier`
before you provision anything, since free-tier terms change without notice.

## What was verified, and when

| Claim | Source | Checked |
|---|---|---|
| Render free web service: 512 MB RAM, 0.1 CPU, 750 free instance-hours/month per workspace | render.com/docs/free | 2026-09-18 |
| Render free service spins down after 15 min idle; cold start ≈1 min on next request, shows a loading page meanwhile | render.com/docs/free | 2026-09-18 |
| Render free service filesystem is ephemeral (wiped on restart/redeploy/spin-down) | render.com/docs/free | 2026-09-18 |
| Render: no credit card required to start; exceeding bandwidth with no payment method suspends free services for the rest of the month; exhausting build minutes disables new builds but leaves the running service alone | render.com/docs/free | 2026-09-18 |
| Render supports Docker-based Web Services on the Free instance type (no documented exclusion) | render.com/docs/web-services, render.com/docs/docker | 2026-09-18 |
| Aiven MySQL free tier: 1 CPU, 1 GB RAM, 1 GB disk, max_connections=76, single node (no HA) | aiven.io/docs/products/mysql/concepts/mysql-free-tier | 2026-09-18 |
| Aiven: no credit card required; automated backups included; free forever (not time-boxed) but may be **powered off after a period of inactivity**, with advance email notice | aiven.io/docs/products/mysql/concepts/mysql-free-tier | 2026-09-18 |

**Re-checked 2026-09-19** against the same pages (a day later), reading exactly
what they state:

- Render (`render.com/docs/free`): 750 free instance hours per calendar month;
  spin-down after 15 minutes without inbound traffic; cold start "about one
  minute"; ephemeral filesystem, no persistent disks on free; suspended when
  hours or bandwidth run out ("If you haven't added a payment method, Render
  instead suspends all of your Free services"), which implies free services can
  exist without a payment method; and a plain warning: *"Do not use them for
  production applications."* That page does **not** explicitly say sign-up never
  asks for a card, and does not itself mention Docker on the Free type
  (Docker web services were read on the web-services/docker pages on
  2026-09-18). Confirm both in the sign-up flow before proceeding, and stop if
  a card is requested.
- Aiven (`.../mysql-free-tier`): "You don't need a credit card to sign up and
  you can use it indefinitely free of charge"; 1 CPU, 1 GB RAM, 1 GB disk,
  `max_connections=76`; **only one free service of each type per organization**;
  may be powered off after inactivity (notice sent, can be re-activated); not
  covered by an SLA. TLS enforcement is **not stated** on that page.

`render.yaml` in the repo root is an optional Render Blueprint (free plan,
secrets prompted for, none stored). It is unexecuted: it needs your account.

**Not independently confirmed in the fetched docs** (treat as "verify yourself
before relying on it"): the exact region list for Aiven's free tier, and
whether Aiven enforces TLS by default on every free service (this has long
been Aiven's standard behavior across their products, and this app is built
to require it regardless via `sslMode=VERIFY_IDENTITY`, but confirm the
specific setting shown in your service's "Connection information" page).

## Why this combination, and its real limitations

- **Render free web service sleeps.** After 15 minutes with no traffic it
  spins down; the next request pays a ~1 minute cold-start cost. This is
  fine for a portfolio demo, not for anything expecting instant response at
  all times. Document this to anyone you send the link to.
- **In-memory sessions + sleep = logged out on cold start.** Every spin-down
  is effectively an app restart, so (as documented in CLAUDE.md) all
  customers get signed out. Banking data itself is untouched — it lives in
  Aiven MySQL, not in the container.
- **Aiven can power off an idle free database.** If that happens, the app's
  next DB connection attempt fails until you resume it from the Aiven
  console. This is a demo trade-off of ₹0 hosting, not a bug.
- **750 free instance-hours/month is more than one always-on-while-visited
  demo needs**, but if the service is never idle it can exhaust the
  allowance before the month ends — Render suspends free services (not
  charges you) when that happens.
- **1 GB storage / 76 connections on Aiven** is ample for a demo dataset;
  the app's HikariCP pool is capped at 5 connections by default
  (`DB_POOL_MAX_SIZE`) specifically so one instance never gets close to that
  ceiling.

If Render or Aiven's free tier terms have materially changed since the dates
above, stop and re-evaluate — do not provision a paid tier "temporarily" and
do not rely on trial credits.

## One-time provisioning (external actions only you can perform)

These require your own accounts; nothing here can be done on your behalf.

### 1. Aiven MySQL

1. Create a free Aiven account (no card required) at aiven.io.
2. Create a new service → MySQL → **Free** plan → pick a cloud/region.
3. Once running, open the service's **Overview** tab and note: host, port,
   database name, user, password, and **Connection information → CA
   certificate**. Download/copy the CA certificate (PEM).
4. Do not create the application database schema yourself — Flyway does
   that automatically on first app startup (V1–V10 in
   `backend/src/main/resources/db/migration`).

### 2. Render web service

1. Push this repository to a Git host Render can read (GitHub/GitLab). This
   requires your explicit authorization to create/use a remote — the agent
   will not invent one.
2. In Render: **New → Web Service → Deploy an existing image or connect a
   repo** → point at this repo, environment = **Docker**, instance type =
   **Free**.
3. Set these environment variables in the Render dashboard (never in the
   Dockerfile, never committed):

   | Variable | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_HOST` | Aiven host |
   | `DB_PORT` | Aiven port |
   | `DB_NAME` | Aiven database name |
   | `DB_USER` | Aiven user |
   | `DB_PASSWORD` | Aiven password |
   | `DB_SSL_CA_PEM` | paste the full Aiven CA certificate (PEM) |
   | `DB_SSL_TRUSTSTORE_PASSWORD` | any strong random string you choose |
   | `COOKIE_SECURE` | `true` |
   | `COOKIE_SAME_SITE` | `Lax` |
   | `ADMIN_BOOTSTRAP_USERNAME` | your choice |
   | `ADMIN_BOOTSTRAP_EMAIL` | your choice (not a real inbox needed) |
   | `ADMIN_BOOTSTRAP_PASSWORD` | strong password, ≥12 chars — set only for first deploy, see below |
   | `TRUST_PROXY_HEADERS` | `true` (Render terminates TLS in front of the app) |
   | `GROQ_API_KEY` | **Optional.** Leave unset to run without AI (labelled fallback). If you set it, use a free-plan key only; see [ai-features.md](ai-features.md). |
   | `GROQ_MODEL` | Optional; default `openai/gpt-oss-20b` (`llama-3.3-70b-versatile` was deprecated for free tiers on 2026-08-16) |

   `PORT` does not need to be set — Render provides it automatically and
   `server.port: ${PORT:8080}` already reads it.

4. Deploy. On first successful startup, Flyway creates the schema and
   `AdminBootstrapRunner` creates the one admin account from
   `ADMIN_BOOTSTRAP_*`. **After confirming the admin account exists**,
   remove or blank `ADMIN_BOOTSTRAP_PASSWORD` in the Render dashboard and
   redeploy — it is only consulted when zero admins exist, but there is no
   reason to leave a plaintext password sitting in the environment
   longer than necessary.
5. Render provides HTTPS automatically at `https://<service>.onrender.com`
   — this is what makes `COOKIE_SECURE=true` and same-origin cookies work
   correctly.

## Post-deploy verification checklist

Using low-volume fictional test data only (never stress-test someone else's
free service):

- [ ] `GET /actuator/health` returns `200 {"status":"UP"}`
- [ ] Flyway ran all 10 migrations (check Render's deploy logs)
- [ ] HTTPS is enforced (Render's default `onrender.com` cert)
- [ ] Register a fictional customer, log out, log back in
- [ ] Create an account, simulate a deposit, transfer to a second fictional
      customer, confirm both balances and the live SSE update
- [ ] Open **Insights** as a new customer → "Insufficient history", no invented numbers
- [ ] Ask the assistant a question with no `GROQ_API_KEY` → labelled "Calculated answer"
- [ ] Attempt to read another customer's account by id → 403
- [ ] Log in as the bootstrapped admin, freeze an account, confirm a
      transfer from it is rejected
- [ ] Restart the Render service (manual deploy or after a natural
      spin-down/wake cycle) → confirm customers/balances/history persisted
      and a fresh login works (sessions do not persist — that's expected)

## The deployed instance (verified 2026-09-19)

Live at `https://securebank-app-k1a4.onrender.com` from commit `2f3294c` on
`feature/initial-build`; every checklist item above was exercised with fictional
data and passed (evidence and the test-script corrections are in
[IMPLEMENTATION_STATUS.md](../IMPLEMENTATION_STATUS.md), sections 6 and 6b).
Facts observed on the real services, not taken from docs:

- Aiven's service page showed **MySQL 8.4.8**, plan **Free-1-1gb**, `SSL mode: REQUIRED`
  (the TLS enforcement the doc page did not state). An account-level banner
  advertises trial credits for *non-free* plans; the billing report showed
  $0.00 costs / $0.00 credits for this service. Render's billing page showed
  the Hobby plan and "No card on file".
- The default compute plan in Render's *New Web Service* form is **$7/month** -
  select **Free** explicitly. Region: Singapore is closest to Aiven Bangalore.
- Render with auto-deploy Off: editing environment variables offers only
  **Save only**; follow it with **Manual Deploy → Deploy latest commit** (or
  *Restart service*) for the change to take effect. Secret values in the
  dashboard are masked and read-only until you click *Show secret*.
- Generated env files created on Windows may have CRLF line endings. A trailing
  `\r` silently ends up in the pasted value; normalise (`sed -i 's/\r$//'`)
  before copying values anywhere.
- Free-tier CPU (0.1) makes registration (bcrypt of the password and ten
  recovery codes) take several seconds. To run the browser specs against the
  public URL use `e2e/playwright.public.config.ts` (longer timeouts), keep the
  volume low (registration is rate-limited per IP), and set
  `E2E_TARGET=packaged` for the static-asset 404 spec.
- Verify database TLS from the server side, not from config: with the CA file,
  `SELECT ssl_version, ssl_cipher, COUNT(*) FROM sys.session_ssl_status s JOIN
  performance_schema.threads t USING (thread_id) WHERE t.processlist_user = '<db user>' GROUP BY 1, 2`.
  Read-only; never run test suites or resets against the hosted database.

## What NOT to do

- Do not add a paid Render instance type, a persistent disk add-on, a paid
  Aiven plan, a cron/worker service, or a custom domain — none of that is
  needed and all of it can incur cost.
- Do not disable TLS verification (`sslMode=REQUIRED` instead of
  `VERIFY_IDENTITY`) to work around a connection error — fix the truststore
  instead.
- Do not commit `DB_SSL_CA_PEM`, `DB_PASSWORD`, or `ADMIN_BOOTSTRAP_PASSWORD`
  anywhere. They belong only in Render's environment variable UI.
