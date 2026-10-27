# BUG-073 — El chat de comunidad no tiene tope de participantes y cada mensaje escala con las miembras

- **Estado:** abierto
- **Severidad:** media
- **Confianza:** probable
- **Área:** backend/message
- **Tarea que lo resuelve:** T28 (carga) · ninguna (PR `perf/` propia)
- **Detectado:** 2026-10-27, revisión de seguridad de la PR de T20

## Dónde
`message/service/MessageService.java` (`incrementUnreadExceptSender`), `message/service/ChatMessageBroadcaster.java` (`onMessageSent`), `message/service/CommunityChatService.java` (`requireCanWrite`).

## Qué pasa
El tope `max-group-size` solo rige en grupos manuales. En un chat de comunidad cada mensaje hace un `UPDATE` sobre todas las filas de participantes y, tras el commit, un `convertAndSendToUser` por miembra en el hilo de entrada de WS. El límite por usuaria (20/min, ventana fija en memoria) no frena a varias usuarias a la vez; dos mensajes concurrentes pueden bloquearse entre sí sobre las mismas filas (sin orden determinista en el `UPDATE`).

## Cómo reproducir
Comunidad con miles de miembras y varias usuarias escribiendo a la vez (prueba de carga, T28).

## Arreglo propuesto
Tope de mensajes por minuto por chat, `UPDATE` con orden determinista o `SKIP LOCKED`/contador de no leídos por lotes, y fan-out asíncrono o por lotes.
