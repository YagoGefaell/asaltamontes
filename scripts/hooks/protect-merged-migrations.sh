#!/usr/bin/env bash
# PreToolUse hook (Edit|Write|MultiEdit): bloquea editar una migración Flyway que ya está en origin/main.
# Una migración mergeada se aplica en la BD de prod y su checksum queda registrado: se corrige con una V nueva.
# Las migraciones nuevas (aún no están en origin/main) se pueden editar libremente.
set -u

file=$(jq -r '.tool_input.file_path // empty' 2>/dev/null)
case "$file" in
  */db/migration/V*.sql) ;;
  *) exit 0 ;;
esac

dir=$(dirname "$file")
top=$(git -C "$dir" rev-parse --show-toplevel 2>/dev/null) || exit 0
rel=${file#"$top"/}

if git -C "$top" cat-file -e "origin/main:$rel" 2>/dev/null; then
  echo "Bloqueado: $rel ya está en origin/main (aplicada en prod, checksum de Flyway). Crea una migración nueva con la skill /new-migration." >&2
  exit 2
fi
exit 0
