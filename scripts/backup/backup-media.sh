#!/usr/bin/env bash
# Backup of the uploaded images (the media_data volume) as one tar.gz (T17c). Called by backup-loop.sh after the
# daily database dump; it can also be run by hand.
#
# - Keeps ONLY the newest BACKUP_MEDIA_KEEP copies (default 3) in $BACKUP_DIR/media/.
# - Before copying it checks that the disk has room for the whole media folder plus BACKUP_MIN_FREE_MB of margin.
#   If not, it copies nothing, keeps the old copies and says so in the log and in $BACKUP_DIR/media/status,
#   which is not part of the container healthcheck on purpose (see the README to watch it).
# - The volume is mounted read-only in the backup service: this script can never modify the images.
# Restore: see the README, "Backups".
set -uo pipefail

MEDIA_DIR="${MEDIA_DIR:-/media}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
KEEP="${BACKUP_MEDIA_KEEP:-3}"
MIN_FREE_MB="${BACKUP_MIN_FREE_MB:-1024}"
OUT="$BACKUP_DIR/media"
STATUS="$OUT/status"

log() { echo "$(date '+%F %T') backup-media: $*"; }

# First word: ok | skipped | failed (the healthcheck only accepts "ok"); the file's mtime says when it was written
set_status() {
  echo "$1 $(date '+%F %T') ${2:-}" > "$STATUS"
}

mkdir -p "$OUT"

case "$KEEP" in
  ''|*[!0-9]*|0) log "BACKUP_MEDIA_KEEP must be a number >= 1 (got '$KEEP')"; set_status failed "invalid BACKUP_MEDIA_KEEP"; exit 1 ;;
esac

if [ ! -d "$MEDIA_DIR" ]; then
  log "FAILED: media folder $MEDIA_DIR not found (is the media_data volume mounted?)"
  set_status failed "media folder not found"
  exit 1
fi

need_bytes=$(du -sb "$MEDIA_DIR" | cut -f1)
avail_bytes=$(( $(df -Pk "$OUT" | awk 'NR==2 {print $4}') * 1024 ))
margin_bytes=$(( MIN_FREE_MB * 1024 * 1024 ))
# JPEGs hardly compress, so the archive is assumed to be as big as the folder
if [ $(( avail_bytes - margin_bytes )) -lt "$need_bytes" ]; then
  log "SKIPPED: not enough free disk space (need $((need_bytes / 1048576)) MB + ${MIN_FREE_MB} MB margin, free $((avail_bytes / 1048576)) MB). No copy made; the existing ones are kept"
  set_status skipped "no free space"
  exit 1
fi

file="$OUT/media-$(date +%Y%m%d-%H%M%S).tar.gz"
log "archiving $MEDIA_DIR to $file ($((need_bytes / 1048576)) MB)"
# Exit code 1 only means that a file changed while it was read (an upload in progress): the archive is still valid
tar -czf "$file.partial" --exclude='.tmp-*' -C "$MEDIA_DIR" . 2>/dev/null
code=$?
if [ "$code" -le 1 ] && tar -tzf "$file.partial" > /dev/null 2>&1; then
  mv "$file.partial" "$file"
  ls -1t "$OUT"/media-*.tar.gz 2>/dev/null | tail -n +"$((KEEP + 1))" | xargs -r rm -f --
  log "done ($(du -h "$file" | cut -f1)); keeping the newest $KEEP"
  set_status ok "$(basename "$file")"
else
  rm -f "$file.partial"
  log "FAILED (tar exit code $code)"
  set_status failed "tar exit code $code"
  exit 1
fi
