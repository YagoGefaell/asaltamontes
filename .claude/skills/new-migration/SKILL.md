---
name: new-migration
description: Crea una migración Flyway nueva en Asaltamontes (número V siguiente, objetos previos localizados por definición, prueba con datos y simulando los nombres de prod). Úsalo siempre que haya un cambio de esquema; nunca se edita una migración ya mergeada.
disable-model-invocation: true
---

# Nueva migración Flyway

Argumento: descripción corta (p. ej. `event_search_indexes`).

1. **Número**: `ls backend/src/main/resources/db/migration/` en `origin/main` **y** en las PRs abiertas (`gh pr list` + `gh pr diff <N> --name-only | grep migration`). Usa el siguiente libre; si hay dos PRs con migración, la de número mayor va en borrador hasta que entre la otra.
2. **Nombre**: `V<n>__<descripcion_en_ingles>.sql`. Nunca edites una `V*` que esté en `origin/main` (el hook `scripts/hooks/protect-merged-migrations.sh` lo bloquea).
3. **Objetos previos**: si la migración toca constraints o índices que ya existían antes de Flyway, localízalos **por definición** (`pg_constraint`, `pg_index` por tabla y columna), no por nombre: la BD de prod la creó Hibernate y sus nombres no son los de `V1__baseline.sql` (BUG-049).
4. **Seguridad con datos**: pensar en filas existentes (nulos, duplicados, mayúsculas). Operaciones largas o bloqueantes sobre tablas grandes → `CREATE INDEX CONCURRENTLY` no funciona dentro de la transacción de Flyway: si hace falta, documentarlo en la PR.
5. **Prueba**: en un PG 17 desechable (nunca en prod ni en el compose de prod): aplicar V1..V<n-1>, insertar datos de ejemplo, **renombrar** las constraints/índices tocados a nombres aleatorios (simula prod) y aplicar la nueva. Para volumen usa el seed (`seed`) o `generate_series`.
6. **Hibernate**: `ddl-auto=validate` debe seguir pasando (`./mvnw -B verify`, los IT parten de V1).
7. **PR**: marca la casilla de migración del DoD (WORKFLOW §6), describe el efecto sobre datos existentes y, si hay índices, adjunta `EXPLAIN`.
