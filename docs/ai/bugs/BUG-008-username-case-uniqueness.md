# BUG-008 — Usernames duplicados que solo difieren en mayúsculas rompen el login

- **Estado:** resuelto
- **Severidad:** alta
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/user
- **Tarea que lo resuelve:** T05
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/user/service/UserValidator.java` (`existsByUsername`) vs `UserService.loadUserByUsername` (`findByUsernameIgnoreCase`)

## Qué pasa
El registro comprueba la unicidad distinguiendo mayúsculas, pero todas las búsquedas ignoran mayúsculas. Se pueden registrar `Ana` y `ana`, y a partir de ahí `findByUsernameIgnoreCase` devuelve dos filas → `IncorrectResultSizeDataAccessException` (500) en login, perfil, follows y chat de ambas cuentas.

## Cómo reproducir
Registrar `Ana` y después `ana`; intentar iniciar sesión con cualquiera → error.

## Arreglo propuesto
Índice único sobre `lower(username)` + validación insensible a mayúsculas + migración que resuelva los duplicados existentes (T05).
