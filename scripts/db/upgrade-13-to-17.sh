#!/usr/bin/env bash
# PostgreSQL 13 -> 17 upgrade (T08). Manual, guided procedure: it is NOT run by the deploy.
#
# Copies the live database (postgres:13 container) into a NEW volume for postgres:17 and checks that
# every table has the same number of rows. The old volume is left untouched (rollback = point back to it).
#
# Usage, from the repository root on the server, BEFORE merging the T08 pull request:
#   ENV_FILE=/etc/github-runner.env scripts/db/upgrade-13-to-17.sh
# Test stack:  PROJECT=asaltamontes-dev DB_CONTAINER=asaltamontes-dev-db ENV_FILE=<dev env> scripts/db/upgrade-13-to-17.sh
#
# Then merge: the deploy starts postgres:17 on the new volume, which already holds the data.
# Re-running is safe: it refuses to overwrite a non-empty new volume unless FORCE=1.
set -euo pipefail

PROJECT="${PROJECT:-asaltamontes}"
DB_CONTAINER="${DB_CONTAINER:-postgres_db}"
ENV_FILE="${ENV_FILE:?ENV_FILE is required (e.g. /etc/github-runner.env)}"
DB_NAME="asaltamontes"
NEW_VOLUME="${PROJECT}_postgres17_data"
TEMP_CONTAINER="${PROJECT}-pg17-upgrade"
DUMP_FILE="$(mktemp -t asaltamontes-pg13-XXXX.dump)"

# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
: "${DATABASE_USERNAME:?missing in $ENV_FILE}" "${DATABASE_PASSWORD:?missing in $ENV_FILE}"

log() { echo "==> $*"; }
cleanup() {
  docker rm -f "$TEMP_CONTAINER" >/dev/null 2>&1 || true
  rm -f "$DUMP_FILE"
}
trap cleanup EXIT

count_rows() {  # $1 = container: prints "table rows" for every table, sorted
  docker exec "$1" psql -U "$DATABASE_USERNAME" -d "$DB_NAME" -At -F ' ' -c "
    SELECT format('%s %s', t.relname,
           (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', t.relname), false, true, '')))[1]::text)
    FROM pg_class t JOIN pg_namespace n ON n.oid = t.relnamespace
    WHERE n.nspname = 'public' AND t.relkind = 'r'
    ORDER BY 1"
}

log "1/5 Checking the source ($DB_CONTAINER)"
docker exec "$DB_CONTAINER" psql -U "$DATABASE_USERNAME" -d "$DB_NAME" -Atc "SHOW server_version"

if docker volume inspect "$NEW_VOLUME" >/dev/null 2>&1; then
  if [ "${FORCE:-0}" != "1" ]; then
    echo "Volume $NEW_VOLUME already exists. Remove it first or run with FORCE=1 to recreate it." >&2
    exit 1
  fi
  log "FORCE=1: recreating $NEW_VOLUME"
  docker volume rm "$NEW_VOLUME" >/dev/null
fi

log "2/5 Dumping PostgreSQL 13"
docker exec "$DB_CONTAINER" pg_dump -U "$DATABASE_USERNAME" --format=custom "$DB_NAME" > "$DUMP_FILE"
ls -lh "$DUMP_FILE"

log "3/5 Starting PostgreSQL 17 on the new volume $NEW_VOLUME"
# Labels so that docker compose adopts the volume as its own "postgres17_data"
docker volume create \
  --label com.docker.compose.project="$PROJECT" \
  --label com.docker.compose.volume=postgres17_data \
  "$NEW_VOLUME" >/dev/null
docker run -d --name "$TEMP_CONTAINER" \
  -e POSTGRES_DB="$DB_NAME" -e POSTGRES_USER="$DATABASE_USERNAME" -e POSTGRES_PASSWORD="$DATABASE_PASSWORD" \
  -v "$NEW_VOLUME":/var/lib/postgresql/data \
  postgres:17 >/dev/null
for _ in $(seq 1 60); do
  # The entrypoint restarts the server once after initdb: wait until it accepts queries for real
  if docker exec "$TEMP_CONTAINER" psql -U "$DATABASE_USERNAME" -d "$DB_NAME" -Atc "SELECT 1" >/dev/null 2>&1 \
     && docker logs "$TEMP_CONTAINER" 2>&1 | grep -q "PostgreSQL init process complete"; then
    break
  fi
  sleep 2
done

log "4/5 Restoring into PostgreSQL 17"
docker cp "$DUMP_FILE" "$TEMP_CONTAINER":/tmp/upgrade.dump
docker exec "$TEMP_CONTAINER" pg_restore -U "$DATABASE_USERNAME" -d "$DB_NAME" --no-owner --exit-on-error /tmp/upgrade.dump
docker exec "$TEMP_CONTAINER" psql -U "$DATABASE_USERNAME" -d "$DB_NAME" -qc "ANALYZE"

log "5/5 Comparing row counts (13 vs 17)"
before=$(count_rows "$DB_CONTAINER")
after=$(count_rows "$TEMP_CONTAINER")
paste <(echo "$before") <(echo "$after" | awk '{print $2}') | awk 'BEGIN {printf "%-30s %10s %10s\n", "table", "pg13", "pg17"} {printf "%-30s %10s %10s\n", $1, $2, $3}'
if [ "$before" != "$after" ]; then
  echo "Row counts differ: the new volume is NOT valid. Nothing has changed in production." >&2
  exit 1
fi

docker stop "$TEMP_CONTAINER" >/dev/null
log "OK: $NEW_VOLUME holds a verified copy on PostgreSQL 17. Now merge the T08 pull request."
log "The old volume (${PROJECT}_postgres_data) is kept for rollback."
