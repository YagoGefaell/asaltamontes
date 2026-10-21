# BUG-014 — `isOwner` sobre una comunidad inexistente provoca 500

- **Estado:** resuelto
- **Severidad:** baja
- **Confianza:** probable (verificar con test)
- **Área:** backend/community
- **Tarea que lo resuelve:** T14
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/community/repository/CommunityRepository.java` → `isOwner()`

## Qué pasa
La consulta no devuelve filas si la comunidad no existe, y el retorno primitivo `boolean` no admite `null` → `AopInvocationException` (500) al salir o borrar una comunidad inexistente.

## Cómo reproducir
`DELETE /api/communities/99999`.

## Arreglo propuesto
`existsByIdAndCreatedById` o `Optional<Boolean>`; 404 si no existe (T14).
