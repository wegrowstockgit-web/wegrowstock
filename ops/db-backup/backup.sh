#!/bin/sh
# Nightly full PostgreSQL dump → gzip → S3 (archives-db-backups).
set -eu

: "${PGHOST:?PGHOST required}"
: "${PGPORT:=5432}"
: "${PGUSER:?PGUSER required}"
: "${PGPASSWORD:?PGPASSWORD required}"
: "${PGDATABASE:?PGDATABASE required}"
: "${S3_ENDPOINT:?S3_ENDPOINT required}"
: "${S3_BUCKET:=archives-db-backups}"
: "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID required}"
: "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY required}"
: "${AWS_DEFAULT_REGION:=us-east-1}"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
KEY="postgres/${PGDATABASE}/${STAMP}.sql.gz"

echo "[db-backup] dumping ${PGDATABASE}@${PGHOST}:${PGPORT} → s3://${S3_BUCKET}/${KEY}"

# Full schema + data; stream through gzip directly into S3 (no local dump file).
pg_dump \
  --host="${PGHOST}" \
  --port="${PGPORT}" \
  --username="${PGUSER}" \
  --dbname="${PGDATABASE}" \
  --format=plain \
  --no-owner \
  --no-acl \
  | gzip -c \
  | aws --endpoint-url="${S3_ENDPOINT}" s3 cp - "s3://${S3_BUCKET}/${KEY}" \
      --content-type "application/gzip" \
      --metadata "source=pg_dump,database=${PGDATABASE}"

echo "[db-backup] upload complete: s3://${S3_BUCKET}/${KEY}"
