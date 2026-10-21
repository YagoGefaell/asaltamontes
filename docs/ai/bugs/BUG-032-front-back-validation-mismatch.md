# BUG-032 — Las reglas de validación del frontend y del backend no coinciden

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** confirmado (lectura de código)
- **Área:** frontend/auth + backend/user
- **Tarea que lo resuelve:** T06
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`frontend/src/config/constants.js` (`VALIDATION`) vs `backend/src/main/java/io/github/yagogefaell/asaltamontes/user/service/UserValidator.java`

## Qué pasa
Frontend: username de 3 a 20 caracteres sin `.`, contraseña ≥ 8. Backend: username de 3 a 50 con `.`, contraseña ≥ 6. Hay usernames válidos en el backend (`ana.trail`) que el formulario rechaza, y mensajes de error incoherentes.

## Cómo reproducir
—

## Arreglo propuesto
Una única fuente de reglas (T06b).
