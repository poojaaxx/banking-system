# Architecture

## System overview

One Docker image contains a Spring Boot (Java 17) backend serving both the
REST API (`/api/**`) and the built React SPA (everything else) from the same
origin, backed by one MySQL 8 database. See `CLAUDE.md` for the pinned
architectural decisions and the reasoning behind them; this document focuses
on how the pieces fit together.

```
Browser
  │  same-origin (cookies + CSRF work without CORS)
  ▼
Spring Boot (Tomcat, embedded)
  ├─ /api/auth/**        session-based login/register/recovery
  ├─ /api/customer/**    customer-only REST endpoints (ROLE_CUSTOMER)
  ├─ /api/admin/**       admin-only REST endpoints (ROLE_ADMIN)
  ├─ /api/events/stream  authenticated SSE (notifications)
  ├─ /actuator/health    liveness/readiness, no auth
  └─ /**  (GET, no extension)  → index.html (React Router takes over)
      /**  (GET, real file)    → static asset or 404
  │
  ▼
MySQL 8 (Flyway-migrated schema, V1–V8)
```

## Backend package layout (modular monolith, package-by-feature)

| Package | Responsibility |
|---|---|
| `account` | Account entity/repo, creation/closure, admin freeze/unfreeze, account-number generation |
| `customer`, `admin` | Identity entities and registration/password logic |
| `security` | Principals, session auth, CSRF, rate limiting, admin bootstrap |
| `ledger` | The double-entry ledger engine, transaction history, spending categories |
| `idempotency` | The durable idempotency-key protocol (used by every money-moving operation) |
| `notification`, `notification.sse` | Durable notifications + the SSE fan-out |
| `beneficiary`, `support`, `moneyrequest`, `billpay`, `budget`, `savingsgoal`, `alert`, `audit` | One package per additional feature |
| `adminapi` | Admin-facing controllers/DTOs that read across several domains (dashboard aggregates, customer/account/transaction inspection) |
| `common`, `config` | Shared exception handling, `AppProperties`, SPA static-resource config |

Relationships between entities are modeled as plain `Long` foreign-key
columns, not JPA `@ManyToOne` associations — this keeps every pessimistic
lock and transaction boundary in the ledger engine explicit (see below)
rather than hidden behind lazy-loading proxies.

## Database schema and relationships

Full DDL lives in `backend/src/main/resources/db/migration/V1__…` through
`V8__…`; the entity relationship summary:

- **customers** ← owns → **accounts** (`accounts.owner_customer_id`).
  Two special **SYSTEM** accounts (`SYSTEM_CASH`, `SYSTEM_BILLPAY`, seeded in
  V1) have no owner and represent "the outside world" for simulated
  deposits/withdrawals/bill payments — they're excluded from recipient
  search and from customer balance totals.
- **financial_transactions** (one row per completed movement, with a unique
  `reference`) ← has exactly two → **ledger_entries** (one `DEBIT`, one
  `CREDIT`; a unique constraint on `(financial_transaction_id, direction)`
  makes a third leg or a duplicate leg impossible at the database level).
- **idempotency_keys**, unique on `(customer_id, operation_type,
  idempotency_key)`, links to the `financial_transactions` row it produced
  and stores a JSON response snapshot for replay.
- **beneficiaries**, **notifications**, **recovery_codes**,
  **support_tickets** → **support_messages**, **audit_log**,
  **alert_rules** → **account_alerts**, **billers** → **bill_payments**,
  **budgets**, **savings_goals** (each with its own dedicated linked
  `accounts` row), **money_requests** — each described in the migration
  file that created it, with inline comments explaining any non-obvious
  invariant (e.g. why `spending_categories` had to exist before
  `ledger_entries`).

## Money-movement invariants (enforced in code, not just convention)

1. **Every balance mutation happens inside `LedgerEngine.executeMovement`**,
   the only method in the codebase allowed to write to `accounts.balance`.
2. **Locking order**: both accounts involved are locked via
   `SELECT … FOR UPDATE` in ascending-id order, always — this is what
   prevents deadlocks under concurrent opposite-direction transfers (see
   `LedgerServiceIntegrationTest#oppositeDirectionTransfers…`).
3. **Idempotency claim happens in its own committed transaction** before the
   movement starts (`IdempotencyKeyStore`), so the database's unique
   constraint — not an in-memory map — is what makes concurrent duplicate
   requests safe.
4. **One transaction covers the whole movement**: both ledger rows, both
   balance updates, the idempotency-row completion, and any notification.
   A failure anywhere rolls all of it back, and the idempotency row is
   marked `FAILED` (safe to retry) in a *separate* transaction after the
   fact — see `LedgerServiceIntegrationTest#failureMidTransaction…`.
5. **Admins never move money.** `AdminAccountService` only has
   freeze/unfreeze methods; there is no code path from an admin principal
   into `LedgerEngine`.

## Real-time updates

`SseEventPublisher` holds in-memory `SseEmitter`s per authenticated
recipient (customer or admin), bounded per-recipient and globally, with a
15-minute timeout relying on the browser's native `EventSource` reconnect.
`NotificationService.create()` persists the notification as part of the
caller's existing transaction and only calls the publisher
**after that transaction commits** (`TransactionSynchronizationManager`),
so a rolled-back operation can never produce a phantom live update. On
reconnect, the client's `Last-Event-ID` header (automatically set by
`EventSource` to the last event id it saw) drives a database replay of any
notifications created while the connection was down.

## Frontend

React 19 + TypeScript + Vite + React Router + TanStack Query. Every
financial mutation goes through `usePendingOperation` (see
`frontend/src/lib/pendingOperation.ts`) to persist its idempotency key
across a refresh and to distinguish a clean rejection (safe to reset) from
an ambiguous outcome (must reuse the same key on retry). SSE events
(`useEventStream`) never update UI state directly — they only invalidate
TanStack Query caches, so a refetch from the real backend is always what
the UI actually renders.
