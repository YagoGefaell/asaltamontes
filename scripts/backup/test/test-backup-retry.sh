#!/usr/bin/env bash
# BUG-080: the backup loop must wait for the DB and retry after a failed dump.
# Uses fake pg_isready/pg_dump (no Docker needed). Run: bash scripts/backup/test/test-backup-retry.sh
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/bin" "$TMP/backups"

# DB "down" for the first 3 pg_isready calls; the first pg_dump after that fails, then they succeed
cat > "$TMP/bin/pg_isready" <<EOS
#!/usr/bin/env bash
n=\$(cat "$TMP/isready.n" 2>/dev/null || echo 0); echo \$((n+1)) > "$TMP/isready.n"
[ "\$n" -ge 3 ]
EOS
cat > "$TMP/bin/pg_dump" <<EOS
#!/usr/bin/env bash
n=\$(cat "$TMP/dump.n" 2>/dev/null || echo 0); echo \$((n+1)) > "$TMP/dump.n"
[ "\$n" -ge 1 ] || { echo "pg_dump: connection refused" >&2; exit 1; }
for a in "\$@"; do case \$a in --file=*) echo data > "\${a#--file=}";; esac; done
EOS
# fast-forward short waits; the long wait for the next day ends the test
cat > "$TMP/bin/sleep" <<'EOS'
#!/usr/bin/env bash
[ "${1:-0}" -gt 100 ] && exit 99
exit 0
EOS
printf '#!/usr/bin/env bash\n' > "$TMP/media.sh"
chmod +x "$TMP/bin/"* "$TMP/media.sh"
sed "s#/scripts/backup-media.sh#$TMP/media.sh#" "$HERE/../backup-loop.sh" > "$TMP/loop.sh"

set +e
PATH="$TMP/bin:$PATH" PGDATABASE=x BACKUP_DIR="$TMP/backups" BACKUP_RETRY_DELAYS="1 2" BACKUP_DB_WAIT_SECONDS=20 \
  timeout 60 bash "$TMP/loop.sh" > "$TMP/out.log" 2>&1
set -e
cat "$TMP/out.log"
ls "$TMP/backups/daily/"*.dump > /dev/null || { echo "FAIL: no dump produced"; exit 1; }
grep -q "FAILED" "$TMP/out.log" && grep -q "retrying in 1s" "$TMP/out.log" && grep -q "done (" "$TMP/out.log" \
  || { echo "FAIL: expected FAILED, retry and done in the log"; exit 1; }
echo "OK"
