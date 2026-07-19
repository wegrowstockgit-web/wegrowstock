FROM postgres:16-alpine

RUN apk add --no-cache aws-cli gzip curl \
    && mkdir -p /opt/db-backup /var/log

COPY backup.sh /opt/db-backup/backup.sh
COPY entrypoint.sh /opt/db-backup/entrypoint.sh
RUN chmod +x /opt/db-backup/backup.sh /opt/db-backup/entrypoint.sh

ENTRYPOINT ["/opt/db-backup/entrypoint.sh"]
