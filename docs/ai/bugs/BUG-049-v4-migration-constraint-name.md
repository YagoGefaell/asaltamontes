# BUG-049 — La migración V4 falla en producción: la constraint única de `communities.name` tiene otro nombre

- **Estado:** resuelto
- **Severidad:** crítica
- **Confianza:** confirmado (log del deploy)
- **Área:** infra / backend/community
- **Tarea que lo resuelve:** ninguna (PR `hotfix/` propia)
- **Detectado:** 2026-10-22, Claude al revisar el deploy fallido de #43 (2026-10-21 20:34Z)

## Dónde
`backend/src/main/resources/db/migration/V4__community_slug_and_unique_name.sql` → `ALTER TABLE communities DROP CONSTRAINT uk_communities_name`

## Qué pasa
V4 (T14a, #43) asume el nombre de la constraint que crea `V1__baseline.sql`. Pero la BD de producción no se creó con V1: la creó Hibernate (`ddl-auto`) antes de Flyway, y T02a la marcó como *baseline*. Ahí la constraint única de `name` tiene un nombre generado por Hibernate (`uk…`). En el deploy, Flyway falla con `constraint "uk_communities_name" of relation "communities" does not exist`, PostgreSQL deshace la migración entera (no queda nada a medias) y el backend no arranca: **la app está caída** desde el deploy de #43.

Los tests no lo detectaron porque Testcontainers crea la BD desde V1, con los nombres de V1.

## Cómo reproducir
BD con V1-V3 en la que la constraint de `communities.name` se renombra (`ALTER TABLE communities RENAME CONSTRAINT uk_communities_name TO ukabc123`) → aplicar V4 → falla.

## Arreglo propuesto
Borrar la constraint única de la columna `name` sea cual sea su nombre (bloque `DO` sobre `pg_constraint`). V4 se edita en lugar de añadir una migración nueva porque **no se ha aplicado en ninguna BD persistente**: en prod falló y se deshizo, y Flyway en PostgreSQL no registra las migraciones fallidas.

**Lección:** cualquier migración que toque objetos creados antes de Flyway (constraints, índices) debe localizarlos por su definición, no por su nombre. Anotado en `WORKFLOW.md` (§6, migraciones).
