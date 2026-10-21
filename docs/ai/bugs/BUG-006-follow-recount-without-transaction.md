# BUG-006 — El recálculo nocturno de contadores de follows se ejecuta sin transacción

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** probable (verificar con test)
- **Área:** backend/user
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia)
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/user/maintenance/FollowCounterMaintenanceService.java` → `recountBatch()`

## Qué pasa
`recountBatch` es `protected @Transactional`, pero se invoca desde el propio bean (`this.recountBatch`), así que el proxy de Spring no interviene y no hay transacción. Las consultas `@Modifying` del repositorio requieren una transacción → previsiblemente `TransactionRequiredException` cada noche a las 04:00, y los contadores nunca se corrigen (agrava BUG-007).

## Cómo reproducir
Test IT que invoque `recountCountersInBatches()` con contadores descuadrados y compruebe que se corrigen. Revisar también los logs del servidor a las 04:00.

## Arreglo propuesto
Anotar `recountCountersInBatches` (público) con `@Transactional` por lote mediante `TransactionTemplate`, o mover `recountBatch` a otro bean. Compararlo con `CommunityMemberCounterMaintenanceService`, que sí es transaccional.
