# BUG-033 — El nombre completo rechaza guiones y apóstrofos

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/user
- **Tarea que lo resuelve:** T06
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/user/service/UserValidator.java` → `validateFullName` (`^[a-zA-ZÀ-ÿ\\s]+$`)

## Qué pasa
"María-José", "O'Donnell" o nombres con caracteres fuera de `À-ÿ` (p. ej. `ł`, `ș`) no se pueden registrar.

## Cómo reproducir
Registrar el nombre "María-José Pérez" → error.

## Arreglo propuesto
Regex Unicode `\p{L}` con espacios, guiones y apóstrofos (T06b).
