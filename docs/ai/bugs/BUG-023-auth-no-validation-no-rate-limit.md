# BUG-023 — Login y registro sin validación de entrada ni límite de intentos

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** confirmado (lectura de código)
- **Área:** backend/auth
- **Tarea que lo resuelve:** T06
- **Detectado:** 2026-10-21, análisis inicial del código por Claude

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/auth/controller/AuthController.java` (`@RequestBody` sin `@Valid`), `backend/src/main/java/io/github/yagogefaell/asaltamontes/auth/dto/LoginRequest.java`

## Qué pasa
No hay límite de intentos: se puede probar contraseñas por fuerza bruta sin restricción. Los DTOs no tienen restricciones, así que un `null` acaba en NPE o en 500 en algunos caminos.

## Cómo reproducir
Bucle de 1000 logins fallidos → todos responden 401 sin bloqueo.

## Arreglo propuesto
Bean Validation + rate limiting por IP y por usuaria (T06b).
