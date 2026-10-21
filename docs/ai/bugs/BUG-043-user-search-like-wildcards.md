# BUG-043 — La búsqueda de usuarias trata `_` y `%` como comodines

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/user
- **Tarea que lo resuelve:** ninguna (PR `fix/` propia, o con T23 si se rehace la búsqueda)
- **Detectado:** 2026-10-21, bug hunting del área user

## Dónde
`user/repository/UserAccountRepository.searchUsersByUsername`: `LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))`.

## Qué pasa
El texto buscado se mete tal cual en el patrón `LIKE`. `_` está permitido en los usernames, así que buscar `ana_t` también encuentra `anaxt`, `ana7t`… y `%` devuelve a todo el mundo. No es un problema de seguridad (el parámetro va ligado), pero los resultados son incorrectos y una búsqueda de `%` es un listado completo.

## Cómo reproducir
`GET /api/users/search?query=_` → todas las usuarias.

## Arreglo propuesto
Escapar `\`, `%` y `_` en el servicio (`query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")`) y usar `LIKE … ESCAPE '\'` en la consulta. Sigue usando el índice trigram de T05. Test con `_` y `%`.
