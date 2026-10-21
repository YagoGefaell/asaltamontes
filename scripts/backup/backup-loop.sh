#!/usr/bin/env bash
# Daily logical backup of the database (T07b). Runs inside the `backup` service of docker-compose.yml.
#
# - One pg_dump (custom format, compressed) per day at BACKUP_TIME (server local time, TZ), plus one
#   right away on start if the newest backup is older than a day (e.g. after a long outage).
# - Keeps the newest BACKUP_KEEP_DAILY daily dumps; the Sunday dump is also copied to weekly/,
#   which keeps the newest BACKUP_KEEP_WEEKLY.
# Restore: scripts/backup/restore.sh (see the README, "Backups").
set -euo pipefail

BACKUP_DIR=/backups
BACKUP_TIME="${BACKUP_TIME:-03:30}"
KEEP_DAILY="${BACKUP_KEEP_DAILY:-7}"
KEEP_WEEKLY="${BACKUP_KEEP_WEEKLY:-4}"

mkdir -p "$BACKUP_DIR/daily" "$BACKUP_DIR/weekly"

log() { echo "$(date '+%F %T') backup: $*"; }

prune() {
  local dir=$1 keep=$2
  ls -1t "$dir"/*.dump 2>/dev/null | tail -n +"$((keep + 1))" | xargs -r rm -f --
}

backup() {
  local file="$BACKUP_DIR/daily/asaltamontes-$(date +%Y%m%d-%H%M).dump"
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
  fi
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
  backup
fi

while true; do
  wait_seconds=$(seconds_until "$BACKUP_TIME")
  log "next backup in ${wait_seconds}s (at $BACKUP_TIME)"
  sleep "$wait_seconds"
  backup
done
