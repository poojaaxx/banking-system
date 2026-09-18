# Backup and restore

## Procedure

`scripts/backup.sh` runs `mysqldump --single-transaction` against the
running database, encrypts the dump with GPG (AES256, symmetric passphrase),
deletes the plaintext dump, and writes `backups/banking-demo-<UTC
timestamp>.sql.gpg`. **Move that file off this machine** — a backup that
lives on the same filesystem as the application is not a backup — and keep
the passphrase somewhere separate from the file itself (a password manager,
not a note next to the backup).

```powershell
# From the repo root, with docker compose's mysql service running and its
# port bound to the host (docker-compose.yml already does this on 127.0.0.1:3307):
$env:BACKUP_GPG_PASSPHRASE = "choose-a-strong-passphrase"
sh scripts/backup.sh
```

`scripts/restore.sh` decrypts a backup and restores it into a MySQL target
whose database name **must contain** `disposable` or `restore_test` — this
is a deliberate guardrail; the script refuses to run otherwise, so it can
never be pointed at a live database name by accident.

```powershell
# Point at a throwaway MySQL container/instance you created for this purpose only:
$env:BACKUP_GPG_PASSPHRASE = "choose-a-strong-passphrase"
$env:RESTORE_DB_PASSWORD = "that-throwaway-instance-root-password"
sh scripts/restore.sh backups/banking-demo-20260101T000000Z.sql.gpg
```

## What was actually verified (2026-09-18)

This was run for real, not assumed:

1. Started the docker-compose stack, registered a fictional customer
   (`backup_test_user`), created an account, and deposited ₹4,242.00 —
   giving the database 19 customers, 21 accounts, 23 financial
   transactions, and 46 ledger entries (some pre-existing from earlier
   local testing).
2. Confirmed the ledger was balanced before backup: `SUM(debit) =
   SUM(credit) = ₹57,242.00`.
3. Ran `scripts/backup.sh` against the compose MySQL (`127.0.0.1:3307`) →
   produced an encrypted `.sql.gpg` file; the plaintext dump was deleted
   immediately after encryption.
4. Started a **separate, disposable** MySQL 8.0 container
   (`banking_demo_restore_test`, not the compose project's volume).
5. Ran `scripts/restore.sh` against it.
6. Verified in the restored database:
   - Same counts: 19 customers, 21 accounts, 23 transactions, 46 ledger
     entries.
   - Same reconciliation: debits = credits = ₹57,242.00.
   - `backup_test_user`'s bcrypt password hash restored byte-for-byte.
   - `flyway_schema_history` shows all 8 migrations present with
     `success = 1`.
7. **Built the actual application jar** (`mvn package`) and ran it
   pointed at the restored disposable database on a different port.
8. Logged in over HTTP as `backup_test_user` with the original password →
   succeeded, and `GET /api/customer/dashboard` returned the exact
   restored account (`130504291480`, ₹4,242.00) and the exact original
   transaction (`TXN-9ed0d31b-…`, ₹4,242.00 deposit).
9. Tore down every disposable resource used for this test (the restore
   container, the test jar's process, the temporary encrypted backup file).
   Nothing from this test was left running or committed.

## Credential rotation

- **Database password**: rotate in Aiven's console (or your local MySQL),
  then update `DB_PASSWORD` in Render's environment variables and redeploy.
  No application code change needed.
- **Admin password**: an admin can change only their own password by...
  actually there is no self-service admin password change endpoint in this
  demo (only customers have one). To rotate an admin's password, use the
  manual database procedure in `docs/admin-bootstrap.md`.
- **Customer passwords**: customers rotate their own via Security Settings
  (`POST /api/auth/customer/change-password`), which also invalidates their
  other active sessions.
- **Backup encryption passphrase**: if a passphrase may have been exposed,
  it does not need "rotating" retroactively (old backups stay encrypted with
  the old passphrase) — just use a new passphrase for the next backup and
  keep the old one only as long as you might need to restore an old backup.

## Known limitations

- Restoring is a **full-database replace** (`DROP DATABASE` +
  `CREATE DATABASE` + reload) — there is no point-in-time or partial
  restore. This is appropriate for a demo, not for a system with real
  financial data.
- Flyway's forward-only migration model means restoring an **older** backup
  taken before a schema change, into a database that has since been
  migrated further, will leave `flyway_schema_history` behind the
  migrations directory — Flyway will then try to apply the "missing"
  migrations on next app startup. For this demo, always restore into a
  database that will subsequently run the exact same migration set the
  backup was taken with (or a superset), never a mix.
- The single-instance, in-memory session model means a restore (like any
  restart) signs everyone out — this is expected, not a restore defect.
