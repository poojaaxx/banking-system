#!/bin/sh
# Logical backup of the banking demo database, encrypted at rest with GPG.
#
# Usage:
#   ./scripts/backup.sh
#
# Reads connection details from the same .env used by docker-compose (or
# pass them as env vars directly). Produces backups/banking-demo-<UTC
# timestamp>.sql.gpg -- move that file (and remember the passphrase) to
# storage OTHER than this machine's application filesystem; a backup that
# lives next to the thing it backs up is not a backup.
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

DB_HOST_FOR_DUMP="${BACKUP_DB_HOST:-127.0.0.1}"
DB_PORT_FOR_DUMP="${BACKUP_DB_PORT:-3307}"
DB_NAME="${MYSQL_DATABASE:-banking_demo}"
DB_USER_FOR_DUMP="${BACKUP_DB_USER:-root}"
DB_PASSWORD_FOR_DUMP="${BACKUP_DB_PASSWORD:-${MYSQL_ROOT_PASSWORD:?set MYSQL_ROOT_PASSWORD in .env, or pass BACKUP_DB_PASSWORD}}"

if [ -z "${BACKUP_GPG_PASSPHRASE:-}" ]; then
  echo "Set BACKUP_GPG_PASSPHRASE to encrypt this backup (never stored in .env for real use -- pass it inline for this run)." >&2
  exit 1
fi

mkdir -p "$ROOT_DIR/backups"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
PLAIN_PATH="$ROOT_DIR/backups/banking-demo-${TIMESTAMP}.sql"
ENCRYPTED_PATH="${PLAIN_PATH}.gpg"

echo "Dumping ${DB_NAME} from ${DB_HOST_FOR_DUMP}:${DB_PORT_FOR_DUMP} ..."
mysqldump \
  --host="$DB_HOST_FOR_DUMP" \
  --port="$DB_PORT_FOR_DUMP" \
  --user="$DB_USER_FOR_DUMP" \
  --password="$DB_PASSWORD_FOR_DUMP" \
  --single-transaction \
  --routines \
  --triggers \
  --set-gtid-purged=OFF \
  "$DB_NAME" > "$PLAIN_PATH"

echo "Encrypting with GPG (AES256, symmetric) ..."
gpg --batch --yes --symmetric --cipher-algo AES256 \
  --passphrase "$BACKUP_GPG_PASSPHRASE" \
  --output "$ENCRYPTED_PATH" \
  "$PLAIN_PATH"

# The plaintext dump never leaves this machine and never persists.
rm -f "$PLAIN_PATH"

echo "Backup written to: $ENCRYPTED_PATH"
echo "Move this file (and remember the passphrase, kept separately) off this machine."
