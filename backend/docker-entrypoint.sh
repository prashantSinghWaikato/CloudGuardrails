#!/bin/sh
set -eu

SECRET_DIR="/app/secrets"
mkdir -p "$SECRET_DIR"

decode_secret_file() {
  env_name="$1"
  output_path="$2"

  value="$(printenv "$env_name" 2>/dev/null || true)"
  if [ -n "$value" ]; then
    printf '%s' "$value" | base64 -d > "$output_path"
  fi
}

decode_secret_file "KAFKA_SSL_KEYSTORE_BASE64" "$SECRET_DIR/client.keystore.p12"
decode_secret_file "KAFKA_SSL_TRUSTSTORE_BASE64" "$SECRET_DIR/client.truststore.jks"

exec java -jar /app/app.jar
