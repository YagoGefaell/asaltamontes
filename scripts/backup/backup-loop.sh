#!/usr/bin/env bash
# Daily logical backup of the database (T07b). Runs inside the `backup` service of docker-compose.yml.
#
# - One pg_dump (custom format, compressed) per day at BACKUP_TIME (server local time, TZ), plus one
#   right away on start if the newest backup is older than a day (e.g. after a long outage).
# - Keeps the newest BACKUP_KEEP_DAILY daily dumps; the Sunday dump is also copied to weekly/,
#   which keeps the newest BACKUP_KEEP_WEEKLY.
# - After each dump, backup-media.sh archives the uploaded images (media_data volume, mounted read-only) and
#   keeps the newest 3 copies, but only if the disk has room (T17c).
# Restore: scripts/backup/restore.sh and the README, "Backups".
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/backups}"
# Seconds to wait for the database before each dump, and pauses between retries of a failed dump (BUG-080)
DB_WAIT_SECONDS="${BACKUP_DB_WAIT_SECONDS:-300}"
RETRY_DELAYS="${BACKUP_RETRY_DELAYS:-60 300 900 3600}"
BACKUP_TIME="${BACKUP_TIME:-03:30}"
KEEP_DAILY="${BACKUP_KEEP_DAILY:-7}"
KEEP_WEEKLY="${BACKUP_KEEP_WEEKLY:-4}"

mkdir -p "$BACKUP_DIR/daily" "$BACKUP_DIR/weekly"

log() { echo "$(date '+%F %T') backup: $*"; }

prune() {
  local dir=$1 keep=$2
  ls -1t "$dir"/*.dump 2>/dev/null | tail -n +"$((keep + 1))" | xargs -r rm -f --
  return 0
}

# `depends_on: service_healthy` is ignored when Docker restarts the container, so wait for the DB ourselves
wait_for_db() {
  local waited=0
  until pg_isready -q; do
    if [ "$waited" -ge "$DB_WAIT_SECONDS" ]; then
      log "database not ready after ${waited}s"
      return 1
    fi
    sleep 2
    waited=$((waited + 2))
  done
}

backup() {
  local file="$BACKUP_DIR/daily/asaltamontes-$(date +%Y%m%d-%H%M).dump"
  if ! wait_for_db; then
    log "FAILED"
    return 1
  fi
  log "dumping to $file"
  if pg_dump --format=custom --compress=9 --file="$file.partial" "$PGDATABASE"; then
    mv "$file.partial" "$file"
    log "done ($(du -h "$file" | cut -f1))"
    if [ "$(date +%u)" = "7" ]; then
      cp "$file" "$BACKUP_DIR/weekly/"
    fi
    prune "$BACKUP_DIR/daily" "$KEEP_DAILY"
    prune "$BACKUP_DIR/weekly" "$KEEP_WEEKLY"
  else
    rm -f "$file.partial"
    log "FAILED"
    return 1
  fi
}

# A failed dump is retried with growing pauses before giving up until the next scheduled time (BUG-080)
backup_with_retry() {
  local delay
  backup && return 0
  for delay in $RETRY_DELAYS; do
    log "retrying in ${delay}s"
    sleep "$delay"
    backup && return 0
  done
  log "giving up until the next scheduled backup"
  return 1
}

# A failure of the media backup is logged and reflected in its status file (healthcheck); it never stops the loop
backup_media() {
  bash /scripts/backup-media.sh || log "media backup did not complete (see above)"
}

seconds_until() {
  local now target
  now=$(date +%s)
  target=$(date -d "today $1" +%s)
  if [ "$target" -le "$now" ]; then
    target=$(date -d "tomorrow $1" +%s)
  fi
  echo $((target - now))
}

if ! find "$BACKUP_DIR/daily" -name '*.dump' -mmin -1440 | grep -q .; then
  backup_with_retry || true
fi
if ! find "$BACKUP_DIR/media" -name 'media-*.tar.gz' -mmin -1440 2>/dev/null | grep -q .; then
  backup_media
fi

while true; do
  wait_seconds=$(seconds_until "$BACKUP_TIME")
  log "next backup in ${wait_seconds}s (at $BACKUP_TIME)"
  sleep "$wait_seconds"
  backup_with_retry || true
  backup_media
done
