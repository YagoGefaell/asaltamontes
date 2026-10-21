# BUG-040 — El interceptor del WebSocket usa el JWT como mensaje de la excepción

- **Estado:** abierto
- **Severidad:** baja
- **Confianza:** probable (lectura de código)
- **Área:** backend/auth
- **Tarea que lo resuelve:** T06
- **Detectado:** 2026-10-21, Claude durante T04

## Dónde
`backend/src/main/java/io/github/yagogefaell/asaltamontes/infrastructure/security/WebSocketJwtInterceptor.java` → `throw new InvalidTokenException(jwt)`

## Qué pasa
Si validar el token del `CONNECT` de STOMP lanza una excepción, se crea `InvalidTokenException` con el **token completo** como mensaje. Ese mensaje puede acabar en los logs (nivel DEBUG/ERROR de Spring Messaging) y en la cabecera `message` del frame `ERROR` que recibe la cliente. Un token en los logs es una credencial reutilizable mientras no caduque.

(T04 ya quitó el mismo patrón de `JwtAuthFilter`.)

## Cómo reproducir
Test: `CONNECT` con un `Authorization: Bearer <token>` que haga fallar `loadUserById` (p. ej. usuaria borrada) y comprobar el mensaje del frame `ERROR` / el log.

## Arreglo propuesto
Mensaje fijo (`"Token inválido"`), como en `AuthService`. Cambio mínimo dentro de la clase (D-012), junto al resto del endurecimiento de T06.
