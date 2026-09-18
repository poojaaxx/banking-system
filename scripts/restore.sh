#!/bin/sh
# Restores an encrypted logical backup into an EXPLICITLY DISPOSABLE MySQL
# target -- never the live database. Refuses to run unless the target
# database name contains "disposable" or "restore_test", as a guardrail
# against accidentally overwriting live data.
#
# Usage:
#   BACKUP_GPG_PASSPHRASE=... ./scripts/restore.sh backups/banking-demo-XXXX.sql.gpg
set -eu

ENCRYPTED_PATH="${1:?Usage: restore.sh <path-to-.sql.gpg>}"
RESTORE_DB_HOST="${RESTORE_DB_HOST:-127.0.0.1}"
RESTORE_DB_PORT="${RESTORE_DB_PORT:-3309}"
RESTORE_DB_NAME="${RESTORE_DB_NAME:-banking_demo_restore_test}"
RESTORE_DB_USER="${RESTORE_DB_USER:-root}"
RESTORE_DB_PASSWORD="${RESTORE_DB_PASSWORD:?set RESTORE_DB_PASSWORD (the disposable target's root password)}"

case "$RESTORE_DB_NAME" in
  *disposable*|*restore_test*) ;;
  *)
    echo "Refusing to restore into '$RESTORE_DB_NAME' -- target database name must contain" >&2
    echo "'disposable' or 'restore_test' as a guardrail against restoring over live data." >&2
    exit 1
    ;;
esac

if [ -z "${BACKUP_GPG_PASSPHRASE:-}" ]; then
  echo "Set BACKUP_GPG_PASSPHRASE to decrypt the backup." >&2
  exit 1
fi

PLAIN_PATH="$(mktemp)"
trap 'rm -f "$PLAIN_PATH"' EXIT

echo "Decrypting $ENCRYPTED_PATH ..."
gpg --batch --yes --decrypt --passphrase "$BACKUP_GPG_PASSPHRASE" --output "$PLAIN_PATH" "$ENCRYPTED_PATH"

echo "Restoring into disposable database '${RESTORE_DB_NAME}' at ${RESTORE_DB_HOST}:${RESTORE_DB_PORT} ..."
mysql --host="$RESTORE_DB_HOST" --port="$RESTORE_DB_PORT" --user="$RESTORE_DB_USER" --password="$RESTORE_DB_PASSWORD" \
  -e "DROP DATABASE IF EXISTS \`${RESTORE_DB_NAME}\`; CREATE DATABASE \`${RESTORE_DB_NAME}\`;"

mysql --host="$RESTORE_DB_HOST" --port="$RESTORE_DB_PORT" --user="$RESTORE_DB_USER" --password="$RESTORE_DB_PASSWORD" \
  "$RESTORE_DB_NAME" < "$PLAIN_PATH"

echo "Restore complete into '${RESTORE_DB_NAME}'."
