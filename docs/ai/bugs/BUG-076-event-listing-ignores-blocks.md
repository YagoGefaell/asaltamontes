# BUG-076 — El listado general de quedadas no oculta a las usuarias bloqueadas

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado
- **Área:** backend/events
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-28, revisión de seguridad de la PR #111 (T31b)

## Dónde
`event/repository/EventSearchRepositoryImpl.java`, `predicates()`.

## Qué pasa
`GET /api/events` (sin `followedBy`) y `GET /api/communities/{slug}/events` devuelven las quedadas públicas de usuarias con las que hay un bloqueo en cualquier dirección. No filtra datos nuevos (son públicas), pero es incoherente con D-022 y con `followedBy`, que sí las excluye.

## Arreglo propuesto
Mover el predicado `NOT EXISTS (UserBlock ...)` de `followedBy` a la parte común de `predicates()`, con test.
