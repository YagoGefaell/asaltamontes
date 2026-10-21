#!/usr/bin/env bash
# Restores a dump made by backup-loop.sh into a database (T07b). Runs inside the `backup` service:
#
#   docker compose exec backup /scripts/restore.sh /backups/daily/<file>.dump <target_database>
#
# The target database is created empty if it does not exist. Restoring over the live database
# ("asaltamontes") requires stopping the backend first and passing --force: it drops and recreates it.
set -euo pipefail

dump=${1:?usage: restore.sh <dump file> <target database> [--force]}
target=${2:?usage: restore.sh <dump file> <target database> [--force]}
force=${3:-}

if [ "$target" = "$PGDATABASE" ] && [ "$force" != "--force" ]; then
  echo "Refusing to restore over the live database '$target' without --force (stop the backend first)." >&2
  exit 1
fi

if [ "$force" = "--force" ]; then
  dropdb --if-exists "$target"
fi
psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$target'" | grep -q 1 || createdb "$target"

pg_restore --no-owner --exit-on-error --dbname="$target" "$dump"
echo "Restored $dump into '$target'."
