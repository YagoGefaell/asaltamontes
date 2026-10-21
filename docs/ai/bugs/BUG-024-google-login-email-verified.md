# BUG-024 — El login con Google no comprueba `email_verified` y genera usernames inválidos

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/auth
- **Tarea que lo resuelve:** T06
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/infrastructure/google/GoogleTokenVerifier.java`, `backend/src/main/java/io/github/yagogefaell/asaltamontes/user/service/UserService.java` → `findOrCreateGoogleUser()`

## Qué pasa
(1) Se vincula la cuenta existente que tenga ese email sin comprobar que Google lo ha verificado. (2) El username generado `givenName + "_" + millis` puede contener espacios, acentos o `null` (`null_1729…`), no cumple las reglas de username y no se puede volver a guardar el perfil sin cambiarlo.

## Cómo reproducir
Iniciar sesión con una cuenta de Google cuyo nombre sea "María José" → username `María José_1729…`.

## Arreglo propuesto
Exigir `email_verified` y normalizar el username (T06b).
