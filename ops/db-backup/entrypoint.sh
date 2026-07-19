#!/bin/sh
set -eu

CRON_SCHEDULE="${BACKUP_CRON:-0 2 * * *}"
RUN_ON_START="${BACKUP_RUN_ON_START:-false}"

install -m 0755 /opt/db-backup/backup.sh /usr/local/bin/backup.sh

if [ "${RUN_ON_START}" = "true" ]; then
  echo "[db-backup] running initial backup"
  /usr/local/bin/backup.sh || echo "[db-backup] initial backup failed (will retry on cron)" >&2
fi

echo "${CRON_SCHEDULE} /usr/local/bin/backup.sh >> /var/log/db-backup.log 2>&1" > /etc/crontabs/root
echo "[db-backup] cron installed: ${CRON_SCHEDULE}"
exec crond -f -l 8
