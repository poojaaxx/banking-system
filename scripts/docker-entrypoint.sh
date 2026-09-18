#!/bin/sh
# Builds a JKS truststore from a PEM-encoded CA certificate at container
# startup, when one is supplied via DB_SSL_CA_PEM (used for hosted MySQL
# providers like Aiven that require sslMode=VERIFY_IDENTITY). This runs fresh
# on every container start -- nothing here needs to persist across restarts,
# and no deployment-specific secret is baked into the image itself.
set -e

if [ -n "$DB_SSL_CA_PEM" ] && [ -z "$DB_SSL_TRUSTSTORE_URL" ]; then
  TRUSTSTORE_PATH="/tmp/db-truststore.jks"
  TRUSTSTORE_PASSWORD="${DB_SSL_TRUSTSTORE_PASSWORD:-changeit}"
  CA_PATH="/tmp/db-ca.pem"

  printf '%s' "$DB_SSL_CA_PEM" > "$CA_PATH"
  keytool -importcert -noprompt \
    -alias hosted-mysql-ca \
    -file "$CA_PATH" \
    -keystore "$TRUSTSTORE_PATH" \
    -storepass "$TRUSTSTORE_PASSWORD"
  rm -f "$CA_PATH"

  export DB_SSL_TRUSTSTORE_URL="file:${TRUSTSTORE_PATH}"
  export DB_SSL_TRUSTSTORE_PASSWORD="$TRUSTSTORE_PASSWORD"
  echo "Built MySQL TLS truststore from DB_SSL_CA_PEM at ${TRUSTSTORE_PATH}"
fi

exec java $JAVA_OPTS -jar /app/app.jar
