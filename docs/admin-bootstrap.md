# Admin bootstrap and recovery

## How the first admin is created

There is no admin registration endpoint and no default admin account. The
**only** way an admin account comes into existence is:

1. On startup, `AdminBootstrapRunner` checks whether any row exists in the
   `admins` table.
2. If the table is **empty** and `ADMIN_BOOTSTRAP_USERNAME`,
   `ADMIN_BOOTSTRAP_EMAIL`, and `ADMIN_BOOTSTRAP_PASSWORD` are all set and the
   password meets the policy (≥12 characters, ≤72 bytes — bcrypt's hard
   limit), it creates exactly one admin with that username/email and a
   bcrypt hash of that password.
3. If any admin already exists, this is a no-op — restarting the app never
   resets or recreates an admin, and never logs the password anywhere.
4. If two instances raced to bootstrap simultaneously (not a concern for
   this single-instance app, but defensively handled anyway), the database's
   unique constraint on `admins.username`/`admins.email` means only one
   insert wins; the loser logs that it detected an existing admin and moves
   on.

**Local (docker-compose):** set `ADMIN_BOOTSTRAP_*` in your `.env` file
before first `docker compose up --build`. Once the admin exists, you can
remove those variables — they're only consulted when the table is empty.

**Hosted deployment (Render):** set them in Render's environment variable
dashboard for the first deploy, confirm the admin can log in, then blank
`ADMIN_BOOTSTRAP_PASSWORD` and redeploy. See `docs/deployment.md`.

## What if you need a second admin, or lost the first one's password?

There is deliberately no self-service admin recovery flow (unlike customer
recovery codes) — admin access is meant to be operator-controlled, not
self-service, since an admin can freeze/unfreeze any customer's account.

**To add a second admin**, or **reset a lost admin password**, connect
directly to the database with a MySQL client (see `docs/backup-restore.md`
for how to reach the DB from the app's own connection details) and either:

- Insert a new row into `admins` with a bcrypt hash you generate yourself
  (e.g. via `htpasswd -bnBC 10 "" 'your-password' | tr -d ':\n' | sed 's/^\$2y/\$2a/'`
  or any bcrypt tool — the app uses Spring Security's
  `DelegatingPasswordEncoder`, so a hash prefixed `{bcrypt}$2a$10$...` is
  required), **or**
- `UPDATE admins SET password_hash = '{bcrypt}...' WHERE username = '...'`
  to reset an existing admin's password.

This is intentionally a manual, break-glass procedure requiring direct
database access — treat database credentials with the same care as admin
credentials themselves.

## Never do this

- Never commit `ADMIN_BOOTSTRAP_PASSWORD` (or any admin password/hash) to
  the repository, logs, or documentation.
- Never expose an admin registration endpoint "temporarily" for convenience.
- Never reuse the same password across the bootstrap admin and any other
  account.
