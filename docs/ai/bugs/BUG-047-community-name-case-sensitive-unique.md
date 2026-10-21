# BUG-047 — Nombres de comunidad únicos solo de forma exacta: "Trail Vigo" y "trail vigo " conviven

- **Estado:** resuelto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código y del esquema)
- **Área:** backend/community
- **Tarea que lo resuelve:** T14
- **Detectado:** 2026-10-21, bug hunt de comunidades (Claude)

## Dónde
- `community/service/CommunityService.java#create` → `existsByName(request.name())` (comparación exacta, sin normalizar).
- `db/migration/V1__baseline.sql` → `CONSTRAINT uk_communities_name UNIQUE (name)` (sensible a mayúsculas y espacios).

## Qué pasa
El backend no recorta ni normaliza el nombre y la unicidad distingue mayúsculas y espacios. El frontend hace `trim()`, pero la API acepta cualquier variante: se pueden crear "Asaltamontes Female", "asaltamontes female" y "Asaltamontes  Female" (doble espacio). Además de confundir en búsquedas y listados, permite **suplantar** una comunidad oficial (importante cuando haya comunidades de marcas). Con el slug de T14, todas acabarían como `asaltamontes-female`, `asaltamontes-female-2`…, lo que no lo resuelve.

Es el mismo problema que T05 resolvió para los usernames.

## Cómo reproducir
`POST /api/communities {"name":"Trail Vigo"}` y luego `{"name":"trail vigo "}` → ambos 201.

## Arreglo propuesto
En T14: normalizar el nombre en el backend (`strip()` y colapsar espacios internos) y hacer la unicidad insensible a mayúsculas con un índice único sobre `lower(name)` en la migración del slug (comprobar antes que no haya duplicados en los datos; D-014 permite resolverlos a mano). `existsByNameIgnoreCase` para devolver el 409 con mensaje claro antes de llegar a la constraint.
